# =============================================================
#  Sanos & Salvos — Dockerfile micro-usuarios
#  Multi-stage build: Maven (build) → JRE 21 slim (runtime)
# =============================================================

# ── Stage 1: Build ────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copiar pom primero para aprovechar la caché de capas de Docker.
# Si el pom no cambia, Maven no re-descarga dependencias.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copiar el código fuente y compilar
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuario no-root por seguridad
RUN addgroup -S ssgroup && adduser -S ssuser -G ssgroup
USER ssuser

WORKDIR /app

# Copiar solo el JAR generado
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
