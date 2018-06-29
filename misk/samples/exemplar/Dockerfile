FROM gcr.io/google-appengine/openjdk
COPY ./build/libs /usr/local/exemplar
WORKDIR /usr/local/exemplar

RUN groupadd -g 999 appuser && \
    useradd -r -u 999 -g appuser appuser
USER 999

CMD ["java", "-jar", "/usr/local/exemplar/exemplar-0.4.0-SNAPSHOT-all.jar"]
