# Control Flow Graph (CFG) and Dataflow Analysis

This document describes the Control Flow Graph (CFG) and dataflow analysis framework implemented for the Slang HLIR.

## Overview

The CFG and dataflow analysis framework provides tools for analyzing control flow and data dependencies in Slang programs. This is useful for:

- Program optimization
- Static analysis
- Bug detection
- Understanding program structure

## Control Flow Graph (CFG)

The CFG represents the flow of control through a program as a directed graph where:
- **Nodes (Basic Blocks)**: Sequences of statements with a single entry and exit point
- **Edges**: Represent possible control flow paths between basic blocks

### Building a CFG

You can build a CFG for a program or a function:

```kotlin
import slang.hlir.*

// For a program
val program = string2hlir("let x = 10; print(x);")
val cfg = (program as Result.Ok).value.buildCFG()

// For a function
val functionProgram = string2hlir("""
    fun factorial(n) {
        if (n == 0) {
            return 1;
        } else {
            return n * factorial(n - 1);
        }
    }
""")
val function = (functionProgram as Result.Ok).value.stmt
    .filterIsInstance<Stmt.Function>()
    .first()
val functionCFG = function.buildCFG()
```

### CFG Structure

A CFG consists of:
- **Entry Block**: Where execution begins
- **Exit Block**: Where execution ends
- **Basic Blocks**: Intermediate blocks containing statements
- **Successors/Predecessors**: Edges connecting blocks

```kotlin
// Access CFG components
val entry = cfg.entry
val exit = cfg.exit
val allBlocks = cfg.getAllBlocks()

// Navigate the graph
for (block in allBlocks) {
    println("Block ${block.id}:")
    for (stmt in block.stmts) {
        println("  ${stmt.prettyPrint()}")
    }
    println("  Successors: ${block.successors.map { it.id }}")
}

// Pretty print the CFG
println(cfg.prettyPrint())
```

## Dataflow Analysis Framework

The dataflow analysis framework uses a **worklist algorithm** to compute dataflow facts at each program point. It's designed to be extensible, allowing you to implement custom analyses.

### Built-in Analyses

#### 1. Reaching Definitions Analysis

Determines which variable definitions reach each program point (forward analysis).

```kotlin
val cfg = programUnit.buildCFG()
val analysis = ReachingDefinitionsAnalysis()
val result = analysis.analyze(cfg)

// Get IN and OUT facts for each block
for (block in cfg.getAllBlocks()) {
    val inFacts = result.getIn(block)
    val outFacts = result.getOut(block)
    println("Block ${block.id}:")
    println("  IN:  $inFacts")
    println("  OUT: $outFacts")
}
```

#### 2. Live Variables Analysis

Determines which variables are live (may be used in the future) at each program point (backward analysis).

```kotlin
val cfg = programUnit.buildCFG()
val analysis = LiveVariablesAnalysis()
val result = analysis.analyze(cfg)

// Pretty print results
println(result.prettyPrint())
```

### Implementing Custom Analyses

You can create custom dataflow analyses by extending the `DataflowAnalysis<T>` class:

```kotlin
class MyCustomAnalysis : DataflowAnalysis<MyFactType>() {
    // Specify direction: FORWARD or BACKWARD
    override val direction = Direction.FORWARD
    
    // Initial value for entry/exit block
    override fun initialValue(): MyFactType = ...
    
    // Initial value for all other blocks
    override fun boundaryValue(): MyFactType = ...
    
    // Meet operator to merge facts from predecessors/successors
    override fun meet(values: List<MyFactType>, block: BasicBlock): MyFactType {
        // Combine dataflow facts (e.g., union, intersection)
        return ...
    }
    
    // Transfer function for a basic block
    override fun transfer(input: MyFactType, block: BasicBlock): MyFactType {
        // Compute output fact based on input and block statements
        return ...
    }
}

// Use the custom analysis
val cfg = programUnit.buildCFG()
val analysis = MyCustomAnalysis()
val result = analysis.analyze(cfg)
```

### Example: Constant Propagation

Here's an example of a simple constant propagation analysis:

```kotlin
class ConstantPropagationAnalysis : DataflowAnalysis<Map<String, Int?>>() {
    override val direction = Direction.FORWARD
    
    override fun initialValue() = emptyMap<String, Int?>()
    override fun boundaryValue() = emptyMap<String, Int?>()
    
    override fun meet(values: List<Map<String, Int?>>, block: BasicBlock): Map<String, Int?> {
        if (values.isEmpty()) return emptyMap()
        
        // Intersection: variable is constant only if same in all paths
        val result = values[0].toMutableMap()
        for (i in 1 until values.size) {
            val keys = result.keys.toSet()
            for (key in keys) {
                if (result[key] != values[i][key]) {
                    result.remove(key)  // Not constant across all paths
                }
            }
        }
        return result
    }
    
    override fun transfer(input: Map<String, Int?>, block: BasicBlock): Map<String, Int?> {
        val output = input.toMutableMap()
        for (stmt in block.stmts) {
            when (stmt) {
                is Stmt.LetStmt -> {
                    when (val expr = stmt.expr) {
                        is Expr.NumberLiteral -> {
                            output[stmt.name] = expr.value.toInt()
                        }
                        else -> {
                            output.remove(stmt.name)  // Not a constant
                        }
                    }
                }
                // Handle other statements...
                else -> {}
            }
        }
        return output
    }
}
```

## Worklist Algorithm

The dataflow analysis framework uses a worklist algorithm, which:

1. Initializes all blocks with boundary values
2. Sets the entry/exit block to the initial value
3. Iteratively processes blocks from a worklist:
   - Computes new dataflow facts using the meet and transfer functions
   - If facts change, adds affected blocks to the worklist
4. Terminates when no more changes occur (reaches a fixed point)

This algorithm is efficient and guaranteed to terminate for monotonic dataflow problems.

## Use Cases

### 1. Dead Code Detection

Use live variables analysis to find unused variables:

```kotlin
val lvAnalysis = LiveVariablesAnalysis()
val result = lvAnalysis.analyze(cfg)

for (block in cfg.getAllBlocks()) {
    for (stmt in block.stmts) {
        if (stmt is Stmt.LetStmt) {
            val outFacts = result.getOut(block) ?: emptySet()
            if (stmt.name !in outFacts) {
                println("Dead code: Variable '${stmt.name}' is never used")
            }
        }
    }
}
```

### 2. Uninitialized Variable Detection

Use reaching definitions analysis to find potentially uninitialized variables:

```kotlin
val rdAnalysis = ReachingDefinitionsAnalysis()
val result = rdAnalysis.analyze(cfg)

for (block in cfg.getAllBlocks()) {
    for (stmt in block.stmts) {
        // Check if variables used in expressions are defined
        val inFacts = result.getIn(block) ?: emptySet()
        // ... check if used variables are in inFacts
    }
}
```

## Testing

The framework includes comprehensive tests in:
- `ControlFlowGraphTest.kt`: Tests for CFG construction
- `DataflowAnalysisTest.kt`: Tests for dataflow analyses

Run tests with:
```bash
./gradlew test
```

## Implementation Details

### CFG Construction

The `CFGBuilder` class constructs CFGs using a recursive descent approach:
- Sequential statements are connected in order
- Conditionals create branching paths
- Loops create back-edges
- Functions create separate CFGs

### Dataflow Analysis

The `DataflowAnalysis` abstract class provides:
- Generic framework parameterized by fact type
- Support for forward and backward analyses
- Worklist algorithm implementation
- Extensibility through abstract methods

## References

- Cooper, Keith D., and Linda Torczon. "Engineering a compiler." (2011)
- Aho, Alfred V., et al. "Compilers: Principles, techniques, and tools." (2007)
