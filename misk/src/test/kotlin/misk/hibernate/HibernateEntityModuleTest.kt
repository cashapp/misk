package misk.hibernate

import com.google.inject.Guice
import misk.inject.getSetOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Qualifier

internal class HibernateEntityModuleTest {
  @Test fun multipleDataSources() {
    val injector = Guice.createInjector(
        object: HibernateEntityModule(Dinosaurs::class) {
          override fun configureHibernate() {
            addEntities(Triceratops::class, Stegosaurus::class)
          }
        },
        object: HibernateEntityModule(Shapes::class) {
          override fun configureHibernate() {
            addEntities(Square::class, Circle::class)
          }
        })

    assertThat(injector.getSetOf(HibernateEntity::class, Dinosaurs::class).unwrap())
        .containsExactly(Triceratops::class, Stegosaurus::class)
    assertThat(injector.getSetOf(HibernateEntity::class, Shapes::class).unwrap())
        .containsExactly(Square::class, Circle::class)
  }

  @Test fun multipleModulesSameDataSource() {
    val injector = Guice.createInjector(
        object: HibernateEntityModule(Dinosaurs::class) {
          override fun configureHibernate() {
            addEntities(Triceratops::class)
          }
        },
        object: HibernateEntityModule(Dinosaurs::class) {
          override fun configureHibernate() {
            addEntities(Stegosaurus::class)
          }
        },
        object: HibernateEntityModule(Shapes::class) {
          override fun configureHibernate() {
            addEntities(Square::class)
          }
        },
        object: HibernateEntityModule(Shapes::class) {
          override fun configureHibernate() {
            addEntities(Circle::class)
          }
        }
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

abstract class Square : DbEntity<Square>
abstract class Circle : DbEntity<Circle>

abstract class Triceratops : DbEntity<Triceratops>
abstract class Stegosaurus : DbEntity<Stegosaurus>
