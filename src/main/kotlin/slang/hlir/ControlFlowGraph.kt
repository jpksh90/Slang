package slang.hlir

/**
 * Represents a basic block in the Control Flow Graph
 */
data class BasicBlock(
    val id: Int,
    val stmts: List<Stmt>,
    val successors: MutableList<BasicBlock> = mutableListOf(),
    val predecessors: MutableList<BasicBlock> = mutableListOf(),
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
        blockIdCounter = 0
        allBlocks.clear()

        val entry = newBlock()
        val exit = newBlock()

        val bodyBlock = buildForStmtList(program.stmt, exit)
        addEdge(entry, bodyBlock.entry)
        addEdge(bodyBlock.exit, exit)

        return ControlFlowGraph(entry, exit, allBlocks)
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

        for (i in 1 until stmts.size) {
            val nextSegment = buildForStmt(stmts[i], exitBlock)
            addEdge(currentSegment.exit, nextSegment.entry)
            currentSegment = CFGSegment(entry, nextSegment.exit)
        }

        return CFGSegment(entry, currentSegment.exit)
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

                CFGSegment(condBlock, mergeBlock)
            }

            is Stmt.WhileStmt -> {
                val condBlock = newBlock(listOf(stmt))
                val mergeBlock = newBlock()
                val bodySegment = buildForStmt(stmt.body, exitBlock)

                addEdge(condBlock, bodySegment.entry)
                addEdge(bodySegment.exit, condBlock)
                addEdge(condBlock, mergeBlock)

                CFGSegment(condBlock, mergeBlock)
            }

            is Stmt.Break -> {
                val block = newBlock(listOf(stmt))
                // Break statements need special handling - they jump to the loop exit
                // For now, we create a dead-end block
                CFGSegment(block, block, breakTargets = listOf(block))
            }

            is Stmt.Continue -> {
                val block = newBlock(listOf(stmt))
                // Continue statements need special handling - they jump to the loop condition
                // For now, we create a dead-end block
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
