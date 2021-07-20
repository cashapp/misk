# wisp-resource-loader-testing

Includes a way to fake filesystem resources loaded
via [wisp-resource-loader](https://github.com/cashapp/misk/tree/master/wisp-resource-loader).

## Usage

```kotlin
val loader = ResourceLoader(
  mapOf(
    "filesystem:" to FakeFilesystemLoaderBackend(
      mapOf(
        "/some/test/file" to "test data!"
      )
    )
  )
)

// This will load from the in-memory map rather than the filesystem.
val data = loader.utf8("filesystem:/some/test/file")
```
