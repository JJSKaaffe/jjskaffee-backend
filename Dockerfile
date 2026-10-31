# -----------------------------------------------------------------------------
# Imagen del backend para Cloud Run (ADR-0013). Guía: docs/despliegue/gcp.md
#
# Dos etapas: compila con el Maven wrapper sobre un JDK 27 y corre el jar sobre un JRE 27, sin Maven ni
# código fuente. Imágenes oficiales de Eclipse Temurin (la misma distribución de Java que usa el CI), fijadas
# por digest: un tag se puede mover a otra imagen, un digest no. Para actualizar, cambia tag y digest juntos
# (docker buildx imagetools inspect eclipse-temurin:<tag>).
#
# Sin secretos: la URL de la base, el usuario y los orígenes llegan como variables de entorno de Cloud Run,
# y la clave de la base y el secreto del WAF, desde Secret Manager (--set-secrets).
# -----------------------------------------------------------------------------

# ----- 1. Compilación -----
FROM eclipse-temurin:27_35-jdk@sha256:473d02d477a8152a05dc42fde10b965754f1863d45e586a97763ef298965c48b AS compilacion
WORKDIR /fuente

# Primero solo el wrapper y el pom: si no cambian, Docker reutiliza la capa con las dependencias ya descargadas
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Las pruebas ya las corre el CI (./mvnw verify)
COPY src src
RUN ./mvnw -B -DskipTests package && cp target/*.jar /fuente/app.jar

# ----- 2. Ejecución -----
FROM eclipse-temurin:27_35-jre@sha256:0dd0465f90d398fa3226b56a3973f5d3c47ac323934dceebdab45ac8910bee6e

# Usuario sin privilegios y sin shell de inicio de sesión: el proceso nunca corre como root
RUN groupadd --system --gid 10001 jjskaffee \
    && useradd --system --uid 10001 --gid jjskaffee --no-create-home --shell /usr/sbin/nologin jjskaffee

WORKDIR /app
COPY --from=compilacion --chown=root:root /fuente/app.jar app.jar
USER 10001:10001

# Cloud Run manda las peticiones al puerto de la variable PORT (8080 por defecto); el perfil gcp lo respeta
EXPOSE 8080

# La JVM usa hasta el 75 % de la memoria del contenedor (1 GiB en Cloud Run) y se reinicia si se queda sin memoria
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
