package misk.policy.opa

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.inject.Inject

@MiskTest(startService = false)
internal class FakeOpaPolicyEngineTest {
  @MiskTestModule val module = FakeOpaModule()

  @Inject lateinit var fakeOpaPolicyEngine: FakeOpaPolicyEngine
  @Inject lateinit var opaPolicyEngine: OpaPolicyEngine

  @Test
  fun `Throws if override missing`() {
    assertThrows<IllegalStateException> { opaPolicyEngine.evaluate<TestResponse>("test") }
  }

  @Test
  fun `Throws if override missing with input`() {
    assertThrows<IllegalStateException> {
      opaPolicyEngine.evaluate<TestRequest, TestResponse>(
        "test",
        TestRequest("moarTests")
      )
    }
  }

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

  @Test
  fun `Override document for multiple input`() {
    fakeOpaPolicyEngine.addOverrideForInput("test", TestRequest("key"), TestResponse("value"))
    fakeOpaPolicyEngine.addOverrideForInput("test", TestRequest("otherKey"), TestResponse("otherValue"))
    opaPolicyEngine.evaluate<TestRequest, TestResponse>("test", TestRequest("key")).apply {
      assertThat(this).isEqualTo(TestResponse("value"))
    }
    opaPolicyEngine.evaluate<TestRequest, TestResponse>("test", TestRequest("otherKey")).apply {
      assertThat(this).isEqualTo(TestResponse("otherValue"))
    }
  }

  @Test
  fun `Override document for the same input`() {
    fakeOpaPolicyEngine.addOverrideForInput("test", TestRequest("key"), TestResponse("value"))
    fakeOpaPolicyEngine.addOverrideForInput("test", TestRequest("key"), TestResponse("otherValue"))
    opaPolicyEngine.evaluate<TestRequest, TestResponse>("test", TestRequest("key")).apply {
      assertThat(this).isEqualTo(TestResponse("otherValue"))
    }
  }


  data class TestRequest(
    val something: String
  ) : OpaRequest()

  data class TestResponse(
    val something: String
  ) : OpaResponse()
}
