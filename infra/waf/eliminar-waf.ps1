# -----------------------------------------------------------------------------
# Borra el WAF de JJSKaffee (ADR-0018) en orden inverso a crear-waf.ps1, para no seguir pagando
# después de la evaluación. No toca el API Gateway, Cloud Run ni Cloud SQL.
#
# Uso (PowerShell, desde la raíz del repo):   .\infra\waf\eliminar-waf.ps1
#
# ANTES de correrlo: si Cloud Run usa el secreto (JJSKAFFEE_WAF_SECRETO), deja la variable vacía para que el
# backend no exija el encabezado y no falle al arrancar sin el secreto (docs/despliegue/waf.md, "Quitar el WAF").
# Si algún recurso ya no existe, el script avisa y sigue con el siguiente.
# -----------------------------------------------------------------------------

# ----- Variables: las mismas de crear-waf.ps1 -----
$PROJECT_ID      = "jjskafee"
$REGION          = "us-east1"
$GATEWAY_ID      = "jjskaffee-gateway"   # no se borra; está aquí para que las variables sean las mismas
$NOMBRE_POLITICA = "jjskaffee-waf"

$NOMBRE_IP       = "jjskaffee-ip"
$NOMBRE_NEG      = "jjskaffee-gateway-neg"
$NOMBRE_BACKEND  = "jjskaffee-backend-lb"
$NOMBRE_URLMAP   = "jjskaffee-urlmap"
$NOMBRE_CERT     = "jjskaffee-cert"
$NOMBRE_PROXY    = "jjskaffee-https-proxy"
$NOMBRE_REGLA    = "jjskaffee-https-regla"
$NOMBRE_SECRETO  = "jjskaffee-waf-secreto"

$fallos = @()
function Borrar([string]$que, [scriptblock]$comando) {
    Write-Host "Borrando $que..."
    & $comando
    if ($LASTEXITCODE -ne 0) { $script:fallos += $que }
}

if ($PROJECT_ID -eq "PROJECT_ID" -or $REGION -eq "REGION") {
    throw "Primero cambia PROJECT_ID y REGION al inicio del script."
}
gcloud config set project $PROJECT_ID

# Orden inverso a crear-waf.ps1
Borrar "la regla de reenvío"     { gcloud compute forwarding-rules delete $NOMBRE_REGLA --global --quiet }
Borrar "el proxy HTTPS"          { gcloud compute target-https-proxies delete $NOMBRE_PROXY --global --quiet }
Borrar "el certificado"          { gcloud compute ssl-certificates delete $NOMBRE_CERT --global --quiet }
Borrar "el URL map"              { gcloud compute url-maps delete $NOMBRE_URLMAP --global --quiet }
# Al borrar el backend service se suelta la política de Cloud Armor
Borrar "el backend service"      { gcloud compute backend-services delete $NOMBRE_BACKEND --global --quiet }
Borrar "la política de Cloud Armor" { gcloud compute security-policies delete $NOMBRE_POLITICA --quiet }
Borrar "la serverless NEG"       { gcloud beta compute network-endpoint-groups delete $NOMBRE_NEG --region=$REGION --quiet }
# Una IP reservada sin usar también se cobra
Borrar "la IP estática"          { gcloud compute addresses delete $NOMBRE_IP --global --quiet }
Borrar "el secreto"              { gcloud secrets delete $NOMBRE_SECRETO --quiet }

Write-Host ""
if ($fallos.Count -eq 0) {
    Write-Host "Listo: se borró todo el WAF. El API Gateway sigue en su URL *.gateway.dev."
} else {
    Write-Host "No se pudo borrar: $($fallos -join ', '). Si ya no existían, no hay nada que hacer;"
    Write-Host "si no, revisa el mensaje de gcloud y vuelve a correr el script."
}
Write-Host "Revisa que no quede nada cobrando:  gcloud compute forwarding-rules list; gcloud compute addresses list"
