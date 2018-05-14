FROM gcr.io/google-appengine/openjdk
COPY ./build/libs /usr/local/exemplar
WORKDIR /usr/local/exemplar
CMD ["java", "-jar", "/usr/local/exemplar/exemplar-0.2.3-SNAPSHOT.jar"]
