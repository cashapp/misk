# Multipart

Multipart Forms are a way to encode multiple files in a single request. This is commonly used when uploading files to a server.

Misk support this by leveraging the `okhttp3.MultipartReader` class. The following is an example of how to use it in a Misk WebAction:

```kotlin
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.actions.WebAction
import okhttp3.MultipartReader

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

For each element of the `part` (separated by comma) we want to log an info message. This can be accomplished by the following code:

```kotlin
  fun execute(
    @RequestBody multipartReader: MultipartReader
  ): String {
    val part = multipartReader.nextPart() ?: return "Empty part"
    val partContent = part.body.readUtf8().split(",")
    val idempotenceToken = part.headers["X-Idempotence-Token"]
  
    partContent.forEach {
      logger.info { "Processing row: $it with $idempotenceToken." }
    }
  
    return "Success"
  }
```

A `part` can also carry headers and this could come in handy to pass additional information like `X-Idempotence-Token` that could serve as an idempotence token so elements are just processed once. 

The following is an example of how to test the above code using curl:

```bash
  curl -v -F "upload=@localfilename;headers=\"X-Idempotence-Token:as456\" http://<BASE_URL>/internal/upload-file
```

Where `localfilename` is the location of a file with the content described above, and the `headers` attribute is used to pass the `X-Idempotence-Token` header.

You could upload multiple parts in the curl request, and in such cases you will have to iterate over the parts in the `MultipartReader` object:

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