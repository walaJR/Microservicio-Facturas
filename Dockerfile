# Etapa de construcción
FROM eclipse-temurin:21-jdk AS buildstage 

# Instalar Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copiar solo pom.xml primero para aprovechar cache de layers
COPY pom.xml .

# Descargar dependencias (se cachea si no cambia pom.xml)
RUN mvn dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Copiar wallet de Oracle Cloud
COPY Wallet_AZTBT2DD4GDVU52W ./Wallet_AZTBT2DD4GDVU52W

# Compilar aplicación
RUN mvn clean package -DskipTests

# Etapa de ejecución
FROM eclipse-temurin:21-jre-alpine

# Crear usuario no-root por seguridad
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Crear directorio EFS con permisos adecuados
RUN mkdir -p /app/efs && \
    chown appuser:appgroup /app/efs && \
    chmod 755 /app/efs

# Copiar el JAR desde la etapa de construcción
COPY --from=buildstage /app/target/*.jar app.jar

# Copiar wallet de Oracle Cloud desde la etapa de construcción
COPY --from=buildstage /app/Wallet_AZTBT2DD4GDVU52W ./Wallet_AZTBT2DD4GDVU52W

# Cambiar ownership del archivo
RUN chown appuser:appgroup app.jar

# Cambiar a usuario no-root
USER appuser

# Crear un volume point para EFS
VOLUME ["/app/efs"]

EXPOSE 8080

# Usar ENTRYPOINT con parámetros optimizados
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]