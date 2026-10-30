# Desplegar el API Gateway (ADR-0017)

> Decisión: [`doc/adr/adr-0017.md`](../../doc/adr/adr-0017.md). Archivo del gateway: [`infra/api-gateway/openapi.yaml`](../../infra/api-gateway/openapi.yaml).
> Fuera de esta guía: Cloud SQL, Secret Manager (ADR-0014) y la imagen del backend. Aquí se supone que el backend ya está desplegado en Cloud Run.

```
Navegador ──(token de Firebase)──► API Gateway ──(token de gateway-invoker + X-Forwarded-Authorization)──► Cloud Run privado
             https://<gateway>/api/...           valida el token de Firebase                                 /api/... (perfil gcp)
```

- El gateway valida el token de Firebase (emisor `https://securetoken.google.com/jjskafee`, audiencia `jjskafee`) y rechaza con 401 lo que no lo traiga, antes de llegar a Cloud Run.
- Llama a Cloud Run con el token de la cuenta de servicio `gateway-invoker` en `Authorization` y copia el token original a `X-Forwarded-Authorization`. `FirebaseAuthFilter` usa ese encabezado si viene; el backend vuelve a verificar el token y la membresía (no se confía solo en el gateway).
- `path_translation: APPEND_PATH_TO_ADDRESS` conserva la ruta completa: `/api/ingresos` llega a Cloud Run como `/api/ingresos`. Por eso el perfil `gcp` usa `server.servlet.context-path=/api`. En local la API sigue sin prefijo.
- Las operaciones `OPTIONS` no piden token, así el preflight de CORS llega al backend, que lo responde con `jjskaffee.cors.origenes`.

## Marcadores

| Marcador | Qué es | Ejemplo |
|---|---|---|
| `PROJECT_ID` | Proyecto de Google Cloud | `jjskafee` |
| `REGION` | Región del gateway y del servicio de Cloud Run | `us-central1` |
| `CLOUD_RUN_URL` | URL del servicio de Cloud Run, **sin `/` al final** | `https://jjskaffee-backend-abc123-uc.a.run.app` |

API Gateway solo existe en algunas regiones (p. ej. `us-central1`, `us-east1`, `us-east4`, `us-west2`, `europe-west1`, `asia-northeast1`). Si Cloud Run está en otra región (p. ej. `southamerica-east1`), usa esa región en los comandos `gcloud run ...` y una región soportada para el gateway.

Todos los comandos son de PowerShell y se corren desde la raíz del repo del backend.

## 0. Variables

```powershell
$PROJECT_ID    = "PROJECT_ID"
$REGION        = "REGION"
$CLOUD_RUN_URL = "CLOUD_RUN_URL"
$SERVICIO_RUN  = "jjskaffee-backend"          # nombre del servicio en Cloud Run
$API_ID        = "jjskaffee-api"
$GATEWAY_ID    = "jjskaffee-gateway"
$CONFIG_ID     = "jjskaffee-config-$(Get-Date -Format 'yyyyMMdd-HHmm')"   # las api-config no se editan: cada cambio es una nueva
$SA            = "gateway-invoker@$PROJECT_ID.iam.gserviceaccount.com"

gcloud config set project $PROJECT_ID
```

Quien despliega necesita `roles/apigateway.admin`, permiso para crear cuentas de servicio y `roles/iam.serviceAccountUser` sobre `gateway-invoker` (para poder usarla en la api-config).

## 1. Habilitar las APIs

```powershell
gcloud services enable apigateway.googleapis.com servicemanagement.googleapis.com servicecontrol.googleapis.com
```

## 2. Cloud Run privado y con el perfil `gcp`

Cloud Run va con **`--no-allow-unauthenticated`**: nadie lo puede llamar desde internet, solo la cuenta del gateway. Al desplegar el backend, incluye esa opción en `gcloud run deploy`. Si el servicio ya estaba público, quítale el acceso a `allUsers`:

```powershell
gcloud run services remove-iam-policy-binding $SERVICIO_RUN --region $REGION --member "allUsers" --role "roles/run.invoker"
```

El backend debe correr con el perfil `gcp` y saber el origen del frontend (sin `JJSKAFFEE_CORS_ORIGENES` no arranca):

```powershell
gcloud run services update $SERVICIO_RUN --region $REGION `
  --update-env-vars "SPRING_PROFILES_ACTIVE=gcp,JJSKAFFEE_CORS_ORIGENES=https://jjskafee.web.app"
```

Con varios orígenes, la lista lleva comas; cambia el separador de `gcloud` así: `--update-env-vars "^;^SPRING_PROFILES_ACTIVE=gcp;JJSKAFFEE_CORS_ORIGENES=https://jjskafee.web.app,https://jjskafee.firebaseapp.com"`.

## 3. Cuenta de servicio `gateway-invoker`

Solo puede invocar **este** servicio de Cloud Run (el rol se da sobre el servicio, no sobre el proyecto):

```powershell
gcloud iam service-accounts create gateway-invoker --display-name "API Gateway -> Cloud Run (JJSKaffee)"

gcloud run services add-iam-policy-binding $SERVICIO_RUN --region $REGION `
  --member "serviceAccount:$SA" --role "roles/run.invoker"
```

Comprueba que en la política solo estén `gateway-invoker` (y no `allUsers`):

```powershell
gcloud run services get-iam-policy $SERVICIO_RUN --region $REGION
```

## 4. Generar `openapi.desplegado.yaml`

`openapi.yaml` tiene el marcador `CLOUD_RUN_URL` en `x-google-backend.address` y `jwt_audience`. La copia con la URL real va en `infra/api-gateway/openapi.desplegado.yaml`, que está en `.gitignore`:

```powershell
$plantilla  = Join-Path (Get-Location) "infra\api-gateway\openapi.yaml"
$desplegado = Join-Path (Get-Location) "infra\api-gateway\openapi.desplegado.yaml"
$contenido  = [System.IO.File]::ReadAllText($plantilla).Replace("CLOUD_RUN_URL", $CLOUD_RUN_URL)
[System.IO.File]::WriteAllText($desplegado, $contenido)    # UTF-8 sin BOM

Select-String -Path $desplegado -Pattern "address:|jwt_audience:"   # deben mostrar la URL real
git status --short infra                                             # openapi.desplegado.yaml no debe aparecer
```

## 5. Crear el API, la api-config y el gateway

```powershell
gcloud api-gateway apis create $API_ID --project $PROJECT_ID

gcloud api-gateway api-configs create $CONFIG_ID --api $API_ID --project $PROJECT_ID `
  --openapi-spec $desplegado `
  --backend-auth-service-account $SA

gcloud api-gateway gateways create $GATEWAY_ID --api $API_ID --api-config $CONFIG_ID `
  --location $REGION --project $PROJECT_ID
```

Crear el gateway tarda unos minutos.

**Cambios posteriores** (una ruta nueva en `openapi.yaml`): repite el paso 4, crea otra api-config con un `$CONFIG_ID` nuevo y apunta el gateway a ella:

```powershell
gcloud api-gateway gateways update $GATEWAY_ID --api $API_ID --api-config $CONFIG_ID --location $REGION --project $PROJECT_ID
```

## 6. Obtener la URL del gateway

```powershell
$GATEWAY_HOST = gcloud api-gateway gateways describe $GATEWAY_ID --location $REGION --project $PROJECT_ID --format "value(defaultHostname)"
$BASE_URL = "https://$GATEWAY_HOST/api"
$BASE_URL
```

## 7. Probar

Sin token, el gateway responde 401 sin llegar al backend (en el formato de Google, `{"code":401,"message":"Jwt is missing"}`, no en ProblemDetail):

```powershell
curl.exe -i "$BASE_URL/acceso/cafeterias"
```

Cloud Run directo, sin pasar por el gateway, responde 403 (es privado):

```powershell
curl.exe -i "$CLOUD_RUN_URL/api/acceso/cafeterias"
```

Preflight de CORS (sin token), debe traer `Access-Control-Allow-Origin` con el origen del frontend:

```powershell
curl.exe -i -X OPTIONS "$BASE_URL/ingresos" `
  -H "Origin: https://jjskafee.web.app" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: authorization,content-type,x-cafeteria-id,idempotency-key"
```

### Postman

Importa [`docs/postman/jjskaffee-registrar-ingreso.postman_collection.json`](../postman/jjskaffee-registrar-ingreso.postman_collection.json) y cambia la variable **`baseUrl` a `https://<gateway>/api`** (el valor de `$BASE_URL`). El resto queda igual: el token va en `Authorization: Bearer ...` (el gateway lo pasa a `X-Forwarded-Authorization`) y `X-Cafeteria-Id` e `Idempotency-Key` pasan sin cambios. Corre la colección en orden: login, mis cafeterías, yo, catálogo, venta, ingreso libre y movimientos.

## 8. Frontend

En el repo del frontend, la variable del build de producción es:

```
VITE_API_URL=https://<gateway>/api
```

El cliente HTTP del frontend le agrega la ruta (`/acceso/cafeterias`, `/ingresos`, ...). En local sigue `VITE_API_URL=/api` con el proxy de Vite hacia `http://localhost:8080`. El origen donde se publique el frontend debe estar en `JJSKAFFEE_CORS_ORIGENES` (paso 2).

## Si algo falla

| Síntoma | Causa probable |
|---|---|
| 401 del gateway con un token que sí es de Firebase | El token es de otro proyecto de Firebase (emisor o audiencia distintos de `jjskafee`) o ya venció |
| 403 del gateway, `PERMISSION_DENIED` desde Cloud Run | `gateway-invoker` no tiene `roles/run.invoker` sobre el servicio o la api-config se creó sin `--backend-auth-service-account` |
| 404 en todas las rutas | El backend no corre con el perfil `gcp` (falta el prefijo `/api`) o la URL de Cloud Run quedó con `/` al final |
| 404 del gateway en una ruta que sí existe en el backend | Falta en `openapi.yaml` (`ContratoApiGatewayTest` debió fallar) o el gateway apunta a una api-config vieja |
| 401 del backend con token válido | El backend leyó el token del gateway: revisa que llegue `X-Forwarded-Authorization` |
| El navegador bloquea por CORS | Falta el origen del frontend en `JJSKAFFEE_CORS_ORIGENES` |
