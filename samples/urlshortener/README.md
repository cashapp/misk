# urlshortener

This sample service demonstrates Misk web actions and SQL persistence.

## Build

```
  $ ./gradlew assemble -p samples/urlshortener
```

## Run the Service

```
  $ java -jar samples/urlshortener/build/libs/urlshortener-0.4.0-SNAPSHOT-all.jar
```

## Use the URL shortener

Create a URL

```
curl 'http://localhost:8080/create' \
  -X POST \
  -H 'content-type: application/json; charset=utf-8' \
  --data '{"long_url": "https://github.com/square/misk/pull/216"}'
```
