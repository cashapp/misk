FROM gcr.io/google-appengine/openjdk
COPY ./build/libs /usr/local/chat
WORKDIR /usr/local/chat
CMD ["java", "-jar", "/usr/local/chat/exemplarchat-0.1.1-SNAPSHOT.jar"]
