package wisp.containers

import com.github.dockerjava.api.exception.NotFoundException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ContainersTest {

    @Test
    fun `create a container successfully`() {
        val container = Container {
            withImage("alpine")
            withName("alpine")
        }

        val composer = Composer("alpine", container)
        composer.start()
        assertTrue(composer.running.get())
        composer.stop()
        assertFalse(composer.running.get())
    }

    @Test
    fun `attempting to create an unknown container should throw NotFoundException`() {
        assertThrows<NotFoundException> {
            val myBadContainer = Container {
                withImage("unknown_image/really_bad_image_name")
                withName("bad_image")
            }

            val composer = Composer("bad_stuff", myBadContainer)
            composer.start()
        }
    }
}
