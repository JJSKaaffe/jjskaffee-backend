# -----------------------------------------------------------------------------
# WAF de JJSKaffee (ADR-0018): balanceador HTTPS externo global + Cloud Armor delante del API Gateway.
#
#   Navegador -> Balanceador + Cloud Armor -> API Gateway (serverless NEG) -> Cloud Run privado -> Cloud SQL
#
# Requisitos: el API Gateway ya existe (docs/despliegue/api-gateway.md) y gcloud tiene sesión iniciada.
# Uso (PowerShell, desde la raíz del repo):   .\infra\waf\crear-waf.ps1
# Guía completa: docs/despliegue/waf.md. Para borrarlo todo: .\infra\waf\eliminar-waf.ps1
#
# El valor secreto del encabezado X-JJSKaffee-Origen vive SOLO en Secret Manager (jjskaffee-waf-secreto):
# este script lo crea o lo lee, nunca lo imprime y nunca se escribe en el repo.
# -----------------------------------------------------------------------------

# ----- Variables (revísalas antes de correrlo; las mismas de docs/despliegue/gcp.md) -----
$PROJECT_ID      = "jjskafee"
$REGION          = "us-east1"            # la región del API Gateway (la misma de Cloud Run y Cloud SQL)
$GATEWAY_ID      = "jjskaffee-gateway"   # el mismo de docs/despliegue/api-gateway.md
$NOMBRE_POLITICA = "jjskaffee-waf"

# Nombres de los demás recursos (eliminar-waf.ps1 usa los mismos)
$NOMBRE_IP       = "jjskaffee-ip"
$NOMBRE_NEG      = "jjskaffee-gateway-neg"
$NOMBRE_BACKEND  = "jjskaffee-backend-lb"
$NOMBRE_URLMAP   = "jjskaffee-urlmap"
$NOMBRE_CERT     = "jjskaffee-cert"
$NOMBRE_PROXY    = "jjskaffee-https-proxy"
$NOMBRE_REGLA    = "jjskaffee-https-regla"
$NOMBRE_SECRETO  = "jjskaffee-waf-secreto"
$ENCABEZADO      = "X-JJSKaffee-Origen"

# Prioridades de las reglas: primero las OWASP y después el límite por IP. El throttle termina la evaluación
# (su conform-action es allow), así que si fuera antes las reglas OWASP nunca se revisarían.
$REGLAS_OWASP = [ordered]@{
    1000 = "sqli-v33-stable"
    1001 = "xss-v33-stable"
    1002 = "lfi-v33-stable"
    1003 = "rce-v33-stable"
    1004 = "scannerdetection-v33-stable"
    1005 = "protocolattack-v33-stable"
}
$PRIORIDAD_LIMITE = 2000

# ----- Ayudas -----
function Comprobar([string]$paso) {
    if ($LASTEXITCODE -ne 0) { throw "Falló: $paso. Revisa el mensaje de gcloud de arriba." }
}

if ($PROJECT_ID -eq "PROJECT_ID" -or $REGION -eq "REGION") {
    throw "Primero cambia PROJECT_ID y REGION al inicio del script."
}

gcloud config set project $PROJECT_ID;                                         Comprobar "elegir el proyecto"
gcloud services enable compute.googleapis.com secretmanager.googleapis.com;    Comprobar "habilitar Compute y Secret Manager"

# ----- 1. IP estática global y dominio sslip.io -----
gcloud compute addresses create $NOMBRE_IP --global --ip-version=IPV4;         Comprobar "reservar la IP"
$IP = gcloud compute addresses describe $NOMBRE_IP --global --format="value(address)"
Comprobar "leer la IP"
$DOMINIO = "$($IP.Trim() -replace '\.', '-').sslip.io"    # 34.120.1.2 -> 34-120-1-2.sslip.io
Write-Host "IP: $IP  Dominio: $DOMINIO"

# ----- 2. Serverless NEG que apunta al API Gateway (en Preview: por eso "beta") -----
gcloud beta compute network-endpoint-groups create $NOMBRE_NEG `
    --region=$REGION `
    --network-endpoint-type=serverless `
    --serverless-deployment-platform=apigateway.googleapis.com `
    --serverless-deployment-resource=$GATEWAY_ID
Comprobar "crear la serverless NEG"

# ----- 3. Secreto del encabezado de origen y backend service -----
gcloud secrets describe $NOMBRE_SECRETO --format="value(name)" 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    # 48 bytes aleatorios en base64url (64 caracteres). Se escribe en un archivo temporal sin salto de línea
    # (PowerShell 5.1 agrega uno al pasarlo por tubería) y el archivo se borra enseguida.
    $bytes = New-Object byte[] 48
    $generador = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $generador.GetBytes($bytes)
    $generador.Dispose()
    $temporal = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText($temporal, [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_'))
        gcloud secrets create $NOMBRE_SECRETO --replication-policy=automatic --data-file=$temporal
        Comprobar "crear el secreto $NOMBRE_SECRETO"
    } finally {
        Remove-Item $temporal -Force
    }
    Write-Host "Secreto $NOMBRE_SECRETO creado en Secret Manager."
} else {
    Write-Host "El secreto $NOMBRE_SECRETO ya existe: se usa su última versión."
}
$VALOR = (gcloud secrets versions access latest --secret=$NOMBRE_SECRETO | Out-String).Trim()
Comprobar "leer el secreto"

# Con registro de cada petición (logging-sample-rate=1) para revisar en Cloud Logging qué bloquearía el WAF
gcloud compute backend-services create $NOMBRE_BACKEND `
    --global `
    --enable-logging --logging-sample-rate=1 `
    --custom-request-header="${ENCABEZADO}:${VALOR}"
Comprobar "crear el backend service"
Remove-Variable VALOR

gcloud compute backend-services add-backend $NOMBRE_BACKEND `
    --global `
    --network-endpoint-group=$NOMBRE_NEG `
    --network-endpoint-group-region=$REGION
Comprobar "agregar la NEG al backend service"

# ----- 4. Política de Cloud Armor (todas las reglas en --preview: solo registran, no bloquean) -----
gcloud compute security-policies create $NOMBRE_POLITICA --description="WAF de JJSKaffee (ADR-0018)"
Comprobar "crear la política"
# Registro detallado: muestra qué parte de la petición activó cada regla
gcloud compute security-policies update $NOMBRE_POLITICA --log-level=VERBOSE
Comprobar "activar el registro detallado"

# GetEnumerator: en un [ordered], $REGLAS_OWASP[1000] se leería como posición, no como clave
foreach ($entrada in $REGLAS_OWASP.GetEnumerator()) {
    $prioridad = $entrada.Key
    $regla = $entrada.Value
    gcloud compute security-policies rules create $prioridad `
        --security-policy=$NOMBRE_POLITICA `
        --expression="evaluatePreconfiguredWaf('$regla', {'sensitivity': 1})" `
        --action=deny-403 `
        --preview `
        --description="OWASP $regla, sensibilidad 1"
    Comprobar "crear la regla $regla"
}

# Límite por IP: 100 peticiones por minuto; el exceso recibe 429
gcloud compute security-policies rules create $PRIORIDAD_LIMITE `
    --security-policy=$NOMBRE_POLITICA `
    --src-ip-ranges="*" `
    --action=throttle `
    --rate-limit-threshold-count=100 `
    --rate-limit-threshold-interval-sec=60 `
    --conform-action=allow `
    --exceed-action=deny-429 `
    --enforce-on-key=IP `
    --preview `
    --description="Límite por IP: 100 peticiones cada 60 s"
Comprobar "crear el límite por IP"

# Regla por defecto (prioridad 2147483647): permitir. No lleva --preview porque permitir no bloquea nada.
gcloud compute security-policies rules update 2147483647 --security-policy=$NOMBRE_POLITICA --action=allow
Comprobar "dejar la regla por defecto en permitir"

# Cuando revises los registros (docs/despliegue/waf.md, "Revisar el modo preview"), quita el preview para
# que las reglas bloqueen de verdad. Descomenta y corre:
#
# foreach ($prioridad in @($REGLAS_OWASP.Keys) + $PRIORIDAD_LIMITE) {
#     gcloud compute security-policies rules update $prioridad --security-policy=$NOMBRE_POLITICA --no-preview
# }

# ----- 5. Política al backend service, URL map, certificado, proxy HTTPS y regla de reenvío -----
gcloud compute backend-services update $NOMBRE_BACKEND --global --security-policy=$NOMBRE_POLITICA
Comprobar "asociar la política al backend service"

gcloud compute url-maps create $NOMBRE_URLMAP --global --default-service=$NOMBRE_BACKEND
Comprobar "crear el URL map"

gcloud compute ssl-certificates create $NOMBRE_CERT --global --domains=$DOMINIO
Comprobar "crear el certificado administrado"

gcloud compute target-https-proxies create $NOMBRE_PROXY --global --url-map=$NOMBRE_URLMAP --ssl-certificates=$NOMBRE_CERT
Comprobar "crear el proxy HTTPS"

gcloud compute forwarding-rules create $NOMBRE_REGLA --global --address=$NOMBRE_IP --target-https-proxy=$NOMBRE_PROXY --ports=443
Comprobar "crear la regla de reenvío en el puerto 443"

# ----- 6. Resultado -----
Write-Host ""
Write-Host "Listo. URL de la API:  https://$DOMINIO/api"
Write-Host "El certificado puede tardar hasta 60 minutos en quedar ACTIVE. Revísalo con:"
Write-Host "  gcloud compute ssl-certificates describe $NOMBRE_CERT --global --format=`"value(managed.status)`""
Write-Host "Las reglas están en modo preview (solo registran). Siguiente paso: docs/despliegue/waf.md"
