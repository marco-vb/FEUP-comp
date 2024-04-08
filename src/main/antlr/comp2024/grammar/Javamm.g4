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
VARINT      : 'int' '...';
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
    : IMPORT name+=ID (DOT name+=ID)* SEMI          #ImportDeclaration
    ;

classDecl
    : CLASS name=ID (EXTENDS ext=ID)?
      LCURLY varDecl* methodDecl* RCURLY          #ClassDeclaration
    ;

methodDecl locals[boolean isPublic=false, boolean isStatic=false]
    :   (PUBLIC {$isPublic=true;})?
        (STATIC {$isStatic=true;})?
        typename=type name=ID
        arguments=args stmt                         #Method
    ;

type locals[boolean isArray=false]
    : name=INT (LBRACKET RBRACKET {$isArray=true;})?
    | name=VARINT
    | name=BOOLEAN
    | name=VOID
    | name='String' (LBRACKET RBRACKET {$isArray=true;})?
    | name=ID
    ;

args
    : LPAREN (arg (COMMA arg)*)? RPAREN
    ;

arg
    : typename=type name=ID                 #Parameter
    ;

params
    : LPAREN (expr (COMMA expr)*)? RPAREN
    ;

stmt
    : ID EQUALS expr SEMI           #AssignStmt
    | ID LBRACKET expr RBRACKET
      EQUALS expr SEMI              #ArrayAssignStmt
    | IF LPAREN expr RPAREN
      stmt ELSE stmt                #IfElseStmt
    | WHILE LPAREN expr RPAREN
      stmt                          #WhileStmt
    | LCURLY stmt* RCURLY  #ScopeStmt
    | RETURN expr SEMI              #ReturnStmt
    | expr SEMI                     #ExpressionStmt
    ;

varDecl
    : typename=type name=ID SEMI    #Variable
    ;

expr
    : LPAREN expr RPAREN            #ParenExpr
    | NOT expr                      #UnaryExpr
    | expr op=(MUL|DIV) expr        #BinaryExpr
    | expr op=(ADD|SUB) expr        #BinaryExpr
    | expr (LE|LT|GT|GE) expr       #BinaryExpr
    | expr (EQ) expr                #BinaryExpr
    | expr (OR|AND) expr            #BinaryExpr
    | expr DOT ID                   #FuncExpr
    | expr DOT ID LPAREN (expr (COMMA expr)* )? RPAREN
                                    #FuncExpr
    | expr (DOT expr)+              #MemberExpr
    | value=INTEGER                 #IntegerLiteral
    | value=('true' | 'false')      #BooleanLiteral
    | name=ID                       #VarRefExpr
    | expr
      (LBRACKET expr RBRACKET)+     #VarRefExpr
    | LBRACKET (expr (COMMA expr)*)?
      RBRACKET                      #ArrayExpr
    | NEW ID LPAREN RPAREN          #NewExpr
    | THIS                          #ThisExpr
    ;
