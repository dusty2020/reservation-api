# reservation-api

## Running locally
### Docker
Build the Docker image
> docker build -t reservation-api

Run
> docker run -d -p 8080:8080 reservation-api

### Maven
Build
> mvn package

Run
> java -jar /target/reservation-api-1.0.0-SNAPSHOT