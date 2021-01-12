package misk.hibernate

import org.hibernate.mapping.Property
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class HibernateTest {
  @Test
  fun inheritedFieldTest() {
    val parentProperty = Property()
    parentProperty.name = "parentVal"
    val childProperty = Property()
    childProperty.name = "childVal"

    assertEquals("parentVal", field(Parent::class.java, parentProperty).name)
    assertEquals("parentVal", field(Child::class.java, parentProperty).name)
    assertEquals("childVal", field(Child::class.java, childProperty).name)

    assertThrows<IllegalStateException> {
      field(Parent::class.java, childProperty)
    }
  }

  open class Parent {
    lateinit var parentVal: String
  }

  class Child : Parent() {
    lateinit var childVal: String
  }
}
