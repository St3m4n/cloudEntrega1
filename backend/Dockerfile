FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/ ./backend/
ARG SERVICE
RUN mvn -B -ntp -f backend/pom.xml -pl ${SERVICE} -am package -DskipTests
RUN cp backend/${SERVICE}/target/${SERVICE}-1.0.0.jar /app.jar
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/data && chown -R 10001:10001 /app
COPY --from=build /app.jar /app/app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java","-jar","/app/app.jar"]
