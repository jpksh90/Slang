# Slang

Slang is a simple programming language designed for educational purposes. It features basic constructs such as variable declarations, function definitions, control flow statements, and expressions. The language is designed to be easy to understand and use, making it ideal for learning the fundamentals of programming languages and compilers.

## Features

- **Variable Declarations**: Declare variables using the `let` keyword.
- **Function Definitions**: Define pure and impure functions using the `fun` keyword.
- **Control Flow**: Supports `if-else`, `while`, `for`, and `do-while` statements.
- **Expressions**: Supports arithmetic, boolean, comparison, and function call expressions.
- **Built-in Functions**: Includes a `print` function for output and a `readInput` function for input.

## Grammar

The grammar for Slang is defined using ANTLR and includes the following rules:

- **compilationUnit**: The root of the program, consisting of multiple statements.
- **stmt**: Statements including variable declarations, assignments, function definitions, control flow, and expressions.
- **expr**: Expressions including arithmetic, boolean, comparison, and function calls.
- **primaryExpr**: Primary expressions such as numbers, booleans, strings, and variable references.

## Example

Here is an example of a Slang program:

```
fun apply_n(f, n, x) => if (n == 0) then x else f(apply_n(f, n - 1, f(x)));
```

This program defines a higher-order function `apply_n` that applies a given function `f` `n` times to an input `x`.

## Project Structure

- `src/main/antlr/Slang.g4`: The ANTLR grammar file for Slang.
- `src/main/kotlin/slast/ast/AstBuilder.kt`: The AST builder that constructs the abstract syntax tree from the parse tree.
- `build/generated-src/antlr/main/SlangParser.java`: The generated parser code.
