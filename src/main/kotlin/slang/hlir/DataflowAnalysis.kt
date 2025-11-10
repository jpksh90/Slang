package slang.hlir

/**
 * Direction of dataflow analysis
 */
enum class Direction {
    FORWARD,
    BACKWARD,
}

/**
 * Interface representing a lattice for dataflow analysis
 * A lattice defines the structure of dataflow facts
 */
interface Lattice<T> {
    /**
     * The top element of the lattice (initial/boundary value)
     */
    fun top(): T

    /**
     * The bottom element of the lattice (entry/exit value for analysis)
     */
    fun bottom(): T

    /**
     * Meet operator to merge dataflow facts
     * For must-analyses: intersection
     * For may-analyses: union
     */
    fun meet(values: List<T>): T
}

/**
 * Interface for dataflow solver strategies
 * Allows different solving algorithms (worklist, chaotic iteration, etc.)
 */
interface SolverStrategy {
    /**
     * Solve the dataflow equations for the given CFG
     *
     * @param cfg the control flow graph to analyze
     * @param analysis the dataflow analysis to run
     * @return a map from basic blocks to their IN and OUT dataflow facts
     */
    fun <T> solve(
        cfg: ControlFlowGraph,
        analysis: DataflowAnalysis<T>,
    ): DataflowResult<T>
}

/**
 * Worklist-based solver strategy
 * Uses a worklist algorithm for efficient fixed-point computation
 */
class WorklistSolver : SolverStrategy {
    override fun <T> solve(
        cfg: ControlFlowGraph,
        analysis: DataflowAnalysis<T>,
    ): DataflowResult<T> {
        val inFacts = mutableMapOf<BasicBlock, T>()
        val outFacts = mutableMapOf<BasicBlock, T>()

        // Initialize all blocks
        for (block in cfg.getAllBlocks()) {
            inFacts[block] = analysis.boundaryValue()
            outFacts[block] = analysis.boundaryValue()
        }

        // Set boundary condition
        when (analysis.direction) {
            Direction.FORWARD -> {
                inFacts[cfg.entry] = analysis.initialValue()
            }
            Direction.BACKWARD -> {
                outFacts[cfg.exit] = analysis.initialValue()
            }
        }

        // Worklist algorithm
        val worklist = mutableSetOf<BasicBlock>()
        worklist.addAll(cfg.getAllBlocks())

        while (worklist.isNotEmpty()) {
            val block = worklist.first()
            worklist.remove(block)

            when (analysis.direction) {
                Direction.FORWARD -> {
                    // IN[block] = meet(OUT[pred] for all pred)
                    val predValues =
                        block.predecessors.mapNotNull { pred ->
                            outFacts[pred]
                        }
                    val newIn = if (block == cfg.entry) analysis.initialValue() else analysis.meet(predValues)
                    inFacts[block] = newIn

                    // OUT[block] = transfer(IN[block], block)
                    val newOut = analysis.transfer(newIn, block)

                    // If OUT changed, add successors to worklist
                    if (newOut != outFacts[block]) {
                        outFacts[block] = newOut
                        worklist.addAll(block.successors)
                    }
                }
                Direction.BACKWARD -> {
                    // OUT[block] = meet(IN[succ] for all succ)
                    val succValues =
                        block.successors.mapNotNull { succ ->
                            inFacts[succ]
                        }
                    val newOut = if (block == cfg.exit) analysis.initialValue() else analysis.meet(succValues)
                    outFacts[block] = newOut

                    // IN[block] = transfer(OUT[block], block)
                    val newIn = analysis.transfer(newOut, block)

                    // If IN changed, add predecessors to worklist
                    if (newIn != inFacts[block]) {
                        inFacts[block] = newIn
                        worklist.addAll(block.predecessors)
                    }
                }
            }
        }

        return DataflowResult(inFacts, outFacts, analysis.direction)
    }
}

/**
 * Abstract interface for dataflow analysis
 *
 * @param T the type of the dataflow facts for the analysis
 * The lattice operations are provided by the abstract methods of this class.
 */
abstract class DataflowAnalysis<T> {
    /**
     * The direction of the analysis (FORWARD or BACKWARD)
     */
    abstract val direction: Direction

    /**
     * The initial value for the entry/exit block
     */
    abstract fun initialValue(): T

    /**
     * The initial value for all other blocks
     */
    abstract fun boundaryValue(): T

    /**
     * Meet operator to merge dataflow facts from multiple predecessors/successors
     */
    abstract fun meet(values: List<T>): T

    /**
     * Transfer function for a basic block
     * Computes the output dataflow fact given the input fact
     */
    abstract fun transfer(
        input: T,
        block: BasicBlock,
    ): T

    /**
     * Run the dataflow analysis on the CFG using the default worklist solver
     *
     * @param cfg the control flow graph to analyze
     * @return a map from basic blocks to their IN and OUT dataflow facts
     */
    fun analyze(cfg: ControlFlowGraph): DataflowResult<T> = analyze(cfg, WorklistSolver())

    /**
     * Run the dataflow analysis on the CFG using a custom solver strategy
     *
     * @param cfg the control flow graph to analyze
     * @param solver the solver strategy to use
     * @return a map from basic blocks to their IN and OUT dataflow facts
     */
    fun analyze(
        cfg: ControlFlowGraph,
        solver: SolverStrategy,
    ): DataflowResult<T> = solver.solve(cfg, this)
}

/**
 * Result of a dataflow analysis
 */
data class DataflowResult<T>(
    val inFacts: Map<BasicBlock, T>,
    val outFacts: Map<BasicBlock, T>,
    val direction: Direction,
) {
    /**
     * Get the IN fact for a block
     */
    fun getIn(block: BasicBlock): T? = inFacts[block]

    /**
     * Get the OUT fact for a block
     */
    fun getOut(block: BasicBlock): T? = outFacts[block]

    /**
     * Pretty print the dataflow results
     */
    fun prettyPrint(blockPrinter: (BasicBlock) -> String = { it.toString() }): String {
        val sb = StringBuilder()
        sb.append("Dataflow Analysis Result (${direction.name}):\n")
        for ((block, inFact) in inFacts) {
            sb.append("  ${blockPrinter(block)}:\n")
            sb.append("    IN:  $inFact\n")
            sb.append("    OUT: ${outFacts[block]}\n")
        }
        return sb.toString()
    }
}

/**
 * Example: Reaching Definitions Analysis
 * A definition reaches a point if there exists a path from the definition to that point
 * where the variable is not redefined
 */
class ReachingDefinitionsAnalysis : DataflowAnalysis<Set<String>>() {
    override val direction = Direction.FORWARD

    override fun initialValue(): Set<String> = emptySet()

    override fun boundaryValue(): Set<String> = emptySet()

    override fun meet(values: List<Set<String>>): Set<String> =
        // Union of all predecessor OUT sets
        values.flatten().toSet()

    override fun transfer(
        input: Set<String>,
        block: BasicBlock,
    ): Set<String> {
        val gen = mutableSetOf<String>()
        val kill = mutableSetOf<String>()

        // Process each statement in the block
        for (stmt in block.stmts) {
            when (stmt) {
                is Stmt.LetStmt -> {
                    gen.add(stmt.name)
                    kill.add(stmt.name) // Kill previous definitions
                }
                is Stmt.AssignStmt -> {
                    when (val lhs = stmt.lhs) {
                        is Expr.VarExpr -> {
                            gen.add(lhs.name)
                            kill.add(lhs.name) // Kill previous definitions
                        }
                        else -> {
                            // For more complex assignments, we don't track them yet
                        }
                    }
                }
                else -> {
                    // Other statements don't define variables
                }
            }
        }

        // OUT = (IN - kill) ∪ gen
        return (input - kill) + gen
    }
}

/**
 * Example: Live Variables Analysis
 * A variable is live at a point if its value may be used in the future
 */
class LiveVariablesAnalysis : DataflowAnalysis<Set<String>>() {
    override val direction = Direction.BACKWARD

    override fun initialValue(): Set<String> = emptySet()

    override fun boundaryValue(): Set<String> = emptySet()

    override fun meet(values: List<Set<String>>): Set<String> =
        // Union of all successor IN sets
        values.flatten().toSet()

    override fun transfer(
        input: Set<String>,
        block: BasicBlock,
    ): Set<String> {
        val use = mutableSetOf<String>()
        val def = mutableSetOf<String>()

        // Process each statement in the block (in reverse for backward analysis)
        for (stmt in block.stmts.reversed()) {
            when (stmt) {
                is Stmt.LetStmt -> {
                    def.add(stmt.name)
                    use.addAll(getUsedVariables(stmt.expr))
                }
                is Stmt.AssignStmt -> {
                    when (val lhs = stmt.lhs) {
                        is Expr.VarExpr -> {
                            def.add(lhs.name)
                        }
                        else -> {
                            // For complex assignments, we don't track them yet
                        }
                    }
                    use.addAll(getUsedVariables(stmt.expr))
                }
                is Stmt.PrintStmt -> {
                    for (arg in stmt.args) {
                        use.addAll(getUsedVariables(arg))
                    }
                }
                is Stmt.ExprStmt -> {
                    use.addAll(getUsedVariables(stmt.expr))
                }
                is Stmt.ReturnStmt -> {
                    use.addAll(getUsedVariables(stmt.expr))
                }
                is Stmt.IfStmt -> {
                    use.addAll(getUsedVariables(stmt.condition))
                }
                is Stmt.WhileStmt -> {
                    use.addAll(getUsedVariables(stmt.condition))
                }
                else -> {
                    // Other statements don't use/define variables
                }
            }
        }

        // IN = (OUT - def) ∪ use
        return (input - def) + use
    }

    private fun getUsedVariables(expr: Expr): Set<String> {
        val used = mutableSetOf<String>()

        fun visit(e: Expr) {
            when (e) {
                is Expr.VarExpr -> used.add(e.name)
                is Expr.BinaryExpr -> {
                    visit(e.left)
                    visit(e.right)
                }
                is Expr.IfExpr -> {
                    visit(e.condition)
                    visit(e.thenExpr)
                    visit(e.elseExpr)
                }
                is Expr.ParenExpr -> visit(e.expr)
                is Expr.NamedFunctionCall -> {
                    for (arg in e.arguments) {
                        visit(arg)
                    }
                }
                is Expr.ExpressionFunctionCall -> {
                    visit(e.target)
                    for (arg in e.arguments) {
                        visit(arg)
                    }
                }
                is Expr.RefExpr -> visit(e.expr)
                is Expr.DerefExpr -> visit(e.expr)
                is Expr.FieldAccess -> {
                    visit(e.lhs)
                    visit(e.rhs)
                }
                is Expr.ArrayAccess -> {
                    visit(e.array)
                    visit(e.index)
                }
                is Expr.ArrayInit -> {
                    for (elem in e.elements) {
                        visit(elem)
                    }
                }
                is Expr.Record -> {
                    for ((_, exprVal) in e.expression) {
                        visit(exprVal)
                    }
                }
                else -> {
                    // Literals and other expressions don't use variables
                }
            }
        }

        visit(expr)
        return used
    }
}
