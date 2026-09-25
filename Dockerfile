# ---------- Etapa 1: compilar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
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
