import org.approvaltests.Approvals
import slang.common.invoke
import slang.common.then
import slang.hlir.ParseTree2HlirTrasnformer
import slang.hlir.prettyPrint
import slang.parser.File2ParseTreeTransformer
import java.io.File
import java.net.URL
import kotlin.test.Test

class IRBuilderTests {
    fun loadTestProgram(name: String): URL? = IRBuilderTests::class.java.getResource("/$name")

    fun buildAst(testCase: URL): String {
        val hlir = (File2ParseTreeTransformer() then ParseTree2HlirTrasnformer()).invoke(File(testCase.toURI()))
        return hlir.fold(
            { it.prettyPrint(0) },
            { "" },
        )
    }

    @Test
    fun testCase1() {
        val program = loadTestProgram("exponent.slang")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase2() {
        val program = loadTestProgram("facl.slang")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase3() {
        val program = loadTestProgram("gcd.slang")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase4() {
        val program = loadTestProgram("minmax.slang")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase5() {
        val program = loadTestProgram("disallowed.slangang")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase6() {
        val program = loadTestProgram("sum_prod.slang")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase7() {
        val program = loadTestProgram("do-while.slang")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase8() {
        val program = loadTestProgram("misc.slang")
        if (program != null) {
            val ast = buildAst(program)
            Approvals.verify(ast)
        }
    }
}
