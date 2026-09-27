# ---------- Etapa 1: compilar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Limita la memoria de Gradle para equipos con poca RAM asignada a Docker
ENV GRADLE_OPTS="-Xmx256m" JAVA_TOOL_OPTIONS="-XX:+UseSerialGC"
RUN mkdir -p /root/.gradle && echo "org.gradle.jvmargs=-Xmx768m -XX:+UseSerialGC" > /root/.gradle/gradle.properties
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Descarga Gradle y las dependencias; reintenta si la conexion es lenta
RUN chmod +x gradlew && for i in 1 2 3; do ./gradlew --no-daemon dependencies > /dev/null && break; \
      echo "Descarga fallida (intento $i), reintentando..."; sleep 10; done \
    && ./gradlew --no-daemon --offline dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---------- Etapa 2: ejecutar ----------
FROM eclipse-temurin:21-jre
ENV TZ=America/Lima
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /app/data/img /app/data/evidencias && chown -R app:app /app
COPY --from=build /app/build/libs/Control-NGR.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=America/Lima", "-jar", "/app/app.jar"]
