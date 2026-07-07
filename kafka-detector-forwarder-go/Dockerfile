FROM golang:1.23-alpine AS build

WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN go build -buildvcs=false -o /out/kafka-detector-forwarder .

FROM alpine:3.20

RUN adduser -D -H -u 10001 forwarder
USER forwarder

COPY --from=build /out/kafka-detector-forwarder /usr/local/bin/kafka-detector-forwarder
ENTRYPOINT ["kafka-detector-forwarder"]
