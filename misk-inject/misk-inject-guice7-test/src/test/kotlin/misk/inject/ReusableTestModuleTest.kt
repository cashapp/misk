package misk.inject

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ReusableTestModuleTest {

  class NoArgTestModuleOne: ReusableTestModule()
  class NoArgTestModuleTwo: ReusableTestModule()
  class OneArgTestModuleOne(private val installX: Boolean): ReusableTestModule()
  class OneArgTestModuleTwo(private val someValue: Int): ReusableTestModule()
  class TwoArgsTestModuleOne(private val installX: Boolean, installY: Boolean): ReusableTestModule()

  @Test
  fun testEquality() {
    assertEquals(NoArgTestModuleOne(), NoArgTestModuleOne())
    assertNotEquals(NoArgTestModuleOne(), NoArgTestModuleTwo())

    assertEquals(OneArgTestModuleOne(true), OneArgTestModuleOne(true))
    assertNotEquals(OneArgTestModuleOne(true), OneArgTestModuleOne(false))
    assertNotEquals(OneArgTestModuleTwo(1), OneArgTestModuleTwo(2))

    assertEquals(TwoArgsTestModuleOne(true, false), TwoArgsTestModuleOne(true, false))
    assertNotEquals(TwoArgsTestModuleOne(true, false), TwoArgsTestModuleOne(false, false))
  }

  @Test
  fun testHashcode() {
    assertEquals(NoArgTestModuleOne().hashCode(), NoArgTestModuleOne().hashCode())
    assertNotEquals(NoArgTestModuleOne().hashCode(), NoArgTestModuleTwo().hashCode())

    assertEquals(OneArgTestModuleOne(true).hashCode(), OneArgTestModuleOne(true).hashCode())
    assertNotEquals(OneArgTestModuleOne(true).hashCode(), OneArgTestModuleOne(false).hashCode())
    assertNotEquals(OneArgTestModuleTwo(1).hashCode(), OneArgTestModuleTwo(2).hashCode())

    assertEquals(TwoArgsTestModuleOne(true, false).hashCode(), TwoArgsTestModuleOne(true, false).hashCode())
    assertNotEquals(TwoArgsTestModuleOne(true, false).hashCode(), TwoArgsTestModuleOne(false, false).hashCode())
  }
}
