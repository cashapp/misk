package misk.jdbc

import com.google.inject.Guice
import misk.inject.getSetOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Qualifier
import kotlin.reflect.KClass

internal class JPAEntityModuleTest {

  @Test fun multipleDataSources() {
    val dinosaurs = setOf(Triceratops::class, Stegosaurus::class)
    val shapes = setOf(Square::class, Circle::class)

    val injector = Guice.createInjector(
      JPAEntityModule.create(dinosaurs),
      JPAEntityModule.create(annotatedBy = Shapes::class, entities = shapes)
    )

    assertEntities(injector.getSetOf(JPAEntity::class), dinosaurs)
    assertEntities(injector.getSetOf(JPAEntity::class, Shapes::class), shapes)
  }

  @Test fun multipleModulesSameDataSource() {
    val injector = Guice.createInjector(
      JPAEntityModule.create(setOf(Triceratops::class)),
      JPAEntityModule.create(setOf(Stegosaurus::class)),
      JPAEntityModule.create(annotatedBy = Shapes::class, entities = setOf(Square::class)),
      JPAEntityModule.create(annotatedBy = Shapes::class, entities = setOf(Circle::class))
    )

    val dinosaurs = setOf(Triceratops::class, Stegosaurus::class)
    val shapes = setOf(Square::class, Circle::class)

    assertEntities(injector.getSetOf(JPAEntity::class), dinosaurs)
    assertEntities(injector.getSetOf(JPAEntity::class, Shapes::class), shapes)
  }

  fun assertEntities(actual: Set<JPAEntity>, expected: Set<KClass<*>>) =
    assertThat(actual.map { it.entity }).containsExactlyElementsOf(expected.map { it.java })
}

@Qualifier
annotation class Shapes

class Square
class Circle

class Triceratops
class Stegosaurus
