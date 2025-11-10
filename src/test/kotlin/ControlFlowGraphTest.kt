package slang.hlir

import slang.common.Result
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ControlFlowGraphTest {
    @Test
    fun testSimpleSequentialProgram() { // Test: let x = 10; let y = 5; print(x + y);
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

        // Should have: entry, 3 statements in blocks, exit
        assertTrue(cfg.blocks.size >= 3) // At minimum: entry, body, exit

        // Entry should have one successor
        assertTrue(cfg.entry.successors.isNotEmpty())

        // Exit should have no successors
        assertTrue(cfg.exit.successors.isEmpty())
    }

    @Test
    fun testIfStatement() { // Test: if (x > 0) then { print(x); } else { print(0); }
        val program =
            """
            let x = 10;
            if (x > 0) {
                print(x);
            } else {
                print(0);
            }
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()

        // Should have branching
        assertTrue(cfg.blocks.size >= 5) // entry, let, if, then, else, merge, exit

        // Find the if block (should have 2 successors)
        val branchBlocks = cfg.blocks.filter { it.successors.size >= 2 }
        assertTrue(branchBlocks.isNotEmpty())
    }

    @Test
    fun testWhileLoop() { // Test: while (x > 0) { x = x - 1; }
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

        // Should have a loop (some block should have a predecessor that comes after it in flow)
        assertTrue(cfg.blocks.size >= 4) // entry, let, while cond, body, exit

        // Find loop header (should have a back edge)
        val loopHeaders = cfg.blocks.filter { block -> block.predecessors.any { it in block.successors } }
        assertTrue(loopHeaders.isNotEmpty())
    }

    @Test
    fun testFunctionCFG() { // Test function with if-then-else
        val program =
            """
            fun max(a, b) {
                if (a > b) {
                    return a;
                } else {
                    return b;
                }
            }
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        // Get the function
        val function = programUnit.stmt.filterIsInstance<Stmt.Function>().firstOrNull()
        assertNotNull(function)

        val cfg = function.buildCFG()

        // Should have entry, if, then, else, merge, exit
        assertTrue(cfg.blocks.size >= 4)

        // Entry should have successors
        assertTrue(cfg.entry.successors.isNotEmpty())
    }

    @Test
    fun testNestedIfStatements() {
        val program =
            """
            let x = 10;
            if (x > 5) {
                if (x > 8) {
                    print(1);
                } else {
                    print(2);
                }
            } else {
                print(3);
            }
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()

        // Should have multiple branching points
        assertTrue(cfg.blocks.size >= 6)
    }

    @Test
    fun testCFGPrettyPrint() {
        val program =
            """
            let x = 10;
            print(x);
            """.trimIndent()

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()
        val output = cfg.prettyPrint()

        // Should contain CFG structure
        assertTrue(output.contains("CFG:"))
    }

    @Test
    fun testEmptyProgram() {
        val program = "let x = 1;"

        val result = string2hlir(program)
        assertTrue(result is Result.Ok)
        val programUnit = (result as Result.Ok).value

        val cfg = programUnit.buildCFG()

        // Should have at least entry and exit
        assertTrue(cfg.blocks.size >= 2)
        assertNotNull(cfg.entry)
        assertNotNull(cfg.exit)
    }
}
