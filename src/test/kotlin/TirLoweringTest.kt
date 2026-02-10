import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import slang.hlir.string2hlir
import slang.tir.Hlir2TirTransform
import slang.tir.TirExpr
import slang.tir.TirLowering
import slang.tir.TirProgramUnit
import slang.tir.TirStmt
import slang.tir.prettyPrint
import slang.typeinfer.SlangType
import slang.typeinfer.prune

class TirLoweringTest {
    private fun lowerOrFail(code: String): TirProgramUnit {
        val hlir =
            string2hlir(code).fold(
                onSuccess = { it },
                onFailure = { throw AssertionError("Parse error: $it") },
            )
        val lowering = TirLowering()
        val tir = lowering.lowerProgram(hlir)
        assertTrue(
            lowering.errors.isEmpty(),
            "Expected no type errors but got:\n${lowering.errors.joinToString("\n") { it.message ?: "" }}",
        )
        return tir
    }

    private fun lowerExpectingErrors(code: String): Pair<TirProgramUnit, List<slang.typeinfer.TypeError>> {
        val hlir =
            string2hlir(code).fold(
                onSuccess = { it },
                onFailure = { throw AssertionError("Parse error: $it") },
            )
        val lowering = TirLowering()
        val tir = lowering.lowerProgram(hlir)
        return Pair(tir, lowering.errors)
    }

    /** Helper: find statements from the __module__main__ function. */
    private fun mainStmts(tir: TirProgramUnit): List<TirStmt> {
        val mainFn = tir.modules[0].functions.find { it.name == "__module__main__" }
        assertNotNull(mainFn, "Expected __module__main__ function")
        return mainFn!!.body.stmts
    }

    // ---- Basic typing ----

    @Test
    fun testNumberLiteralType() {
        val tir = lowerOrFail("let x = 42;")
        val letStmt = mainStmts(tir)[0] as TirStmt.LetStmt
        assertEquals(SlangType.TNum, prune(letStmt.expr.type))
    }

    @Test
    fun testBoolLiteralType() {
        val tir = lowerOrFail("let x = true;")
        val letStmt = mainStmts(tir)[0] as TirStmt.LetStmt
        assertEquals(SlangType.TBool, prune(letStmt.expr.type))
    }

    @Test
    fun testStringLiteralType() {
        val tir = lowerOrFail("""let x = "hello";""")
        val letStmt = mainStmts(tir)[0] as TirStmt.LetStmt
        assertEquals(SlangType.TString, prune(letStmt.expr.type))
    }

    @Test
    fun testArithmeticType() {
        val tir = lowerOrFail("let x = 1 + 2;")
        val letStmt = mainStmts(tir)[0] as TirStmt.LetStmt
        val binExpr = letStmt.expr as TirExpr.BinaryExpr
        assertEquals(SlangType.TNum, prune(binExpr.type))
    }

    @Test
    fun testComparisonType() {
        val tir = lowerOrFail("let x = 1 < 2;")
        val letStmt = mainStmts(tir)[0] as TirStmt.LetStmt
        val binExpr = letStmt.expr as TirExpr.BinaryExpr
        assertEquals(SlangType.TBool, prune(binExpr.type))
    }

    // ---- Functions ----

    @Test
    fun testFunctionReturnType() {
        val tir =
            lowerOrFail(
                """
                fun double(x) => x * 2;
                let y = double(5);
                """.trimIndent(),
            )
        val doubleFn = tir.modules[0].functions.find { it.name == "double" }!!
        val fnType = prune(doubleFn.type) as SlangType.TFun
        assertEquals(SlangType.TNum, prune(fnType.ret))
        assertEquals(1, fnType.params.size)
        assertEquals(SlangType.TNum, prune(fnType.params[0]))
    }

    @Test
    fun testFunctionCallType() {
        val tir =
            lowerOrFail(
                """
                fun double(x) => x * 2;
                let y = double(5);
                """.trimIndent(),
            )
        val stmts = mainStmts(tir)
        val letStmt = stmts[0] as TirStmt.LetStmt
        val call = letStmt.expr as TirExpr.NamedFunctionCall
        assertEquals(SlangType.TNum, prune(call.type))
    }

    @Test
    fun testHigherOrderFunction() {
        val tir =
            lowerOrFail(
                """
                fun apply(f, x) => f(x);
                fun inc(n) => n + 1;
                let result = apply(inc, 5);
                """.trimIndent(),
            )
        val stmts = mainStmts(tir)
        val letStmt = stmts[0] as TirStmt.LetStmt
        assertEquals(SlangType.TNum, prune(letStmt.expr.type))
    }

    // ---- Lambda / Inlined function ----

    @Test
    fun testLambdaType() {
        val tir =
            lowerOrFail(
                """
                let add = fun(a, b) => a + b;
                let r = add(1, 2);
                """.trimIndent(),
            )
        val stmts = mainStmts(tir)
        val addLet = stmts[0] as TirStmt.LetStmt
        val funType = prune(addLet.expr.type) as SlangType.TFun
        assertEquals(2, funType.params.size)
        // The call site resolves to Num
        val rLet = stmts[1] as TirStmt.LetStmt
        assertEquals(SlangType.TNum, prune(rLet.expr.type))
    }

    // ---- If expression ----

    @Test
    fun testIfExprType() {
        val tir = lowerOrFail("let x = if (true) then 1 else 2;")
        val stmts = mainStmts(tir)
        val letStmt = stmts[0] as TirStmt.LetStmt
        assertEquals(SlangType.TNum, prune(letStmt.expr.type))
    }

    // ---- Typed params in functions ----

    @Test
    fun testFunctionParamsTyped() {
        val tir =
            lowerOrFail(
                """
                fun add(a, b) => a * b;
                let r = add(1, 2);
                """.trimIndent(),
            )
        val addFn = tir.modules[0].functions.find { it.name == "add" }!!
        assertEquals(2, addFn.params.size)
        // `*` constrains params to Num (unlike `+` which is polymorphic)
        assertEquals(SlangType.TNum, prune(addFn.params[0].second))
        assertEquals(SlangType.TNum, prune(addFn.params[1].second))
    }

    // ---- Pretty print contains types ----

    @Test
    fun testPrettyPrintContainsTypes() {
        val tir = lowerOrFail("let x = 42;")
        val output = tir.prettyPrint()
        assertTrue(output.contains("Num"), "Expected 'Num' type in pretty print output, got:\n$output")
    }

    // ---- Error propagation ----

    @Test
    fun testTypeErrorStillProducesTir() {
        val (tir, errors) =
            lowerExpectingErrors(
                """
                let x = true;
                let y = x * 2;
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Expected type errors")
        assertNotNull(tir, "TIR should still be produced even with errors")
    }

    // ---- Transform pipeline ----

    @Test
    fun testHlir2TirTransformSuccess() {
        val hlir =
            string2hlir("let x = 42;").fold(
                onSuccess = { it },
                onFailure = { throw AssertionError("Parse error: $it") },
            )
        val result = Hlir2TirTransform().transform(hlir)
        assertTrue(result.isOk, "Expected Ok result from transform")
    }

    @Test
    fun testHlir2TirTransformError() {
        val hlir =
            string2hlir(
                """
                let x = true;
                let y = x * 2;
                """.trimIndent(),
            ).fold(
                onSuccess = { it },
                onFailure = { throw AssertionError("Parse error: $it") },
            )
        val result = Hlir2TirTransform().transform(hlir)
        assertTrue(result.isErr, "Expected Err result from transform")
    }
}
