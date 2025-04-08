package slang.parser

import org.junit.jupiter.api.Assertions.*
import java.io.File
import kotlin.test.Test

class ParserTest {
    @Test
    fun test1() {
        val file = "src/test/resources/disallowed.slang"
        val parser = Parser(File(file))
        assertFalse(parser.parse())
    }

    @Test
    fun test2() {
        val file = "src/test/resources/mandelbrot.slang"
        val parser = Parser(File(file))
        assertTrue(parser.parse())
    }
}