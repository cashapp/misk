# Multipart

Multipart is a way to send multiple files in a single request. This is commonly used when uploading files to a server.

Misk support this by leveraging the `okhttp3.MultipartReader` class. The following is an example of how to use it in a Misk WebAction:

```kotlin
class UploadFileAction @Inject constructor() : WebAction {
  @Post("/internal/upload-file")
  @RequestContentType("multipart/*")
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  fun upload(
    @RequestBody multipartReader: MultipartReader
  ): String { 
    // use multipartReader to read the parts of the request
  }
}
```

To illustrate how to use the `MultipartReader` class, let's consider an example where you want to upload the following file:

```
19119888,127009194,436445396,143573243,307290287
```

Each time we upload the file, we would like to add an `idempotence-token`. Also, for each line we want to log an info message.

```kotlin
  fun execute(
    @RequestBody multipartReader: MultipartReader
  ): String {
    val part = multipartReader.nextPart() ?: return "Empty part"
    val fileContent = part.body.readUtf8().split(",")
    val idempotenceToken = part.headers["X-Idempotence-Token"]
  
    fileContent.forEach {
      logger.info { "Processing row: $it with $idempotenceToken." }
    }
  
    return "Success"
  }
```
The following is an example of how to test the above code using curl:

```bash
  curl -v -F "upload=@localfilename;headers=\"X-Idempotence-Token:as456\" http://<BASE_URL>/internal/upload-file
```

Where `localfilename` is the location of a file with the content described above, and the `headers` attribute is used to pass the `X-Idempotence-Token` header.

You could upload multiple files in the curl request, and it such case you will have to iterate over the parts in the `MultipartReader` object:

```kotlin
  fun execute(
    @RequestBody multipartReader: MultipartReader
  ): String {
    var part = multipartReader.nextPart()
    while (part != null) {
      ...
      part = multipartReader.nextPart()
    }
    return "Success"
  }
```