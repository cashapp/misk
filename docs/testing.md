# Testing

Misk provides a few ways to streamline testing with JUnit.

Without Misk, you would instantiate units under test and their dependencies:

```kotlin
class FeatureWithoutMiskTest {
    @Test 
    fun `tests something`() {
        val repository = MyRepository()
        val handler = MyHandler(repository)

        // ... perform assertions
    }
}
```

## Using `@MiskTest`

`@MiskTest` will stand up a Misk app given a provided module and then inject members onto the test 
class. For instance, with a test that looks like this:

```kotlin
@MiskTest(startService = true)
class HelloWebIntegrationTest {
  @MiskTestModule val module = MyTestingModule()

  @Inject lateinit var myHandler: MyHandler()

  @Test
  fun `makes a call to the service`() {
      // use myHandler...
  }
}

class MyTestingModule : KAbstractModule() {
    override fun configure() {
        // add modules, declare bindings...
    }
}
```

- `@MiskTest` signals to JUnit to honour the other annotations.
- `@MiskTestModule` declares which module to use to perform the tests in this class. This module (or a sub-module under it) should have a binding for `MyHandler`, which gets injected in the test class.

### Next steps

Some common domains to test:

- [Testing actions](actions.md#testing)
