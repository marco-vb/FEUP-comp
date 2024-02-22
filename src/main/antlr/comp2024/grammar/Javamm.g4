grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LBRACKET : '[';
RBRACKET : ']';
MUL : '*' ;
ADD : '+' ;
DIV : '/';
SUB : '-';
DOT : '.' ;
COMMA : ',';
OR : '||' ;
AND : '&&' ;
LT : '<' ;
GT : '>' ;
LE : '<=' ;
GE : '>=' ;
EQ : '==' ;

CLASS : 'class' ;
INT : 'int' ;
STRING : 'String' ;
STATIC : 'static' ;
VOID : 'void' ;
BOOLEAN : 'boolean' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
IMPORT : 'import';
EXTENDS : 'extends';
IF : 'if';
ELSE : 'else';
WHILE : 'while';
NEW : 'new' ;
TRUE : 'true' ;
FALSE : 'false' ;
NOT : '!' ;

INTEGER : '0' | [1-9][0-9]* ;
ID : [a-zA-Z][a-zA-Z0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : importDecl* classDecl EOF
    ;

importDecl
    : IMPORT ID (DOT ID)* SEMI
    ;

classDecl
    : CLASS name=ID (EXTENDS ID)?
      LCURLY
      varDecl*
      methodDecl*
      RCURLY
    ;

varDecl
    : type name=ID SEMI
    ;

type
    : name= INT
    | name= INT DOT DOT DOT
    | name= BOOLEAN
    | name= ID
    | name= STRING
    | name= VOID
    | type LBRACKET RBRACKET
    ;

methodDecl locals[boolean isPublic=false]
    :   (PUBLIC {$isPublic=true;})?
        STATIC?
        type name=ID
        args
        LCURLY varDecl* stmt* RCURLY
    ;

args
    : LPAREN (param (COMMA param)*)? RPAREN
    ;

param
    : type name=ID
    | expr
    ;

stmt
    : var=ID (LBRACKET expr? RBRACKET)?
      EQUALS expr SEMI              #AssignStmt
    | IF LPAREN expr RPAREN
      stmt (ELSE stmt)?             #IfElseStmt
    | WHILE LPAREN expr RPAREN
      stmt                          #WhileStmt
    | LCURLY stmt* RCURLY           #ScopeStmt
    | RETURN expr SEMI              #ReturnStmt
    | expr SEMI                     #ExpressionStmt
    ;

expr
    : LPAREN expr RPAREN            #ParenExpr
    | expr (DOT expr args?)* args   #FuncExpr
    | expr (DOT expr)+              #MemberExpr
    | expr op=(MUL|DIV) expr        #BinaryExpr
    | expr op=(ADD|SUB) expr        #BinaryExpr
    | value=INTEGER                 #IntegerLiteral
    | expr (OR|AND) expr            #BooleanExpr
    | expr (LE|LT|GT|GE|EQ) expr    #BooleanExpr
    | NOT expr                      #BooleanExpr
    | value=(TRUE | FALSE)          #BooleanLiteral
    | name=ID                       #VarRefExpr
    | expr
      (LBRACKET expr RBRACKET)+     #VarRefExpr
    | LBRACKET expr (COMMA expr)*
      RBRACKET                      #ArrayExpr
    | NEW type? expr                #NewExpr
    ;



