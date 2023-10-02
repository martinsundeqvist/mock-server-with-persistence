FROM openjdk:21

RUN mkdir -p /app
COPY ./target/mock-server-1.0-SNAPSHOT.jar /app/mock-server-1.0-SNAPSHOT.jar
WORKDIR /app

CMD ["java", "-cp", "mock-server-1.0-SNAPSHOT.jar", "com.example.MockServer"]
