package slang.hlir

/**
 * Represents a basic block in the Control Flow Graph
 */
data class BasicBlock(
    val id: Int,
    val stmts: List<Stmt>,
    val successors: MutableSet<BasicBlock> = mutableSetOf(),
    val predecessors: MutableSet<BasicBlock> = mutableSetOf(),
) {
    override fun toString(): String = "BB$id"

    override fun hashCode(): Int = id

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BasicBlock) return false
        return id == other.id
    }
}

/**
 * Control Flow Graph for a function or program
 */
class ControlFlowGraph(
    val entry: BasicBlock,
    val exit: BasicBlock,
    val blocks: List<BasicBlock>,
) {
    /**
     * Returns all basic blocks in the CFG
     */
    fun getAllBlocks(): List<BasicBlock> = blocks

    /**
     * Returns a string representation of the CFG
     */
    fun prettyPrint(): String {
        val sb = StringBuilder()
        sb.append("CFG:\n")
        for (block in blocks) {
            sb.append("  ${block.id}:\n")
            for (stmt in block.stmts) {
                sb.append("    ${stmt.prettyPrint()}\n")
            }
            if (block.successors.isNotEmpty()) {
                sb.append("    -> ${block.successors.joinToString(", ") { it.toString() }}\n")
            }
        }
        return sb.toString()
    }
}

/**
 * Builder for constructing Control Flow Graphs from AST statements
 */
class CFGBuilder {
    private var blockIdCounter = 0
    private val allBlocks = mutableListOf<BasicBlock>()

    private fun newBlock(stmts: List<Stmt> = emptyList()): BasicBlock {
        val block = BasicBlock(blockIdCounter++, stmts)
        allBlocks.add(block)
        return block
    }

    private fun addEdge(
        from: BasicBlock,
        to: BasicBlock,
    ) {
        if (!from.successors.contains(to)) {
            from.successors.add(to)
        }
        if (!to.predecessors.contains(from)) {
            to.predecessors.add(from)
        }
    }

    /**
     * Build CFG for a function
     */
    fun buildForFunction(function: Stmt.Function): ControlFlowGraph {
        blockIdCounter = 0
        allBlocks.clear()

        val entry = newBlock()
        val exit = newBlock()

        val bodyResult = buildForStmt(function.body, exit)
        addEdge(entry, bodyResult.entry)
        addEdge(bodyResult.exit, exit)

        return ControlFlowGraph(entry, exit, allBlocks)
    }

    /**
     * Build CFG for a program
     */
    fun buildForProgram(program: ProgramUnit): ControlFlowGraph {
        // For backward compatibility with tests, return a single CFG representing the
        // module-level "__module__main__" function if present. Otherwise, build a
        // synthetic entry/exit with any top-level statements.
        blockIdCounter = 0
        allBlocks.clear()

        if (program.stmt.isEmpty()) {
            val entry = newBlock()
            val exit = newBlock()
            return ControlFlowGraph(entry, exit, allBlocks)
        }

        // Use the first module for program-level CFG
        val module = program.stmt[0]

        // Try to find the synthetic module main function created by the IR builder
        val moduleMain = module.functions.find { it.name == "__module__main__" }
        return if (moduleMain != null) {
            buildForFunction(moduleMain)
        } else if (module.functions.isNotEmpty()) {
            // Fallback: build CFG for the first top-level function
            buildForFunction(module.functions[0])
        } else {
            // No functions: create empty entry/exit
            val entry = newBlock()
            val exit = newBlock()
            ControlFlowGraph(entry, exit, allBlocks)
        }
    }

    private data class CFGSegment(
        val entry: BasicBlock,
        val exit: BasicBlock,
        val breakTargets: List<BasicBlock> = emptyList(),
        val continueTargets: List<BasicBlock> = emptyList(),
    )

    private fun buildForStmtList(
        stmts: List<Stmt>,
        exitBlock: BasicBlock,
    ): CFGSegment {
        if (stmts.isEmpty()) {
            val emptyBlock = newBlock()
            return CFGSegment(emptyBlock, emptyBlock)
        }

        var currentSegment = buildForStmt(stmts[0], exitBlock)
        val entry = currentSegment.entry

        val accumulatedBreaks = mutableListOf<BasicBlock>()
        val accumulatedContinues = mutableListOf<BasicBlock>()
        accumulatedBreaks.addAll(currentSegment.breakTargets)
        accumulatedContinues.addAll(currentSegment.continueTargets)

        for (i in 1 until stmts.size) {
            val nextSegment = buildForStmt(stmts[i], exitBlock)
            // Normal flow: connect the previous segment's exit to the next segment's entry
            addEdge(currentSegment.exit, nextSegment.entry)

            // Accumulate break/continue targets from subsequent segments; they should not be
            // connected into the normal fall-through chain here (they are handled by loops)
            accumulatedBreaks.addAll(nextSegment.breakTargets)
            accumulatedContinues.addAll(nextSegment.continueTargets)

            currentSegment = CFGSegment(entry, nextSegment.exit, accumulatedBreaks.toList(), accumulatedContinues.toList())
        }

        return CFGSegment(entry, currentSegment.exit, accumulatedBreaks.toList(), accumulatedContinues.toList())
    }

    private fun buildForStmt(
        stmt: Stmt,
        exitBlock: BasicBlock,
    ): CFGSegment =
        when (stmt) {
            is Stmt.LetStmt,
            is Stmt.AssignStmt,
            is Stmt.PrintStmt,
            is Stmt.ExprStmt,
            is Stmt.ReturnStmt,
            is Stmt.DerefStmt,
            -> {
                val block = newBlock(listOf(stmt))
                CFGSegment(block, block)
            }

            is Stmt.BlockStmt -> {
                if (stmt.stmts.isEmpty()) {
                    val emptyBlock = newBlock()
                    CFGSegment(emptyBlock, emptyBlock)
                } else {
                    buildForStmtList(stmt.stmts, exitBlock)
                }
            }

            is Stmt.IfStmt -> {
                val condBlock = newBlock(listOf(stmt))
                val thenSegment = buildForStmt(stmt.thenBody, exitBlock)
                val elseSegment = buildForStmt(stmt.elseBody, exitBlock)
                val mergeBlock = newBlock()

                addEdge(condBlock, thenSegment.entry)
                addEdge(condBlock, elseSegment.entry)
                addEdge(thenSegment.exit, mergeBlock)
                addEdge(elseSegment.exit, mergeBlock)

                // combine break/continue targets from both branches and propagate upward
                val combinedBreaks = thenSegment.breakTargets + elseSegment.breakTargets
                val combinedContinues = thenSegment.continueTargets + elseSegment.continueTargets

                CFGSegment(condBlock, mergeBlock, combinedBreaks, combinedContinues)
            }

            is Stmt.WhileStmt -> {
                val condBlock = newBlock(listOf(stmt))
                val mergeBlock = newBlock()

                // Build the loop body with the knowledge that its breaks should target mergeBlock
                val bodySegment = buildForStmt(stmt.body, exitBlock)

                // Normal loop edges: cond -> body, body -> cond, cond -> merge (loop exit)
                addEdge(condBlock, bodySegment.entry)
                addEdge(bodySegment.exit, condBlock)
                addEdge(condBlock, mergeBlock)

                // Resolve break targets inside the loop: they should jump to mergeBlock
                for (bt in bodySegment.breakTargets) {
                    addEdge(bt, mergeBlock)
                }

                // Resolve continue targets inside the loop: they should jump to the condition block
                for (ct in bodySegment.continueTargets) {
                    addEdge(ct, condBlock)
                }

                // Consumed break/continue targets should not propagate beyond this loop
                CFGSegment(condBlock, mergeBlock)
            }

            is Stmt.Break -> {
                val block = newBlock(listOf(stmt))
                // Break statements need special handling - they jump to the loop exit
                // Represent a break by returning the block as a break target. It will be
                // wired up by the nearest enclosing loop to jump to the loop exit.
                CFGSegment(block, block, breakTargets = listOf(block))
            }

            is Stmt.Continue -> {
                val block = newBlock(listOf(stmt))
                // Continue statements need special handling - they jump to the loop condition
                // Represent a continue by returning the block as a continue target. It will be
                // wired up by the nearest enclosing loop to jump back to the loop condition.
                CFGSegment(block, block, continueTargets = listOf(block))
            }

            is Stmt.Function,
            is Stmt.StructStmt,
            -> {
                // Functions and structs are not part of the control flow
                // They define new scopes but don't affect the current flow
                val block = newBlock()
                CFGSegment(block, block)
            }
        }
}

/**
 * Extension function to build CFG for a function
 */
fun Stmt.Function.buildCFG(): ControlFlowGraph = CFGBuilder().buildForFunction(this)

/**
 * Extension function to build CFG for a program
 */
fun ProgramUnit.buildCFG(): ControlFlowGraph = CFGBuilder().buildForProgram(this)
