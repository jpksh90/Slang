import org.approvaltests.Approvals
import slang.parser.Parser
import slang.slast.SlastBuilder
import slang.slast.prettyPrint
import java.io.File
import java.net.URL
import kotlin.test.Test

class IRBuilderTests {

    fun loadTestProgram(name: String): URL? {
        return IRBuilderTests::class.java.getResource("/$name")
    }

    fun buildAst(testCase: URL): String {
        val file = File(testCase.toURI())
        val parser = Parser(file).compilationUnit
        if (parser != null) {
            val irBuilder = SlastBuilder(parser).compilationUnit
            return irBuilder.prettyPrint()
        }
        return ""
    }

    @Test
    fun testCase1() {
        val program = loadTestProgram("exponent.sl")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase2() {
        val program = loadTestProgram("facl.sl")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase3() {
        val program = loadTestProgram("gcd.sl")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase4() {
        val program = loadTestProgram("minmax.sl")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase5() {
        val program = loadTestProgram("disallowed.sl")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase6() {
        val program = loadTestProgram("sum_prod.sl")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase7() {
        val program = loadTestProgram("do-while.sl")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase8() {
        val program = loadTestProgram("misc.sl")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }
}