package cash.detektive.javacompat

class TestCode(val things: List<String> = emptyList()) {
  fun doIt(x: String = "", y: Int) {}
}
