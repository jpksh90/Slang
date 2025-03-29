grammar Slang;

compilationUnit  : stmt* EOF ;

stmt
    : 'fun' ID '(' paramList? ')' '=>'  expr ';' #funPureStmt
    | 'fun' ID '(' paramList? ')' '{' stmt* '}' #funImpureStmt
    |'let' ID '=' expr ';'    # letExpr
    | lhs '=' expr ';' # assignExpr
    | 'while' expr blockStmt # whileStmt
    | 'for' '(' ID '=' expr ';' expr ';' ID1 = expr ')' blockStmt # forStmt
    | 'print' '(' argList? ')' ';' # printStmt
    | 'if' '(' expr ')' blockStmt 'else' blockStmt #ifThenElseStmt
    | expr ';' # exprStmt
    | 'return' expr ';' #returnStmt
    | 'do' blockStmt 'while' '(' expr ')' ';' #doWhileStmt
    | 'break' ';' #breakStmt
    | 'continue' ';' #continueStmt
    | 'struct' ID '(' constructorMembers ')' '{' structMember*'}' #structStmt
    ;

constructorMembers : ID (',' ID)* ;

structMember
    : 'let' ID '=' expr ';' # structField
    | 'fun' ID '(' paramList? ')' '=>'  expr ';' # structMethodPure
    | 'fun' ID '(' paramList? ')' '{' stmt* '}' # structMethodImpure
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
    | expr op=('==' | '!=' | '<' | '>' | '<=' | '>=' ) expr            # comparisonExpr
    | expr op=('&&' | '||') expr                        # booleanExpr
    | 'fun' '(' paramList? ')' '=>'  expr               # funAnonymousPureExpr
    | 'fun' '(' paramList? ')' '{' stmt* '}'            # funAnonymousImpureExpr
    | expr '[' expr ']'                                 # arrayAccessExpr
    | '(' exprList ')'                                  # arrayLiteralExpression
    ;

exprList : expr (',' expr)* ;

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
