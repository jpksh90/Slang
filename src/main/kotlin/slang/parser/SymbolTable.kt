package slang.parser

import SlangParser


object SymbolTable {
    val structDeclarations = mutableMapOf<SlangParser.CompilationUnitContext, SlangParser.StructStmtContext>()
//    val globalVariables = mutableMapOf<SlangParser.CompilationUnitContext, String>()
}