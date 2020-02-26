package com.squareup.misk.sqldelight.test

import com.google.common.truth.Truth.assertThat
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Provider

@MiskTest(startService = true)
class MySqlTest {
    @MiskTestModule
    val module = SqlDelightTestingModule()

    @Inject @SqlDelight lateinit var database: Provider<TestDatabase>

    private val dogQueries: DogQueries
        get() = database.get().dogQueries

    @Test fun simpleSelect() {
        dogQueries.insertDog("Tilda", "Pomeranian", true)

        assertThat(dogQueries.selectDogs().executeAsOne())
            .isEqualTo(Dog.Impl(
                name = "Tilda",
                breed = "Pomeranian",
                is_good = true
            ))
    }
}