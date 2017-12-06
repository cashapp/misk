# Exemplar 
This is a sample app that makes use of MISK.

## Building
Build exemplar:

```
  $ ../gradlew clean jar
```  

## Docker
Run exemplar in Docker:

```
  $ docker build -t exemplar-0.0.1 .
  $ docker run -p 8080:8080 exemplar-0.0.1
```

Visit [Docker for Mac](https://docs.docker.com/docker-for-mac/install/) to install Docker on a Mac for testing.


