package misk.testing

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.FUNCTION)
@Test
@ExtendWith(InjectingParameterResolver::class)
annotation class InjectTest
