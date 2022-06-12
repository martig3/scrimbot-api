#build stage
FROM openjdk:alpine AS builder
WORKDIR .
COPY . .
RUN ./gradlew build install

#final stage
FROM alpine:latest
RUN apk update
RUN apk --no-cache add ca-certificates
RUN apk add --update openjdk11 tzdata curl unzip bash
RUN rm -rf /var/cache/apk/*
COPY --from=builder build/install/* /app
ENTRYPOINT /app/bin/scrimbot-api
LABEL Name=martig3/scrimbot-api Version=0.6.0
EXPOSE 8080
