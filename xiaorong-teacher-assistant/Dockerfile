# Build the application inside Docker so image creation never depends on a
# pre-existing host-side target/*.jar artifact.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/eclipse-temurin:17-jre
WORKDIR /app
ENV JAVA_OPTS=""

COPY --from=build /workspace/target/xiaorong-teacher-assistant-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8088
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]