grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

// keywords only
IMPORT      : 'import';
PUBLIC      : 'public';
STATIC      : 'static';
CLASS       : 'class';
EXTENDS     : 'extends';
RETURN      : 'return';
NEW         : 'new';
VOID        : 'void';
BOOLEAN     : 'boolean';
ELLIPSIS    : '...';
INT         : 'int';
IF          : 'if';
ELSE        : 'else';
WHILE       : 'while';
THIS        : 'this';

WS          : [ \t\n\r\f]+ -> skip;
SINGLE_LINE_COMMENT : '//' .*? '\n' -> skip;
MULTI_LINE_COMMENT  : '/*' .*? '*/' -> skip;

INTEGER     : '0' | [1-9][0-9]*;
ID          : [a-zA-Z_$][a-zA-Z0-9_$]*;

program
    : importDecl* classDecl EOF
    ;

importDecl
    : IMPORT name+=ID
      ('.' name+=ID)* ';'               #ImportDeclaration
    ;

classDecl
    : CLASS name=ID (EXTENDS ext=ID)?
      '{' varDecl* methodDecl* '}'      #ClassDeclaration
    ;

methodDecl locals[boolean isPublic=false, boolean isStatic=false]
    : (PUBLIC {$isPublic=true;})?
      (STATIC {$isStatic=true;})?
      typename=type name=ID
      arguments=args
      '{' varDecl* stmt* '}'            #Method
    ;

type locals[boolean isArray=false]
    : name=INT ('[' ']' {$isArray=true;})?
    | name=INT (ELLIPSIS {$isArray=true;})?
    | name=BOOLEAN
    | name=VOID
    | name='String' ('[' ']' {$isArray=true;})?
    | name=ID
    ;

args
    : '(' (arg (',' arg)*)? ')'         #Arguments
    ;

arg
    : typename=type name=ID             #Argument
    ;

stmt
    : name=ID '=' expr ';'              #AssignStmt
    | name=ID '[' expr ']' '=' expr ';' #ArrayAssignStmt
    | IF '(' expr ')' stmt ELSE stmt    #IfElseStmt
    | WHILE '(' expr ')' stmt           #WhileStmt
    | '{' stmt* '}'                     #ScopeStmt
    | RETURN expr ';'                   #ReturnStmt
    | expr ';'                          #ExpressionStmt
    ;

varDecl
    : typename=type name=ID ';'         #Variable
    ;

expr
    : '(' expr ')'                      #ParenExpr
    | '!' expr                          #UnaryExpr
    | expr op=('*' | '/') expr          #BinaryExpr
    | expr op=('+' | '-') expr          #BinaryExpr
    | expr ('<=' | '<' | '>' | '>=')
      expr                              #BinaryExpr
    | expr ('==') expr                  #BinaryExpr
    | expr ('||' | '&&') expr           #BinaryExpr
    | expr '.' name=ID                  #FuncExpr
    | expr '.' name=ID
      '(' (expr (',' expr)* )? ')'      #FuncExpr
    | expr ('.' expr)+                  #MemberExpr
    | value=INTEGER                     #IntegerLiteral
    | value=('true' | 'false')          #BooleanLiteral
    | name=ID                           #VarRefExpr
    | expr ('[' expr ']')+              #VarRefExpr
    | '[' (expr (',' expr)*)? ']'       #ArrayExpr
    | NEW ID '(' ')'                    #NewExpr
    | NEW INT '[' expr ']'              #NewArrayExpr
    | THIS                              #ThisExpr
    ;


