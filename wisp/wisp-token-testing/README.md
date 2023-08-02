# wisp-token-testing

This module contains code to do token generation for testing.

For details on the generation used, see
[TokenGenerator](https://github.com/cashapp/wisp/blob/main/wisp-token/src/main/kotlin/wisp/token/TokenGenerator.kt)

## Usage

```kotlin
val tokenGenerator = FakeTokenGenerator() 
val label = "some label"
val length = 7 // 4 to 25
val token = tokenGenerator.generate(label, length)
```
