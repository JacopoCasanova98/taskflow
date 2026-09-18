# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -Dmaven.test.skip=true dependency:go-offline

COPY src/main ./src/main
# The separate quality gate runs tests; this stage only packages production code.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -Dmaven.test.skip=true package \
    && set -- target/*.jar \
    && test "$#" -eq 1 \
    && cp "$1" /build/app.jar

FROM eclipse-temurin:21.0.12_8-jre-noble AS runtime
RUN groupadd --system --gid 10001 taskflow \
    && useradd --system --uid 10001 --gid taskflow --no-create-home \
        --shell /usr/sbin/nologin taskflow
WORKDIR /app
COPY --from=build --chmod=0444 /build/app.jar /app/app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
