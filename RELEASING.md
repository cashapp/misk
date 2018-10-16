Releasing
-------------

The upload isn't idempotent, so if you're uploading multiple artifacts and it fails partway,
you'll need to invoke the uploadArchives command for each remaining artifact.

Release the base `misk/misk` subproject with command below from the main `cash/misk` directory.

  ```
  $ ./gradlew misk:uploadArchives -Pinternal

  ```

Release a specific misk subproject with `./gradlew { misk-subproject }:uploadArchives -Pinternal`. Example for `misk-aws` below.

  ```
  $ ./gradlew misk-aws:uploadArchives -Pinternal

  ```

Release all misk subprojects with command below from the main `cash/misk` directory.

  ```
  $ ./gradlew uploadArchives -Pinternal
  ```

If base `misk/misk` subproject or other artifacts have already been published, any of the above commands will fail. You will then need to manually `uploadArchives` for all subprojects.
Note: the below command may not be up to date with all of the current Misk subprojects.

  ```
  $ ./gradlew misk:uploadArchives -Pinternal && \
      ./gradlew misk-aws:uploadArchives -Pinternal && \
      ./gradlew misk-eventrouter:uploadArchives -Pinternal && \
      ./gradlew misk-events:uploadArchives -Pinternal && \
      ./gradlew misk-gcp:uploadArchives -Pinternal && \
      ./gradlew misk-gcp-testing:uploadArchives -Pinternal && \
      ./gradlew misk-grpc-tests:uploadArchives -Pinternal && \
      ./gradlew misk-hibernate:uploadArchives -Pinternal && \
      ./gradlew misk-hibernate-testing:uploadArchives -Pinternal && \
      ./gradlew misk-jaeger:uploadArchives -Pinternal && \
      ./gradlew misk-prometheus:uploadArchives -Pinternal && \
      ./gradlew misk-testing:uploadArchives -Pinternal && \
      ./gradlew misk-zipkin:uploadArchives -Pinternal
  ```
