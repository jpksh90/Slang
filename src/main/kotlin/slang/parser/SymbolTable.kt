package slang.parser

object SymbolTable {
    val structDeclarations = mutableMapOf<SlangParser.CompilationUnitContext, SlangParser.StructStmtContext>()
    val globalVariables = mutableMapOf<SlangParser.CompilationUnitContext, String>()
}