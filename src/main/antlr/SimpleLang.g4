grammar SimpleLang;

prog    : stmt* EOF ;

stmt
    : 'let' ID '=' expr ';'    # letExpr
    | ID '=' expr ';' # assignExpr
    | 'fun' ID '(' paramList? ')' '=>'  expr ';' # funPure
    | 'fun' ID '(' paramList? ')' '{' stmt* '}' # funImpure
    | 'while' expr blockStmt # whileStmt
    | 'for' '(' ID '=' expr ';' expr ';' ID1 = expr ')' blockStmt # forStmt
    | 'print' '(' argList? ')' ';' # printStmt
    | 'if' '(' expr ')' ifStmt 'else' elseStmt #ifThenElseStmt
    | expr ';' # exprStmt
    | 'return' expr ';' #returnStmt
    ;

blockStmt : '{' stmt* '}';
ifStmt : '{' stmt* '}';
elseStmt : '{' stmt* '}';

expr
    : INT                         # intExpr
    | BOOL                        # boolExpr
    | ID                          # varExpr
    | 'readInput' '(' ')'         # readInputExpr
    | ID '(' argList? ')'         # funcCallExpr
    | expr op=('+'|'-'|'*'|'/') expr  # arithmeticExpr
    | expr op=('=='|'!='|'<'|'>') expr # comparisonExpr
    | expr op=('&&'|'||') expr    # booleanExpr
    | 'ifte' '(' expr ',' expr ',' expr ')' # ifExpr
    | '(' expr ')'                # parenExpr
    ;

argList : expr (',' expr)* ;

paramList : ID (',' ID)* ;

BOOL : 'true' | 'false' ;

ID : [a-zA-Z_][a-zA-Z0-9_]* ;

INT : [0-9]+ ;

WS : [ \t\r\n]+ -> skip ;
