package misk.hibernate

import com.google.inject.Guice
import misk.inject.getSetOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Qualifier

internal class HibernateEntityModuleTest {

  @Test fun multipleDataSources() {
    val injector = Guice.createInjector(
        HibernateEntityModule(Dinosaurs::class,
            setOf(Triceratops::class, Stegosaurus::class)),
        HibernateEntityModule(Shapes::class,
            setOf(Square::class, Circle::class))
    )

    assertThat(injector.getSetOf(HibernateEntity::class, Dinosaurs::class).unwrap())
        .containsExactly(Triceratops::class, Stegosaurus::class)
    assertThat(injector.getSetOf(HibernateEntity::class, Shapes::class).unwrap())
        .containsExactly(Square::class, Circle::class)
  }

  @Test fun multipleModulesSameDataSource() {
    val injector = Guice.createInjector(
        HibernateEntityModule(Dinosaurs::class, setOf(Triceratops::class)),
        HibernateEntityModule(Dinosaurs::class, setOf(Stegosaurus::class)),
        HibernateEntityModule(Shapes::class, setOf(Square::class)),
        HibernateEntityModule(Shapes::class, setOf(Circle::class))
    )

    assertThat(injector.getSetOf(HibernateEntity::class, Dinosaurs::class).unwrap())
        .containsExactly(Triceratops::class, Stegosaurus::class)
    assertThat(injector.getSetOf(HibernateEntity::class, Shapes::class).unwrap())
        .containsExactly(Square::class, Circle::class)
  }
}

private fun Set<HibernateEntity>.unwrap() = map { it.entity }

@Qualifier
annotation class Dinosaurs

@Qualifier
annotation class Shapes

class Square
class Circle

class Triceratops
class Stegosaurus
