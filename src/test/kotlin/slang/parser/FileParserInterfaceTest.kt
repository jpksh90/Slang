package slang.parser

import slang.hlir.file2hlir
import java.io.File
import kotlin.test.Test

class FileParserInterfaceTest {
    @Test
    fun test1() {
        val file = "src/test/resources/disallowed.slang"
        val parseTree = file2hlir(File(file))
        assert(parseTree.isErr)
    }

    @Test
    fun test2() {
        val file = "src/test/resources/mandelbrot.slang"
        val parseTree = file2hlir(File(file))
        assert(parseTree.isOk)
    }
}