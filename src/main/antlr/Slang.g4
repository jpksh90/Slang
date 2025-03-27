grammar Slang;

compilationUnit  : stmt* EOF ;

stmt
    : 'let' ID '=' expr ';'    # letExpr
    | lhs '=' expr ';' # assignExpr
    | 'while' expr blockStmt # whileStmt
    | 'for' '(' ID '=' expr ';' expr ';' ID1 = expr ')' blockStmt # forStmt
    | 'print' '(' argList? ')' ';' # printStmt
    | 'if' '(' expr ')' blockStmt 'else' blockStmt #ifThenElseStmt
    | expr ';' # exprStmt
    | 'return' expr ';' #returnStmt
    | 'do' blockStmt 'while' '(' expr ')' ';' #doWhileStmt
    ;

recordElems
    : ID ':' expr (',' ID ':' expr)*
    ;

blockStmt : '{' stmt* '}';

lhs : ID | fieldAccess | deref;

expr
    : primaryExpr                                       # primaryExprWrapper
    | expr '.' ID                                       # fieldAccessExpr
    | expr '(' argList? ')'                             # funcCallExpr
    | 'if' '(' expr ')' 'then' expr 'else' expr         # ifExpr
    | expr op=('*' | '/') expr                          # arithmeticExpr
    | expr op=('+' | '-') expr                          # arithmeticExpr
    | expr op=('==' | '!=' | '<' | '>') expr            # comparisonExpr
    | expr op=('&&' | '||') expr                        # booleanExpr
    | 'fun' ID? '(' paramList? ')' '=>'  expr           # funPureExpr
    | 'fun' ID? '(' paramList? ')' '{' stmt* '}'        # funImpureExpr
    ;

primaryExpr
    : NUMBER                      # intExpr
    | BOOL                        # boolExpr
    | STRING                      # stringExpr
    | ID                          # varExpr
    | NONE                        # noneValue
    | 'readInput' '(' ')'         # readInputExpr
    | '{' recordElems? '}'        # recordExpr
    | 'ref' '(' expr ')'          # refExpr
    | '(' expr ')'                # parenExpr
    | deref                       # derefExpr
    ;

fieldAccess:
    expr '.' ID;

deref: 'deref(' expr ')';

argList : expr (',' expr)* ;

paramList : ID (',' ID)* ;

BOOL : 'true' | 'false' ;

NONE : 'None';

ID : [a-zA-Z_][a-zA-Z0-9_]* ;

NUMBER :  '-'?[0-9]+ ('.' [0-9]+)? ;

STRING : '"' ( ~["\\] | '\\' . )* '"' ;

COMMENT : '#' ~[\r\n]* -> skip ;

MULTILINE_COMMENT : '(*' .*? '*)' -> skip ;

WS : [ \t\r\n]+ -> skip ;
