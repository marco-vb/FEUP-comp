grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

LPAREN      : '(' ;
RPAREN      : ')' ;
LCURLY      : '{' ;
RCURLY      : '}' ;
LBRACKET    : '[';
RBRACKET    : ']';
EQUALS      : '=';
SEMI        : ';' ;
DOT         : '.' ;
COMMA       : ',';    
MUL         : '*' ;
ADD         : '+' ;
DIV         : '/';
SUB         : '-';
NOT         : '!' ;
AND         : '&&' ;
OR          : '||' ;
LT          : '<' ;
GT          : '>' ;
LE          : '<=' ;
GE          : '>=' ;
EQ          : '==' ;

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
INT         : 'int';
IF          : 'if';
ELSE        : 'else';
WHILE       : 'while';
THIS        : 'this';

INTEGER     : '0' | [1-9][0-9]*;
ID          : [a-zA-Z][a-zA-Z0-9]*;
WS          : [ \t\n\r\f]+ -> skip;

program
    : importDecl* classDecl EOF
    ;

importDecl
    : IMPORT name+=ID (DOT name+=ID)* SEMI          #ImportDeclaration
    ;

classDecl
    : CLASS name=ID (EXTENDS ext=ID)?
      LCURLY varDecl* methodDecl* RCURLY            #ClassDeclaration
    ;

methodDecl locals[boolean isPublic=false]
    :   (PUBLIC {$isPublic=true;})?
        STATIC? typename=type name=ID
        arguments=args stmt                         #Method
    ;

type locals[boolean isArray=false]
    : name=INT (LBRACKET RBRACKET {$isArray=true;})?
    | name=BOOLEAN
    | name=VOID
    | name='String' (LBRACKET RBRACKET {$isArray=true;})?
    | name=ID
    ;

args
    : LPAREN (param (COMMA param)*)? RPAREN
    | LPAREN (expr (COMMA expr)*)? RPAREN
    ;

param
    : typename=type name=ID                 #Parameter
    | typename=varint name=ID               #Varargs
    ;

varint
    : INT DOT DOT DOT
    ;

stmt
    : expr EQUALS expr SEMI         #AssignStmt
    | IF expr stmt ELSE stmt        #IfElseStmt
    | WHILE expr stmt               #WhileStmt
    | LCURLY stmt* RCURLY           #ScopeStmt
    | RETURN expr SEMI              #ReturnStmt
    | expr SEMI                     #ExpressionStmt
    | varDecl                       #VarDeclStmt
    | SEMI                          #EmptyStmt
    ;

varDecl
    : typename=type name=ID SEMI    #Variable
    ;

expr
    : LPAREN expr RPAREN            #ParenExpr
    | NOT expr                      #BooleanExpr
    | expr op=(MUL|DIV) expr        #BinaryExpr
    | expr op=(ADD|SUB) expr        #BinaryExpr
    | expr (LE|LT|GT|GE) expr       #BooleanExpr
    | expr (EQ) expr                #BooleanExpr
    | expr (OR|AND) expr            #BooleanExpr
    | expr (DOT expr args?)* args   #FuncExpr
    | expr (DOT expr)+              #MemberExpr
    | value=INTEGER                 #IntegerLiteral
    | value=('true' | 'false')      #BooleanLiteral
    | name=ID                       #VarRefExpr
    | expr
      (LBRACKET expr RBRACKET)+     #VarRefExpr
    | LBRACKET (expr (COMMA expr)*)?
      RBRACKET                      #ArrayExpr
    | NEW type? expr                #NewExpr
    | THIS                          #ThisExpr
    ;
