FROM gcr.io/google-appengine/openjdk
COPY ./build/libs /usr/local/chat
WORKDIR /usr/local/chat

RUN groupadd -g 999 appuser && \
    useradd -r -u 999 -g appuser appuser
USER 999

CMD ["java", "-jar", "/usr/local/chat/exemplarchat-0.2.5-SNAPSHOT.jar"]
