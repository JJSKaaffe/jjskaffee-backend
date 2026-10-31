# Desplegar el backend en Google Cloud (ADR-0013 y ADR-0014)

> Decisiones: [`doc/adr/adr-0013.md`](../../doc/adr/adr-0013.md) (Google Cloud), [`doc/adr/adr-0014.md`](../../doc/adr/adr-0014.md) (Secret Manager), [`doc/adr/adr-0012.md`](../../doc/adr/adr-0012.md) (PostgreSQL con RLS en Cloud SQL).
> Esta guía deja **Cloud SQL, la imagen, Secret Manager y Cloud Run** listos. Después siguen [`api-gateway.md`](api-gateway.md) (ADR-0017) y [`waf.md`](waf.md) (ADR-0018), en ese orden.

```
Navegador ─► Balanceador + Cloud Armor ─► API Gateway ─► Cloud Run privado (jjskaffee-backend) ─► Cloud SQL (jjskaffee-db)
             (waf.md)                     (api-gateway.md)  esta guía, cuenta backend-sa              conector Java, sin IP autorizada
                                                            JJSKAFFEE_DB_CLAVE ◄── Secret Manager (jjskaffee-db-clave)
```

| Recurso | Nombre |
|---|---|
| Proyecto (el mismo de Firebase) | `jjskafee` |
| Región (Cloud SQL, Artifact Registry, Cloud Run y API Gateway) | `us-east1` |
| Instancia de Cloud SQL | `jjskaffee-db` (PostgreSQL 17, Enterprise, `db-f1-micro`) |
| Base y usuario de la aplicación | `jjskaffee` y `app_jjskaffee` |
| Repositorio de Artifact Registry | `jjskaffee` (Docker) |
| Imagen | `us-east1-docker.pkg.dev/jjskafee/jjskaffee/jjskaffee-backend:<hash del commit>` |
| Servicio de Cloud Run | `jjskaffee-backend` |
| Cuenta de servicio del backend | `backend-sa@jjskafee.iam.gserviceaccount.com` |
| Secretos | `jjskaffee-db-clave` (esta guía) y `jjskaffee-waf-secreto` (`waf.md`) |
| Frontend (Firebase Hosting) | `https://jjskafee.web.app` y `https://jjskafee.firebaseapp.com` |

Ningún secreto pasa por la pantalla, el repo ni la imagen: la clave de `app_jjskaffee` se genera aleatoria directo a Secret Manager y Cloud Run la entrega al backend como `JJSKAFFEE_DB_CLAVE`. Firebase usa las credenciales de `backend-sa` (no hay JSON de cuenta de servicio).

Los comandos son de **PowerShell** y se corren desde la raíz del repo del backend, salvo el paso 4, que es en **Cloud Shell** (bash). Quien despliega es dueño del proyecto (`roles/owner`); si no, necesita los roles de administrador de cada servicio.

## 0. Variables

Pega este bloque en cada ventana nueva de PowerShell antes de seguir:

```powershell
$PROJECT_ID   = "jjskafee"
$REGION       = "us-east1"
$INSTANCIA    = "jjskaffee-db"
$CONEXION_SQL = "${PROJECT_ID}:${REGION}:${INSTANCIA}"          # jjskafee:us-east1:jjskaffee-db
$REPOSITORIO  = "jjskaffee"
$SERVICIO_RUN = "jjskaffee-backend"
$SA_BACKEND   = "backend-sa@$PROJECT_ID.iam.gserviceaccount.com"
$SECRETO_DB   = "jjskaffee-db-clave"

gcloud config set project $PROJECT_ID
```

## 1. Habilitar las APIs

```powershell
gcloud services enable `
  run.googleapis.com sqladmin.googleapis.com artifactregistry.googleapis.com cloudbuild.googleapis.com `
  secretmanager.googleapis.com apigateway.googleapis.com servicemanagement.googleapis.com `
  servicecontrol.googleapis.com compute.googleapis.com iamcredentials.googleapis.com
```

## 2. Crear la instancia de Cloud SQL

PostgreSQL 17, edición **Enterprise** (`db-f1-micro` solo existe ahí; para PostgreSQL 17 la edición por defecto es Enterprise Plus), una zona, 10 GB de HDD (el mínimo) y **sin respaldos automáticos** para ahorrar. Tiene IP pública, pero **sin redes autorizadas**: nadie entra por IP; el backend entra con el conector de Java y la identidad de `backend-sa`. Solo acepta conexiones cifradas.

```powershell
gcloud sql instances create $INSTANCIA `
  --database-version=POSTGRES_17 `
  --edition=ENTERPRISE `
  --tier=db-f1-micro `
  --region=$REGION `
  --availability-type=ZONAL `
  --storage-type=HDD `
  --storage-size=10 `
  --no-storage-auto-increase `
  --no-backup `
  --assign-ip `
  --ssl-mode=ENCRYPTED_ONLY
```

Tarda entre 5 y 10 minutos. Si `gcloud` rechaza `--storage-type=HDD` para esta configuración, quita esa línea (queda SSD de 10 GB, el mínimo).

Comprueba que no haya redes autorizadas (la salida debe estar vacía):

```powershell
gcloud sql instances describe $INSTANCIA --format="value(settings.ipConfiguration.authorizedNetworks)"
```

> `db-f1-micro` admite unas **25 conexiones**. Por eso Cloud Run usa un pool de 5 por instancia (`JJSKAFFEE_DB_POOL=5`, paso 7): con `--max-instances 2` son 10, y quedan conexiones para la consola y los scripts.

## 3. Claves de `postgres` y de `app_jjskaffee`

**`postgres`** (solo para administrar y correr los scripts). Escríbela cuando `gcloud` la pida y guárdala en tu gestor de contraseñas; no queda en el historial de PowerShell:

```powershell
gcloud sql users set-password postgres --instance=$INSTANCIA --prompt-for-password
```

**`app_jjskaffee`** (la del backend). Se genera aleatoria y va directo al secreto `jjskaffee-db-clave`, sin mostrarse. Igual que en `crear-waf.ps1`, se escribe en un archivo temporal sin salto de línea que se borra enseguida:

```powershell
$bytes = New-Object byte[] 32
$generador = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$generador.GetBytes($bytes)
$generador.Dispose()
$temporal = [System.IO.Path]::GetTempFileName()
try {
    # 32 bytes en base64url (43 caracteres, solo letras, números, - y _)
    [System.IO.File]::WriteAllText($temporal, [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_'))
    gcloud secrets create $SECRETO_DB --replication-policy=automatic --data-file=$temporal
} finally {
    Remove-Item $temporal -Force
    Remove-Variable bytes
}
```

El rol `app_jjskaffee` **no** se crea con `gcloud sql users create`: así quedaría en `cloudsqlsuperuser`. Lo crea `db/01` (sin superusuario ni `BYPASSRLS`) y en el paso 4 se le pone la clave del secreto.

## 4. Crear la base con los scripts `db/01` a `db/10` (Cloud Shell)

Abre **Cloud Shell** en la consola de Google Cloud (proyecto `jjskafee`). Ahí ya están `gcloud`, `git` y `psql`.

```bash
gcloud config set project jjskafee
git clone https://github.com/JJSKaaffe/jjskaffee-backend.git
cd jjskaffee-backend
git checkout <rama o commit que vas a desplegar>
```

`gcloud sql connect` autoriza la IP de Cloud Shell durante 5 minutos (después la quita sola) y abre `psql`. Pide la clave de `postgres` del paso 3.

### 4.1 Base `postgres`: rol y base (`db/01`) y clave de `app_jjskaffee`

```bash
gcloud sql connect jjskaffee-db --user=postgres --database=postgres
```

Dentro de `psql`:

```sql
\set ON_ERROR_STOP on
\i db/01_crear_usuario_y_base.sql

-- La clave se lee del secreto dentro de psql: no se muestra ni queda en el historial (allí queda :'clave')
\set clave `gcloud secrets versions access latest --secret=jjskaffee-db-clave`
ALTER ROLE app_jjskaffee PASSWORD :'clave';
\unset clave
\q
```

### 4.2 Base `jjskaffee`: esquema y seeds (`db/02` a `db/10`)

En Cloud SQL, `postgres` **no es superusuario**. En local y en el CI sí lo es, y por eso los seeds pasan por encima del RLS. Aquí, como dueño de las tablas con `FORCE ROW LEVEL SECURITY`, el RLS también lo frena y los `INSERT` de los seeds fallarían (`new row violates row-level security policy`). Por eso, mientras corren los seeds y antes de que exista el backend, se quita el `FORCE` de las cinco tablas que llenan y se vuelve a poner al final. Las migraciones y su orden son los mismos del CI.

```bash
gcloud sql connect jjskaffee-db --user=postgres --database=jjskaffee
```

```sql
\set ON_ERROR_STOP on
\i db/02_esquema.sql

-- Solo mientras corren los seeds (el backend todavía no existe): el dueño (postgres) puede escribir sin cafetería fijada
ALTER TABLE cafeteria        NO FORCE ROW LEVEL SECURITY;
ALTER TABLE usuarios         NO FORCE ROW LEVEL SECURITY;
ALTER TABLE categoria        NO FORCE ROW LEVEL SECURITY;
ALTER TABLE producto         NO FORCE ROW LEVEL SECURITY;
ALTER TABLE producto_adicion NO FORCE ROW LEVEL SECURITY;

\i db/03_seed_cafeterias_y_catalogo.sql
\i db/04_seed_usuarios.sql
\i db/05_cafeteria_activa_e_idempotencia.sql
\i db/06_seed_cafeteria_inactiva.sql
\i db/07_multicafeteria.sql
\i db/08_seed_multicafeteria.sql
\i db/09_ingresos_libres.sql
\i db/10_seed_categorias_ingreso.sql

-- RLS forzado otra vez
ALTER TABLE cafeteria        FORCE ROW LEVEL SECURITY;
ALTER TABLE usuarios         FORCE ROW LEVEL SECURITY;
ALTER TABLE categoria        FORCE ROW LEVEL SECURITY;
ALTER TABLE producto         FORCE ROW LEVEL SECURITY;
ALTER TABLE producto_adicion FORCE ROW LEVEL SECURITY;

-- Debe devolver 0 filas: toda tabla tiene RLS activo y forzado
SELECT relname FROM pg_class
WHERE relnamespace = 'public'::regnamespace AND relkind = 'r'
  AND NOT (relrowsecurity AND relforcerowsecurity);
```

Si un script falla a la mitad, lo más simple es borrar la base y empezar de nuevo el 4.1 sin el `CREATE ROLE` (o borrar también el rol): `DROP DATABASE jjskaffee;` desde la base `postgres`.

### 4.3 Tus membresías reales

Con el RLS ya forzado, se insertan como lo haría la aplicación: fijando la cafetería en la transacción. Joel (`ps7qMBAwzLM4kmfvhEq0yAt0c2D2`) queda ADMINISTRADOR en la cafetería A y CAJERO en la B. Sigue en la misma sesión de `psql` (o vuelve a conectarte como en el 4.2):

```sql
BEGIN;
SELECT set_config('app.current_cafeteria', '11111111-1111-1111-1111-111111111111', true);
INSERT INTO usuarios (id, id_cafeteria, firebase_uid, nombre, rol)
VALUES (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'ps7qMBAwzLM4kmfvhEq0yAt0c2D2', 'Joel', 'ADMINISTRADOR');

SELECT set_config('app.current_cafeteria', '22222222-2222-2222-2222-222222222222', true);
INSERT INTO usuarios (id, id_cafeteria, firebase_uid, nombre, rol)
VALUES (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 'ps7qMBAwzLM4kmfvhEq0yAt0c2D2', 'Joel', 'CAJERO');
COMMIT;

-- Comprobación con la identidad (lo mismo que hace GET /acceso/cafeterias): 2 filas
BEGIN;
SELECT set_config('app.current_uid', 'ps7qMBAwzLM4kmfvhEq0yAt0c2D2', true);
SELECT id_cafeteria, rol, activo FROM usuarios;
COMMIT;
\q
```

Al salir, revisa que la IP de Cloud Shell ya no esté autorizada (salida vacía; si no, quítala):

```bash
gcloud sql instances describe jjskaffee-db --format="value(settings.ipConfiguration.authorizedNetworks)"
# gcloud sql instances patch jjskaffee-db --clear-authorized-networks
```

## 5. Artifact Registry y la imagen

```powershell
gcloud artifacts repositories create $REPOSITORIO `
  --repository-format=docker --location=$REGION --description="Imágenes del backend de JJSKaffee"
```

La imagen se construye en **Cloud Build** con el [`Dockerfile`](../../Dockerfile) del repo (compila con el Maven wrapper sobre Temurin 27 y corre con un JRE 27, como usuario sin privilegios). Lo que se sube lo filtra [`.gcloudignore`](../../.gcloudignore): nunca `.env`, JSON de credenciales, `target`, `docs`, `db` ni `infra`. La etiqueta es el hash corto del commit, así cada imagen dice de qué código salió; por eso, haz commit antes de construir:

```powershell
if (git status --porcelain) { throw "Hay cambios sin commit: la etiqueta no correspondería al código." }
$ETIQUETA = (git rev-parse --short HEAD).Trim()
$IMAGEN   = "$REGION-docker.pkg.dev/$PROJECT_ID/$REPOSITORIO/${SERVICIO_RUN}:$ETIQUETA"

gcloud builds submit --region=$REGION --tag $IMAGEN .
```

Tarda unos minutos (descarga Maven y las dependencias). Para comprobarla en tu PC antes de subirla (si tienes Docker): `docker build -t jjskaffee-backend:local .`.

## 6. Cuenta de servicio `backend-sa` (permisos mínimos)

`backend-sa` solo puede conectarse a Cloud SQL y leer **sus** secretos. Verificar tokens de Firebase no necesita ningún rol: el SDK descarga las llaves públicas de Google.

```powershell
gcloud iam service-accounts create backend-sa --display-name "Backend de JJSKaffee (Cloud Run)"

# Conectarse a Cloud SQL con el conector de Java
gcloud projects add-iam-policy-binding $PROJECT_ID `
  --member "serviceAccount:$SA_BACKEND" --role "roles/cloudsql.client" --condition=None

# Leer solo el secreto de la base (no todos los del proyecto)
gcloud secrets add-iam-policy-binding $SECRETO_DB `
  --member "serviceAccount:$SA_BACKEND" --role "roles/secretmanager.secretAccessor"
```

Cuando se monte el WAF, `waf.md` (paso 3) le da el mismo rol sobre `jjskaffee-waf-secreto`, solo sobre ese secreto.

## 7. Desplegar en Cloud Run

Privado (`--no-allow-unauthenticated`), con `backend-sa`, 1 GiB, de 0 a 2 instancias y más CPU al arrancar (`--cpu-boost`) para que Spring Boot arranque rápido desde cero.

Las variables llevan comas (la URL de la base no, pero `JJSKAFFEE_CORS_ORIGENES` sí), así que se cambia el separador de `gcloud` con `^;^` al inicio: desde ahí las variables se separan con `;`. En PowerShell `^` y `&` no son especiales dentro de comillas dobles.

```powershell
$URL_DB = "jdbc:postgresql:///jjskaffee?cloudSqlInstance=$CONEXION_SQL&socketFactory=com.google.cloud.sql.postgres.SocketFactory"

gcloud run deploy $SERVICIO_RUN `
  --image $IMAGEN `
  --region $REGION `
  --service-account $SA_BACKEND `
  --no-allow-unauthenticated `
  --memory 1Gi `
  --cpu 1 `
  --min-instances 0 `
  --max-instances 2 `
  --cpu-boost `
  --port 8080 `
  --set-env-vars "^;^SPRING_PROFILES_ACTIVE=gcp;JJSKAFFEE_DB_URL=$URL_DB;JJSKAFFEE_DB_USUARIO=app_jjskaffee;JJSKAFFEE_DB_POOL=5;JJSKAFFEE_FIREBASE_PROJECT_ID=jjskafee;JJSKAFFEE_CORS_ORIGENES=https://jjskafee.web.app,https://jjskafee.firebaseapp.com;JJSKAFFEE_WAF_SECRETO=" `
  --set-secrets "JJSKAFFEE_DB_CLAVE=${SECRETO_DB}:latest"
```

| Variable | Valor | Para qué |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `gcp` | Prefijo `/api`, CORS y WAF ([`application-gcp.properties`](../../src/main/resources/application-gcp.properties)) |
| `JJSKAFFEE_DB_URL` | `jdbc:postgresql:///jjskaffee?cloudSqlInstance=jjskafee:us-east1:jjskaffee-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory` | Sin host: `postgres-socket-factory` abre la conexión cifrada con la identidad de `backend-sa` |
| `JJSKAFFEE_DB_USUARIO` | `app_jjskaffee` | Usuario limitado, sujeto a RLS (nunca `postgres`) |
| `JJSKAFFEE_DB_CLAVE` | secreto `jjskaffee-db-clave:latest` | Clave de `app_jjskaffee` desde Secret Manager (ADR-0014) |
| `JJSKAFFEE_DB_POOL` | `5` | Conexiones por instancia (`db-f1-micro` admite ~25) |
| `JJSKAFFEE_FIREBASE_PROJECT_ID` | `jjskafee` | Proyecto de Firebase de los tokens. `JJSKAFFEE_FIREBASE_CREDENCIALES` no se define: se usan las credenciales de `backend-sa` |
| `JJSKAFFEE_CORS_ORIGENES` | `https://jjskafee.web.app,https://jjskafee.firebaseapp.com` | Orígenes del frontend en Firebase Hosting |
| `JJSKAFFEE_WAF_SECRETO` | vacía | Sin WAF todavía, no se exige `X-JJSKaffee-Origen`. `waf.md` (paso 3) la cambia por el secreto `jjskaffee-waf-secreto` |

**Versiones nuevas**: repite el paso 5 con el commit nuevo y despliega solo la imagen (las variables y el secreto se conservan):

```powershell
gcloud run deploy $SERVICIO_RUN --image $IMAGEN --region $REGION
```

Si cambia la clave de la base: agrega una versión al secreto (`gcloud secrets versions add jjskaffee-db-clave --data-file=...`, con el mismo bloque del paso 3), cámbiala en la base (`ALTER ROLE` del paso 4.1) y despliega una revisión nueva para que la lea (`gcloud run services update $SERVICIO_RUN --region $REGION --update-secrets "JJSKAFFEE_DB_CLAVE=${SECRETO_DB}:latest"`).

## 8. Verificar

**Arranque** (busca `Started ...` y que no haya `APPLICATION FAILED TO START`):

```powershell
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=$SERVICIO_RUN" `
  --freshness=15m --limit=50 --format="value(timestamp, severity, textPayload)"
```

**Privado**: sin credenciales de Google, Cloud Run responde 403 sin llegar al backend:

```powershell
$URL_RUN = (gcloud run services describe $SERVICIO_RUN --region $REGION --format "value(status.url)").Trim()
curl.exe -i "$URL_RUN/api/acceso/cafeterias"
```

**Backend, Cloud SQL y Secret Manager** (opcional, antes del gateway): imita lo que hará el gateway. Tu cuenta de Google invoca Cloud Run y el token de Firebase va en `X-Forwarded-Authorization` (sácalo de la petición 0 de la colección de Postman). Debe responder 200 con tus dos cafeterías; eso prueba la conexión a Cloud SQL con la clave del secreto y el RLS por uid:

```powershell
$TOKEN_GOOGLE   = (gcloud auth print-identity-token).Trim()
$TOKEN_FIREBASE = "<ID token de Firebase>"
curl.exe -i "$URL_RUN/api/acceso/cafeterias" `
  -H "Authorization: Bearer $TOKEN_GOOGLE" `
  -H "X-Forwarded-Authorization: Bearer $TOKEN_FIREBASE"
Remove-Variable TOKEN_GOOGLE, TOKEN_FIREBASE
```

Sin `X-Forwarded-Authorization`, el backend responde 401 (ProblemDetail): el token de Google no es de Firebase. Eso también confirma que la petición llegó al backend.

| Síntoma | Causa probable |
|---|---|
| La revisión no arranca: `Could not resolve placeholder 'JJSKAFFEE_...'` | Falta una variable del paso 7 |
| La revisión no arranca: `Permission denied on secret` | `backend-sa` no tiene `secretAccessor` sobre `jjskaffee-db-clave` (paso 6) |
| 500 al llamar y en los registros `cloudsql.instances.connect` o `NOT_AUTHORIZED` | `backend-sa` no tiene `roles/cloudsql.client`, o la URL tiene mal el nombre de conexión (`jjskafee:us-east1:jjskaffee-db`) |
| `password authentication failed for user "app_jjskaffee"` | La clave de la base no es la del secreto: repite el `ALTER ROLE` del paso 4.1 |
| `remaining connection slots are reserved` | Demasiadas conexiones para `db-f1-micro`: baja `JJSKAFFEE_DB_POOL` o `--max-instances` |
| `gcloud builds submit` falla con permisos de Artifact Registry o de registros | La cuenta con la que corre Cloud Build (en proyectos nuevos, la de Compute por defecto) necesita `roles/artifactregistry.writer` y `roles/logging.logWriter` |
| 404 en todo | No corre con el perfil `gcp` (falta `/api`) |

## 9. Siguientes pasos

1. [`api-gateway.md`](api-gateway.md): cuenta `gateway-invoker`, la única con `roles/run.invoker`, y el gateway en `us-east1`.
2. [`waf.md`](waf.md): balanceador con Cloud Armor y el secreto `jjskaffee-waf-secreto` en Cloud Run.
3. Frontend: `VITE_API_URL=https://<ip-con-guiones>.sslip.io/api` (o la del gateway mientras no haya WAF).

## Apagar y borrar todo

> **El crédito de Google Cloud vence el 29 de enero de 2027.** Antes de esa fecha borra todo (o cambia la facturación); después, lo que siga creado se cobra a la tarjeta. Conviene crear un presupuesto con alertas en **Facturación → Presupuestos y alertas**.

**Pausar sin borrar** (entre entregas): Cloud Run con `--min-instances 0` no cobra si nadie lo llama. Cloud SQL sí cobra mientras está encendido; apágalo (el disco y la IP se siguen cobrando, mucho menos):

```powershell
gcloud sql instances patch $INSTANCIA --activation-policy=NEVER    # apagar
gcloud sql instances patch $INSTANCIA --activation-policy=ALWAYS   # encender
```

**Borrar**, en orden inverso a como se creó (variables del paso 0):

```powershell
# 1. WAF: primero el paso 8 de waf.md (dejar JJSKAFFEE_WAF_SECRETO vacía) y después
.\infra\waf\eliminar-waf.ps1

# 2. API Gateway (nombres de api-gateway.md) y su cuenta
gcloud api-gateway gateways delete jjskaffee-gateway --location $REGION --quiet
gcloud api-gateway api-configs list --api jjskaffee-api --format "value(name.basename())" |
  ForEach-Object { gcloud api-gateway api-configs delete $_ --api jjskaffee-api --quiet }
gcloud api-gateway apis delete jjskaffee-api --quiet
gcloud iam service-accounts delete "gateway-invoker@$PROJECT_ID.iam.gserviceaccount.com" --quiet

# 3. Cloud Run y su cuenta
gcloud run services delete $SERVICIO_RUN --region $REGION --quiet
gcloud projects remove-iam-policy-binding $PROJECT_ID --member "serviceAccount:$SA_BACKEND" --role "roles/cloudsql.client" --condition=None
gcloud iam service-accounts delete $SA_BACKEND --quiet

# 4. Imágenes y el código que subió Cloud Build
gcloud artifacts repositories delete $REPOSITORIO --location $REGION --quiet
gcloud storage rm --recursive "gs://${PROJECT_ID}_cloudbuild"

# 5. Secreto de la base
gcloud secrets delete $SECRETO_DB --quiet

# 6. Cloud SQL (borra la base y sus datos; no hay respaldos)
gcloud sql instances patch $INSTANCIA --no-deletion-protection
gcloud sql instances delete $INSTANCIA --quiet
```

Revisa que no quede nada cobrando:

```powershell
gcloud sql instances list; gcloud run services list; gcloud artifacts repositories list
gcloud api-gateway gateways list; gcloud compute forwarding-rules list; gcloud compute addresses list
gcloud secrets list
```

**No borres el proyecto `jjskafee`** salvo que también quieras perder Firebase: Authentication (usuarios) y Hosting (frontend) viven en el mismo proyecto.
