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
            Direction.FORWARD -> inFacts[cfg.entry] = analysis.initialValue()
            Direction.BACKWARD -> outFacts[cfg.exit] = analysis.initialValue()
        }

        // Helper lambdas to avoid duplicating neighbor/meet logic
        val neighborValues: (BasicBlock) -> List<T> =
            when (analysis.direction) {
                Direction.FORWARD -> { b -> b.predecessors.mapNotNull { outFacts[it] } }
                Direction.BACKWARD -> { b -> b.successors.mapNotNull { inFacts[it] } }
            }

        val isBoundaryBlock: (BasicBlock) -> Boolean =
            when (analysis.direction) {
                Direction.FORWARD -> { b -> b == cfg.entry }
                Direction.BACKWARD -> { b -> b == cfg.exit }
            }

        val worklist = ArrayDeque<BasicBlock>()
        worklist.addAll(cfg.getAllBlocks())

        while (worklist.isNotEmpty()) {
            val block = worklist.removeFirst()

            // Compute meet over neighbors (or use initial value for boundary)
            val meetValue = if (isBoundaryBlock(block)) analysis.initialValue() else analysis.meet(neighborValues(block))

            if (analysis.direction == Direction.FORWARD) {
                // IN = meet(pred OUTs)
                val newIn = meetValue
                inFacts[block] = newIn

                // OUT = transfer(IN)
                val newOut = analysis.transfer(newIn, block)

                // If OUT changed, add successors to worklist
                if (newOut != outFacts[block]) {
                    outFacts[block] = newOut
                    worklist.addAll(block.successors)
                }
            } else {
                // BACKWARD
                // OUT = meet(succ INs)
                val newOut = meetValue
                outFacts[block] = newOut

                // IN = transfer(OUT)
                val newIn = analysis.transfer(newOut, block)

                // If IN changed, add predecessors to worklist
                if (newIn != inFacts[block]) {
                    inFacts[block] = newIn
                    worklist.addAll(block.predecessors)
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
 * Generic gen-kill framework for dataflow analysis
 * This framework implements the standard gen-kill pattern where:
 *   OUT = (IN - kill) ∪ gen  (for forward analysis)
 *   IN  = (OUT - kill) ∪ gen (for backward analysis)
 *
 * @param T the type of the dataflow facts (must support set operations)
 */
abstract class GenKillAnalysis<T>(
    override val direction: Direction,
) : DataflowAnalysis<T>() {
    /**
     * Compute the gen set for a basic block or statement
     * Gen represents facts that are generated/defined at this point
     */
    abstract fun gen(block: BasicBlock): T

    /**
     * Compute the kill set for a basic block or statement
     * Kill represents facts that are invalidated/killed at this point
     */
    abstract fun kill(block: BasicBlock): T

    /**
     * Set difference operation: input - kill
     */
    abstract fun difference(
        input: T,
        kill: T,
    ): T

    /**
     * Set union operation: (input - kill) ∪ gen
     */
    abstract fun union(
        left: T,
        right: T,
    ): T

    /**
     * Transfer function using gen-kill pattern
     */
    override fun transfer(
        input: T,
        block: BasicBlock,
    ): T {
        val genSet = gen(block)
        val killSet = kill(block)
        // OUT = (IN - kill) ∪ gen
        return union(difference(input, killSet), genSet)
    }
}

/**
 * Example: Reaching Definitions Analysis using Gen-Kill Framework
 * A definition reaches a point if there exists a path from the definition to that point
 * where the variable is not redefined
 */
class ReachingDefinitionsAnalysis : GenKillAnalysis<Set<String>>(Direction.FORWARD) {
    override fun initialValue(): Set<String> = emptySet()

    override fun boundaryValue(): Set<String> = emptySet()

    override fun meet(values: List<Set<String>>): Set<String> =
        // Union of all predecessor OUT sets
        values.flatten().toSet()

    override fun gen(block: BasicBlock): Set<String> {
        val gen = mutableSetOf<String>()
        for (stmt in block.stmts) {
            when (stmt) {
                is Stmt.LetStmt -> gen.add(stmt.name)
                is Stmt.AssignStmt -> {
                    when (val lhs = stmt.lhs) {
                        is Expr.VarExpr -> gen.add(lhs.name)
                        else -> {}
                    }
                }
                else -> {}
            }
        }
        return gen
    }

    override fun kill(block: BasicBlock): Set<String> {
        val kill = mutableSetOf<String>()
        for (stmt in block.stmts) {
            when (stmt) {
                is Stmt.LetStmt -> kill.add(stmt.name)
                is Stmt.AssignStmt -> {
                    when (val lhs = stmt.lhs) {
                        is Expr.VarExpr -> kill.add(lhs.name)
                        else -> {}
                    }
                }
                else -> {}
            }
        }
        return kill
    }

    override fun difference(
        input: Set<String>,
        kill: Set<String>,
    ): Set<String> = input - kill

    override fun union(
        left: Set<String>,
        right: Set<String>,
    ): Set<String> = left + right
}

/**
 * Example: Live Variables Analysis using Gen-Kill Framework
 * A variable is live at a point if its value may be used in the future
 * For live variables: gen = use, kill = def
 */
class LiveVariablesAnalysis : GenKillAnalysis<Set<String>>(Direction.BACKWARD) {
    override fun initialValue(): Set<String> = emptySet()

    override fun boundaryValue(): Set<String> = emptySet()

    override fun meet(values: List<Set<String>>): Set<String> =
        // Union of all successor IN sets
        values.flatten().toSet()

    override fun gen(block: BasicBlock): Set<String> {
        // Gen = use (variables used in the block)
        val use = mutableSetOf<String>()
        // Process in reverse for backward analysis
        for (stmt in block.stmts.reversed()) {
            when (stmt) {
                is Stmt.LetStmt -> use.addAll(getUsedVariables(stmt.expr))
                is Stmt.AssignStmt -> use.addAll(getUsedVariables(stmt.expr))
                is Stmt.PrintStmt -> {
                    for (arg in stmt.args) {
                        use.addAll(getUsedVariables(arg))
                    }
                }
                is Stmt.ExprStmt -> use.addAll(getUsedVariables(stmt.expr))
                is Stmt.ReturnStmt -> use.addAll(getUsedVariables(stmt.expr))
                is Stmt.IfStmt -> use.addAll(getUsedVariables(stmt.condition))
                is Stmt.WhileStmt -> use.addAll(getUsedVariables(stmt.condition))
                else -> {}
            }
        }
        return use
    }

    override fun kill(block: BasicBlock): Set<String> {
        // Kill = def (variables defined in the block)
        val def = mutableSetOf<String>()
        for (stmt in block.stmts.reversed()) {
            when (stmt) {
                is Stmt.LetStmt -> def.add(stmt.name)
                is Stmt.AssignStmt -> {
                    when (val lhs = stmt.lhs) {
                        is Expr.VarExpr -> def.add(lhs.name)
                        else -> {}
                    }
                }
                else -> {}
            }
        }
        return def
    }

    override fun difference(
        input: Set<String>,
        kill: Set<String>,
    ): Set<String> = input - kill

    override fun union(
        left: Set<String>,
        right: Set<String>,
    ): Set<String> = left + right

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

/**
 * Lattice element for constant propagation
 * Represents the constant value of a variable or BOTTOM/TOP
 */
sealed class ConstantValue {
    /** Top element - no information yet */
    object Top : ConstantValue() {
        override fun toString() = "⊤"
    }

    /** Bottom element - not a constant (multiple possible values) */
    object Bottom : ConstantValue() {
        override fun toString() = "⊥"
    }

    /** A constant numeric value */
    data class Constant(
        val value: Double,
    ) : ConstantValue() {
        override fun toString() = value.toString()
    }
}

/**
 * Constant Propagation Analysis
 * Tracks which variables have constant values at each program point
 * Uses the worklist solver from the generic framework
 */
class ConstantPropagationAnalysis : DataflowAnalysis<Map<String, ConstantValue>>() {
    override val direction = Direction.FORWARD

    override fun initialValue(): Map<String, ConstantValue> = emptyMap()

    override fun boundaryValue(): Map<String, ConstantValue> = emptyMap()

    override fun meet(values: List<Map<String, ConstantValue>>): Map<String, ConstantValue> {
        if (values.isEmpty()) return emptyMap()

        // Collect all variable names
        val allVars = values.flatMap { it.keys }.toSet()
        val result = mutableMapOf<String, ConstantValue>()

        for (varName in allVars) {
            // Meet of constant values: if all have same constant, use it; otherwise BOTTOM
            val varValues = values.mapNotNull { it[varName] }
            if (varValues.isEmpty()) {
                // Variable not in any predecessor
                continue
            }

            // Start with the first value
            var meetValue: ConstantValue = varValues[0]
            for (i in 1 until varValues.size) {
                meetValue = meetConstantValues(meetValue, varValues[i])
            }
            result[varName] = meetValue
        }

        return result
    }

    /**
     * Meet two constant values
     * TOP ⊓ x = x
     * x ⊓ TOP = x
     * BOTTOM ⊓ x = BOTTOM
     * x ⊓ BOTTOM = BOTTOM
     * Constant(c1) ⊓ Constant(c2) = Constant(c1) if c1 == c2, else BOTTOM
     */
    private fun meetConstantValues(
        v1: ConstantValue,
        v2: ConstantValue,
    ): ConstantValue =
        when {
            v1 is ConstantValue.Top -> v2
            v2 is ConstantValue.Top -> v1
            v1 is ConstantValue.Bottom -> ConstantValue.Bottom
            v2 is ConstantValue.Bottom -> ConstantValue.Bottom
            v1 is ConstantValue.Constant && v2 is ConstantValue.Constant -> {
                if (v1.value == v2.value) v1 else ConstantValue.Bottom
            }
            else -> ConstantValue.Bottom
        }

    override fun transfer(
        input: Map<String, ConstantValue>,
        block: BasicBlock,
    ): Map<String, ConstantValue> {
        val output = input.toMutableMap()

        for (stmt in block.stmts) {
            when (stmt) {
                is Stmt.LetStmt -> {
                    // Evaluate the expression with current constant values
                    output[stmt.name] = evaluateExpr(stmt.expr, output)
                }
                is Stmt.AssignStmt -> {
                    when (val lhs = stmt.lhs) {
                        is Expr.VarExpr -> {
                            output[lhs.name] = evaluateExpr(stmt.expr, output)
                        }
                        else -> {
                            // Complex assignment - don't track
                        }
                    }
                }
                else -> {
                    // Other statements don't affect constants
                }
            }
        }

        return output
    }

    /**
     * Evaluate an expression to a constant value if possible
     */
    private fun evaluateExpr(
        expr: Expr,
        constants: Map<String, ConstantValue>,
    ): ConstantValue =
        when (expr) {
            is Expr.NumberLiteral -> ConstantValue.Constant(expr.value)
            is Expr.VarExpr -> constants[expr.name] ?: ConstantValue.Top
            is Expr.BinaryExpr -> {
                val left = evaluateExpr(expr.left, constants)
                val right = evaluateExpr(expr.right, constants)
                evaluateBinaryOp(expr.op, left, right)
            }
            is Expr.ParenExpr -> evaluateExpr(expr.expr, constants)
            else -> ConstantValue.Bottom // Unknown/non-constant expression
        }

    /**
     * Evaluate a binary operation on constant values
     */
    private fun evaluateBinaryOp(
        op: Operator,
        left: ConstantValue,
        right: ConstantValue,
    ): ConstantValue {
        if (left is ConstantValue.Constant && right is ConstantValue.Constant) {
            return try {
                when (op) {
                    Operator.PLUS -> ConstantValue.Constant(left.value + right.value)
                    Operator.MINUS -> ConstantValue.Constant(left.value - right.value)
                    Operator.TIMES -> ConstantValue.Constant(left.value * right.value)
                    Operator.DIV -> {
                        if (right.value == 0.0) {
                            ConstantValue.Bottom
                        } else {
                            ConstantValue.Constant(left.value / right.value)
                        }
                    }
                    Operator.MOD -> {
                        if (right.value == 0.0) {
                            ConstantValue.Bottom
                        } else {
                            ConstantValue.Constant(left.value % right.value)
                        }
                    }
                    // Comparison operators return 0.0 or 1.0 (false or true)
                    Operator.EQ -> ConstantValue.Constant(if (left.value == right.value) 1.0 else 0.0)
                    Operator.NEQ -> ConstantValue.Constant(if (left.value != right.value) 1.0 else 0.0)
                    Operator.LT -> ConstantValue.Constant(if (left.value < right.value) 1.0 else 0.0)
                    Operator.GT -> ConstantValue.Constant(if (left.value > right.value) 1.0 else 0.0)
                    Operator.LEQ -> ConstantValue.Constant(if (left.value <= right.value) 1.0 else 0.0)
                    Operator.GEQ -> ConstantValue.Constant(if (left.value >= right.value) 1.0 else 0.0)
                    // Logical operators: 0.0 is false, non-zero is true
                    Operator.AND -> {
                        ConstantValue.Constant(
                            if (left.value != 0.0 && right.value != 0.0) 1.0 else 0.0,
                        )
                    }
                    Operator.OR -> {
                        ConstantValue.Constant(
                            if (left.value != 0.0 || right.value != 0.0) 1.0 else 0.0,
                        )
                    }
                }
            } catch (e: ArithmeticException) {
                ConstantValue.Bottom
            }
        }

        // If either operand is BOTTOM, result is BOTTOM
        if (left is ConstantValue.Bottom || right is ConstantValue.Bottom) {
            return ConstantValue.Bottom
        }

        // If either operand is TOP, result is TOP (no information yet)
        return ConstantValue.Top
    }
}
