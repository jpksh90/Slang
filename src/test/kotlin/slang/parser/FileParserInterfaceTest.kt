package slang.parser

import org.junit.jupiter.api.Assertions.*
import java.io.File
import kotlin.test.Test

class FileParserInterfaceTest {
    @Test
    fun test1() {
        val file = "src/test/resources/disallowed.slang"
        val parser = FileParserInterface(File(file))
        assertFalse(parser.parse())
    }

    @Test
    fun test2() {
        val file = "src/test/resources/mandelbrot.slang"
        val parser = FileParserInterface(File(file))
        assertTrue(parser.parse())
    }
}