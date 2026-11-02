# Montar el WAF (Cloud Armor) delante del API Gateway (ADR-0018)

> Decisión: [`doc/adr/adr-0018.md`](../../doc/adr/adr-0018.md). Scripts: [`infra/waf/crear-waf.ps1`](../../infra/waf/crear-waf.ps1) y [`infra/waf/eliminar-waf.ps1`](../../infra/waf/eliminar-waf.ps1).
> Antes: el API Gateway ya debe estar desplegado ([`api-gateway.md`](api-gateway.md)).

```
Navegador ─► Balanceador HTTPS + Cloud Armor ─► API Gateway ─► Cloud Run privado ─► Cloud SQL
             https://<ip-con-guiones>.sslip.io     (serverless NEG)
             agrega X-JJSKaffee-Origen: <secreto>
```

- **Cloud Armor** revisa cada petición con reglas OWASP (inyección SQL, XSS, archivos, ejecución remota, escáneres y ataques de protocolo) y limita a 100 peticiones por minuto por IP.
- **Dominio**: no hay dominio propio. El balanceador usa una IP estática y el dominio gratuito `<ip-con-guiones>.sslip.io` (por ejemplo `34-120-1-2.sslip.io`), que resuelve solo a esa IP. Google le da un certificado HTTPS.
- **Para que no se salten el WAF**: la URL `*.gateway.dev` del gateway no se puede apagar. El balanceador agrega a cada petición el encabezado `X-JJSKaffee-Origen` con un valor secreto, y el backend (`OrigenWafFilter`) responde 403 a toda petición que no lo traiga, antes de revisar el token. El valor solo está en Secret Manager (`jjskaffee-waf-secreto`): nunca en el repo.

Costo aproximado mientras esté montado: unos USD 31 al mes (balanceador, política y reglas) más USD 0,75 por millón de peticiones. Cuando termine la evaluación, bórralo (paso 8).

## 1. Requisitos

- `gcloud` con sesión iniciada y los componentes beta: `gcloud components install beta`.
- Permisos en el proyecto para crear balanceadores, políticas de Cloud Armor y secretos (por ejemplo `roles/compute.loadBalancerAdmin`, `roles/compute.securityAdmin` y `roles/secretmanager.admin`).
- Windows no deja correr scripts por defecto. Habilítalo solo para la ventana actual:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

## 2. Crear el WAF

Las variables de arriba de `infra/waf/crear-waf.ps1` ya traen los valores del despliegue: `PROJECT_ID = jjskafee`, `REGION = us-east1` (la del API Gateway), `GATEWAY_ID = jjskaffee-gateway` y `NOMBRE_POLITICA = jjskaffee-waf`. Revísalas y, desde la raíz del repo:

```powershell
.\infra\waf\crear-waf.ps1
```

El script:
1. Reserva una IP estática global y calcula el dominio `<ip-con-guiones>.sslip.io`.
2. Crea una serverless NEG que apunta al gateway.
3. Crea el secreto `jjskaffee-waf-secreto` con un valor aleatorio de 64 caracteres (si no existe) y el backend service con el encabezado `X-JJSKaffee-Origen`. Deja el registro de peticiones activado.
4. Crea la política de Cloud Armor con las reglas OWASP (sensibilidad 1, `deny(403)`), el límite por IP (`deny(429)`) y la regla por defecto en permitir. **Todas quedan en modo preview**: registran lo que harían, pero no bloquean.
5. Asocia la política y crea el URL map, el certificado, el proxy HTTPS y la regla de reenvío en el puerto 443.
6. Imprime la URL `https://<dominio>/api`.

El certificado puede tardar **hasta 60 minutos** en quedar `ACTIVE`. Mientras tanto, HTTPS falla. Revísalo así:

```powershell
gcloud compute ssl-certificates describe jjskaffee-cert --global --format="value(managed.status)"
```

Si el script falla a la mitad, corre `eliminar-waf.ps1` y vuelve a empezar.

## 3. Pedirle a Cloud Run el encabezado

Hazlo **cuando el certificado esté `ACTIVE`**. Desde ese momento la URL `*.gateway.dev` responde 403 y el frontend debe usar la nueva (paso 7).

```powershell
$PROJECT_ID   = "jjskafee"
$REGION_RUN   = "us-east1"             # la región del servicio de Cloud Run
$SERVICIO_RUN = "jjskaffee-backend"

# Cuenta con la que corre Cloud Run (gcp.md, paso 6)
$SA_RUN = "backend-sa@$PROJECT_ID.iam.gserviceaccount.com"

# Que pueda leer solo este secreto (ya lee jjskaffee-db-clave, también solo ese)
gcloud secrets add-iam-policy-binding jjskaffee-waf-secreto --member "serviceAccount:$SA_RUN" --role "roles/secretmanager.secretAccessor"

# En el paso 7 de gcp.md quedó como variable vacía: se quita, porque Cloud Run no deja tener
# una variable y un secreto con el mismo nombre
gcloud run services update $SERVICIO_RUN --region $REGION_RUN --remove-env-vars JJSKAFFEE_WAF_SECRETO

# El backend lee el secreto desde Secret Manager; el valor nunca pasa por tu pantalla
gcloud run services update $SERVICIO_RUN --region $REGION_RUN --update-secrets "JJSKAFFEE_WAF_SECRETO=jjskaffee-waf-secreto:latest"
```

El primer comando puede terminar con error: esa revisión no arranca porque le falta la variable. Cloud Run sigue atendiendo con la revisión anterior, y el segundo comando lo deja bien.

## 4. Probar que funciona

```powershell
$DOMINIO = "<ip-con-guiones>.sslip.io"
$TOKEN   = "<ID token de Firebase>"        # p. ej. el de la petición 0 de Postman
$GATEWAY = "<gateway>.gateway.dev"         # defaultHostname del gateway
```

| Prueba | Comando | Respuesta esperada |
|---|---|---|
| Entrar por el WAF con token | `curl.exe -i "https://$DOMINIO/api/acceso/cafeterias" -H "Authorization: Bearer $TOKEN"` | 200 |
| Entrar por el WAF sin token | `curl.exe -i "https://$DOMINIO/api/acceso/cafeterias"` | 401 del gateway |
| Saltarse el WAF (directo al gateway) | `curl.exe -i "https://$GATEWAY/api/acceso/cafeterias" -H "Authorization: Bearer $TOKEN"` | 403 del backend (`Acceso no permitido`) |
| Inyección SQL (`?q=' OR '1'='1`) | `curl.exe -i "https://$DOMINIO/api/movimientos?q=%27%20OR%20%271%27%3D%271"` | En preview: 401 (pasa el WAF y el gateway pide token). **Activo: 403 de Cloud Armor** |
| Límite por IP | `1..120 \| ForEach-Object { curl.exe -s -o NUL -w "%{http_code} " "https://$DOMINIO/api/acceso/cafeterias" }` | En preview: siempre 401. **Activo: 429 después de unas 100** |

## 5. Revisar el modo preview en Cloud Logging

Deja las reglas en preview unos días mientras el equipo usa la aplicación normalmente (ventas, ingresos libres, egresos, movimientos). Después mira qué **habría bloqueado** Cloud Armor.

En la consola: **Logging → Explorador de registros**, con esta consulta:

```
resource.type="http_load_balancer"
jsonPayload.previewSecurityPolicy.name="jjskaffee-waf"
jsonPayload.previewSecurityPolicy.outcome="DENY"
```

O desde PowerShell (sin comillas dobles dentro, para que lleguen bien a `gcloud`):

```powershell
gcloud logging read "resource.type=http_load_balancer AND jsonPayload.previewSecurityPolicy.name=jjskaffee-waf AND jsonPayload.previewSecurityPolicy.outcome=DENY" `
  --freshness=7d --limit=50 `
  --format="table(timestamp, httpRequest.requestMethod, httpRequest.requestUrl, jsonPayload.previewSecurityPolicy.priority, jsonPayload.previewSecurityPolicy.preconfiguredExprIds)"
```

Cómo leerlo:
- `priority` dice qué regla saltó: 1000 SQL, 1001 XSS, 1002 archivos, 1003 ejecución remota, 1004 escáneres, 1005 protocolo y 2000 límite por IP.
- `preconfiguredExprIds` dice qué firma exacta saltó (por ejemplo `owasp-crs-v030301-id942100-sqli`).
- Si solo aparecen pruebas de ataque o tráfico raro, se pueden activar las reglas.
- Si aparece una petición **legítima** (por ejemplo una venta cuya descripción tiene comillas), es un falso positivo. Excluye esa firma en su regla y vuelve a revisar:

```powershell
gcloud compute security-policies rules update 1000 --security-policy=jjskaffee-waf `
  --expression="evaluatePreconfiguredWaf('sqli-v33-stable', {'sensitivity': 1, 'opt_out_rule_ids': ['owasp-crs-v030301-id942100-sqli']})"
```

## 6. Activar las reglas

Quita el preview de todas las reglas. Es el bloque comentado al final del paso 4 de `crear-waf.ps1`:

```powershell
foreach ($prioridad in 1000, 1001, 1002, 1003, 1004, 1005, 2000) {
    gcloud compute security-policies rules update $prioridad --security-policy=jjskaffee-waf --no-preview
}
```

Los cambios tardan unos minutos en aplicarse. Repite las pruebas del paso 4: la inyección SQL debe dar **403** y el límite por IP **429**. Para volver a modo preview, usa el mismo comando con `--preview`.

## 7. Frontend y Postman

- En el build de producción del frontend: `VITE_API_URL=https://<ip-con-guiones>.sslip.io/api`. Ya no se usa la URL `*.gateway.dev`.
- En Postman: `baseUrl = https://<ip-con-guiones>.sslip.io/api`.
- El origen del frontend no cambia, así que `JJSKAFFEE_CORS_ORIGENES` queda igual.

## 8. Quitar el WAF después de la evaluación

1. Primero, que el backend deje de exigir el encabezado. Si no lo haces, la URL `*.gateway.dev` seguiría respondiendo 403 y Cloud Run fallaría al arrancar sin el secreto:

```powershell
gcloud run services update $SERVICIO_RUN --region $REGION_RUN --remove-secrets JJSKAFFEE_WAF_SECRETO
gcloud run services update $SERVICIO_RUN --region $REGION_RUN --update-env-vars "JJSKAFFEE_WAF_SECRETO="
```

2. Borra todo (mismas variables que en `crear-waf.ps1`):

```powershell
.\infra\waf\eliminar-waf.ps1
```

3. El frontend vuelve a `VITE_API_URL=https://<gateway>.gateway.dev/api`.

## Cambiar el secreto

Si el valor se filtró: crea una versión nueva del secreto, pon el nuevo valor en el backend service y despliega una revisión de Cloud Run para que lo lea. Entre el segundo y el tercer comando, las peticiones dan 403 durante un momento.

```powershell
# Usa el mismo bloque que crea el valor en crear-waf.ps1, pero con: gcloud secrets versions add jjskaffee-waf-secreto --data-file=$temporal
$VALOR = (gcloud secrets versions access latest --secret=jjskaffee-waf-secreto | Out-String).Trim()
gcloud compute backend-services update jjskaffee-backend-lb --global --custom-request-header="X-JJSKaffee-Origen:$VALOR"
Remove-Variable VALOR
gcloud run services update $SERVICIO_RUN --region $REGION_RUN --update-secrets "JJSKAFFEE_WAF_SECRETO=jjskaffee-waf-secreto:latest"
```

El valor se puede ver en la configuración del backend service. Por eso solo el equipo debe tener acceso al proyecto.

## Si algo falla

| Síntoma | Causa probable |
|---|---|
| Error de certificado o de conexión HTTPS en el dominio sslip.io | El certificado todavía no está `ACTIVE` (hasta 60 minutos) |
| 403 `Acceso no permitido` entrando por el dominio sslip.io | El secreto de Cloud Run y el del backend service no coinciden: vuelve a correr el bloque de "Cambiar el secreto" |
| Cloud Run no arranca después del paso 3 | `backend-sa` no tiene `roles/secretmanager.secretAccessor` sobre `jjskaffee-waf-secreto` |
| 403 de Cloud Armor en una petición legítima | Falso positivo: búscalo en el paso 5 y excluye la firma |
| 404 o 502 del balanceador | La NEG apunta a otro `GATEWAY_ID` o a otra región |
| El navegador bloquea por CORS | El preflight también pasa por el WAF; revisa `JJSKAFFEE_CORS_ORIGENES` y que el preflight no esté marcado en el paso 5 |
