FROM maven:3.8.5-openjdk-18 AS build

WORKDIR /root

COPY toolchains.xml /root/.m2/
COPY . .
RUN mvn package

FROM bitnami/java:17.0.3-debian-10-r4

ENV JAVA_JAR_FILE="reservation-api-1.0.0-SNAPSHOT.jar"

RUN useradd --create-home reservation-api
WORKDIR /home/reservation-api

COPY --from=build /root/target/${JAVA_JAR_FILE} ./
COPY --from=build /root/target/dependency/* ./

CMD ["sh", "-c", "java -jar ${JAVA_JAR_FILE}"]

