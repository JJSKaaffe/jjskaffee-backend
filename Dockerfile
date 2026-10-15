# =====================================================================
# Imagen del backend modular de JJSKaffee.
# Dos etapas: la primera compila con Maven, la segunda solo lleva el JRE y el jar,
# asi la imagen final no carga el compilador ni el codigo fuente (menos superficie
# de ataque y menos hallazgos de Trivy).
#
# El TLS lo termina el proveedor (Cloud Run / Nginx / el balanceador): aqui se
# escucha HTTP plano en $PORT, que es lo que esperan esas plataformas.
# =====================================================================

# ---------- Etapa 1: compilar ----------
FROM maven:3.9-eclipse-temurin-27 AS compilacion
WORKDIR /obra

# Primero solo el pom: si no cambian las dependencias, Docker reutiliza esta capa
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Ahora el codigo. Las pruebas ya corrieron en GitHub Actions (job "pruebas"),
# y las de integracion necesitan PostgreSQL, que aqui no existe.
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Etapa 2: ejecutar ----------
FROM eclipse-temurin:27-jre-alpine
WORKDIR /aplicacion

# Usuario sin privilegios: si alguien se mete al contenedor, no es root
RUN addgroup -S jjskaffee && adduser -S jjskaffee -G jjskaffee
USER jjskaffee

COPY --from=compilacion /obra/target/*.jar aplicacion.jar

# Fechas en UTC-0 tambien dentro del contenedor (ADR del equipo)
ENV TZ=UTC
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"

# Documentativo: el puerto real lo define la variable PORT en tiempo de ejecucion
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "aplicacion.jar"]
