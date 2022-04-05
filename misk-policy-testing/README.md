# misk-policy-testing

This module provides a simple abstraction for mocking policy engine requests in unit tests.
Additionally, this module allows developers to run [Open Policy Agent (OPA)](https://www.openpolicyagent.org/docs/latest/) instances locally during development, fully integrated into the container lifecycle.
This feature uses docker to launch the latest OPA image, mounting a local policy directory and configuring the service to use the local instance for queries.

## Usage

For mocking functionality in unit tests, use `FakeOpaModule`:

```kotlin
install(FakeOpaModule())
```

For an improved local development experience, use the `OpaDevelopmentModule`.
This module is mutually exclusive with `OpaModule` in the misk-policy package.

```kotlin
install(OpaDevelopmentModule())
//or
install(OpaDevelopmentModule(policyDirectory = "some path", withLogging = true))
```

The default policy directory is `service/src/policy` and the recommended location to keep policies accessible for local testing.

## Unit testing

The testing framework make the `FakeOpaPolicyEngine` interface available as an implementation of `OpaPolicyEngine`.
The fake functions more like a mock and similar to misk-feature-testing.
Developers should use the fake interface, to add specific return values for given document names and inputs.

```kotlin
@MiskTest(startService = false)
internal class FakeOpaPolicyEngineTest {
  @MiskTestModule val module = FakeOpaModule()

  @Inject lateinit var fakeOpaPolicyEngine: FakeOpaPolicyEngine
  @Inject lateinit var opaPolicyEngine: OpaPolicyEngine

  @Test
  fun `Override document without input`() {
    fakeOpaPolicyEngine.addOverride("test", TestResponse("value"))
    val evaluate = opaPolicyEngine.evaluate<TestResponse>("test")
    assertThat(evaluate).isEqualTo(TestResponse("value"))
  }

  @Test
  fun `Override document with input`() {
    fakeOpaPolicyEngine.addOverrideForInput("test", TestRequest("key"), TestResponse("value"))
    val evaluate = opaPolicyEngine.evaluate<TestRequest, TestResponse>("test", TestRequest("key"))
    assertThat(evaluate).isEqualTo(TestResponse("value"))
  }

  data class TestRequest(
    val something: String
  ) : OpaRequest

  data class TestResponse(
    val something: String
  ) : OpaResponse
```

## Local development

It is possible spin up a docker image of OPA, with all the local policy loaded as part of misk's lifecycle.
This allows developers to run an actual instance of the policy engine, providing end-to-end testing capability during development.
To use this feature, setup `OpaDevelopmentModule`.
That's it!

```kotlin
install(OpaDevelopmentModule())
//or
install(OpaDevelopmentModule(policyDirectory = "some path", withLogging = true))
```

Once installed, misk will automatically instantiate a docker container, mounting the policyDirectory (default: `service/src/policy`) into it and running `opa run -s -b -w /policies` , where the policies directory is the mapped directory.
while the service is running, OPA is queryable on port 8181 locally.
See `LocalOpaService.kt` for more information and ways to change this behavior.
