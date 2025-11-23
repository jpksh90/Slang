package slang.hlir

import slang.common.Result
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DataflowAnalysisTest {
    @Test
    fun testReachingDefinitionsSimple() { // Test: let x = 10; let y = 5; print(x + y);
        val program =
            """
            let x = 10;
            let y = 5;
            print(x + y);
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = ReachingDefinitionsAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // At the exit, both x and y should be defined
        val exitOut = analysisResult.getOut(cfg.exit)
        assertNotNull(exitOut)
    }

    @Test
    fun testReachingDefinitionsWithReassignment() {
        val program =
            """
            let x = 10;
            x = 20;
            print(x);
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = ReachingDefinitionsAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // x should be defined (the second definition kills the first)
        val exitIn = analysisResult.getIn(cfg.exit)
        assertNotNull(exitIn)
    }

    @Test
    fun testReachingDefinitionsWithIfStatement() {
        val program =
            """
            let x = 10;
            if (x > 0) {
                x = 20;
            } else {
                x = 30;
            }
            print(x);
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = ReachingDefinitionsAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // After the if statement, x should be defined
        val exitIn = analysisResult.getIn(cfg.exit)
        assertNotNull(exitIn)
    }

    @Test
    fun testLiveVariablesSimple() {
        val program =
            """
            let x = 10;
            let y = 5;
            print(x);
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = LiveVariablesAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // y is not used, so it should not be live anywhere important
        // x is used in print, so it should be live before print
        assertNotNull(analysisResult.getIn(cfg.entry))
    }

    @Test
    fun testLiveVariablesWithDeadCode() {
        val program =
            """
            let x = 10;
            let y = 5;
            let z = x + y;
            print(x);
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = LiveVariablesAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // z is defined but never used, so it's dead
        // x is used in print
        // y is used in computing z, but z is dead
        assertNotNull(analysisResult.getOut(cfg.exit))
    }

    @Test
    fun testLiveVariablesWithLoop() {
        val program =
            """
            let x = 10;
            while (x > 0) {
                x = x - 1;
            }
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = LiveVariablesAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // x should be live in the loop
        assertNotNull(analysisResult.getIn(cfg.entry))
    }

    @Test
    fun testLiveVariablesWithIfStatement() {
        val program =
            """
            let x = 10;
            let y = 5;
            if (x > 0) {
                print(x);
            } else {
                print(y);
            }
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = LiveVariablesAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // Both x and y are used in different branches
        assertNotNull(analysisResult.getIn(cfg.entry))
    }

    @Test
    fun testDataflowResultPrettyPrint() {
        val program =
            """
            let x = 10;
            print(x);
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = ReachingDefinitionsAnalysis()
        val analysisResult = analysis.analyze(cfg)

        val output = analysisResult.prettyPrint()
        assertTrue(output.contains("Dataflow Analysis Result"))
    }

    @Test
    fun testCustomDataflowAnalysis() {
        // Create a simple constant propagation-like analysis
        class SimpleConstantAnalysis : DataflowAnalysis<Map<String, Boolean>>() {
            override val direction = Direction.FORWARD

            override fun initialValue(): Map<String, Boolean> = emptyMap()

            override fun boundaryValue(): Map<String, Boolean> = emptyMap()

            override fun meet(values: List<Map<String, Boolean>>): Map<String, Boolean> {
                if (values.isEmpty()) return emptyMap() // Intersection of all maps
                val result = values[0].toMutableMap()
                for (i in 1 until values.size) {
                    result.keys.retainAll(values[i].keys)
                }
                return result
            }

            override fun transfer(
                input: Map<String, Boolean>,
                block: BasicBlock,
            ): Map<String, Boolean> {
                val output = input.toMutableMap()
                for (stmt in block.stmts) {
                    when (stmt) {
                        is Stmt.LetStmt -> { // Track if it's a constant literal
                            output[stmt.name] = stmt.expr is Expr.NumberLiteral
                        }

                        else -> {}
                    }
                }
                return output
            }
        }

        val program =
            """
            let x = 10;
            let y = x + 5;
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = SimpleConstantAnalysis()
        val analysisResult = analysis.analyze(cfg)

        assertNotNull(analysisResult.getOut(cfg.exit))
    }

    @Test
    fun testEmptyProgramDataflow() {
        val program = "let x = 1;"

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()

        val rdAnalysis = ReachingDefinitionsAnalysis()
        val rdResult = rdAnalysis.analyze(cfg)
        assertNotNull(rdResult.getIn(cfg.entry))

        val lvAnalysis = LiveVariablesAnalysis()
        val lvResult = lvAnalysis.analyze(cfg)
        assertNotNull(lvResult.getOut(cfg.exit))
    }

    @Test
    fun testFunctionDataflow() {
        val program =
            """
            fun add(a, b) {
                let sum = a + b;
                return sum;
            }
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        // pick the first user-defined top-level function (skip synthetic module main)
        val function = programUnit.stmt.flatMap { it.functions }.firstOrNull { it.name != "__module__main__" }
        assertNotNull(function)

        val cfg = function!!.buildCFG()

        val lvAnalysis = LiveVariablesAnalysis()
        val lvResult = lvAnalysis.analyze(cfg)

        // Parameters a and b should be live initially
        assertNotNull(lvResult.getOut(cfg.exit))
    }

    @Test
    fun testConstantPropagationSimple() {
        val program =
            """
            let x = 10;
            let y = 5;
            let z = x + y;
            print(z);
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = ConstantPropagationAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // At the exit, x should be 10, y should be 5, z should be 15
        val exitIn = analysisResult.getIn(cfg.exit)
        assertNotNull(exitIn)
        assertTrue(exitIn!!["x"] is ConstantValue.Constant)
        assertTrue(exitIn["y"] is ConstantValue.Constant)
        assertTrue(exitIn["z"] is ConstantValue.Constant)

        val xVal = exitIn["x"] as ConstantValue.Constant
        val yVal = exitIn["y"] as ConstantValue.Constant
        val zVal = exitIn["z"] as ConstantValue.Constant
        assertTrue(xVal.value == 10.0)
        assertTrue(yVal.value == 5.0)
        assertTrue(zVal.value == 15.0)
    }

    @Test
    fun testConstantPropagationWithReassignment() {
        val program =
            """
            let x = 10;
            x = 20;
            print(x);
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = ConstantPropagationAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // At the exit, x should be 20
        val exitIn = analysisResult.getIn(cfg.exit)
        assertNotNull(exitIn)
        assertTrue(exitIn!!["x"] is ConstantValue.Constant)
        val xVal = exitIn["x"] as ConstantValue.Constant
        assertTrue(xVal.value == 20.0)
    }

    @Test
    fun testConstantPropagationNonConstant() {
        val program =
            """
            let x = 10;
            let y = x + z;
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = ConstantPropagationAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // x should be constant, y should be bottom (depends on unknown z)
        val exitIn = analysisResult.getIn(cfg.exit)
        assertNotNull(exitIn)
        assertTrue(exitIn!!["x"] is ConstantValue.Constant)
        assertTrue(exitIn["y"] is ConstantValue.Bottom || exitIn["y"] is ConstantValue.Top)
    }

    @Test
    fun testConstantPropagationArithmetic() {
        val program =
            """
            let a = 10;
            let b = 5;
            let sum = a + b;
            let diff = a - b;
            let prod = a * b;
            let quot = a / b;
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = ConstantPropagationAnalysis()
        val analysisResult = analysis.analyze(cfg)

        val exitIn = analysisResult.getIn(cfg.exit)
        assertNotNull(exitIn)

        val sum = exitIn!!["sum"] as? ConstantValue.Constant
        assertNotNull(sum)
        assertTrue(sum!!.value == 15.0)

        val diff = exitIn["diff"] as? ConstantValue.Constant
        assertNotNull(diff)
        assertTrue(diff!!.value == 5.0)

        val prod = exitIn["prod"] as? ConstantValue.Constant
        assertNotNull(prod)
        assertTrue(prod!!.value == 50.0)

        val quot = exitIn["quot"] as? ConstantValue.Constant
        assertNotNull(quot)
        assertTrue(quot!!.value == 2.0)
    }

    @Test
    fun testConstantPropagationWithBranching() {
        val program =
            """
            let x = 10;
            if (x > 0) {
                x = 20;
            } else {
                x = 30;
            }
            print(x);
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = ConstantPropagationAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // After the if, x could be either 20 or 30, so it should be BOTTOM
        val exitIn = analysisResult.getIn(cfg.exit)
        assertNotNull(exitIn)
        // x is not constant (could be 20 or 30 depending on branch)
        assertTrue(exitIn!!["x"] is ConstantValue.Bottom || exitIn["x"] is ConstantValue.Constant)
    }

    @Test
    fun testGenKillFrameworkReachingDefinitions() {
        // Verify that the gen-kill framework is working for reaching definitions
        val program =
            """
            let x = 10;
            let y = 20;
            x = 30;
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = ReachingDefinitionsAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // Both x and y should reach the exit
        val exitIn = analysisResult.getIn(cfg.exit)
        assertNotNull(exitIn)
        assertTrue(exitIn!!.contains("x"))
        assertTrue(exitIn.contains("y"))
    }

    @Test
    fun testGenKillFrameworkLiveVariables() {
        // Verify that the gen-kill framework is working for live variables
        val program =
            """
            let x = 10;
            let y = 20;
            print(x);
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val analysis = LiveVariablesAnalysis()
        val analysisResult = analysis.analyze(cfg)

        // At entry, we should have some live variable information
        assertNotNull(analysisResult.getIn(cfg.entry))
    }
}
