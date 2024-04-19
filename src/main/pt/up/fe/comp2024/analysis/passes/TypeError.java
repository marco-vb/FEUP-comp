package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;

/**
 * A visitor that checks for type errors in the AST.
 */
public class TypeError extends AnalysisVisitor {

    private JmmNode currentClass;
    private JmmNode currentMethod;


    /**
     * Creates a new instance of the {@link TypeError} class.
     */
    @Override
    public void buildVisitor() {
        addVisit(Kind.CLASS_DECL, this::visitClassDecl);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);

        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);
        addVisit(Kind.IF_ELSE_STMT, this::visitIfElseStmt);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
        addVisit(Kind.FUNC_EXPR, this::visitFuncExpr);
    }

    /**
     * Visits a class declaration
     *
     * @param classDecl The class declaration
     * @param table The symbol table
     */
    private Void visitClassDecl(JmmNode classDecl, SymbolTable table) {
        currentClass = classDecl;

        return null;
    }

    /**
     * Visits a method declaration node and sets the current method.
     *
     * @param method The method declaration node to visit.
     * @param table The symbol table.
     * @return null
     */
    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method;

        return null;
    }

    /**
     * Visits an array access expression node and checks if the index is an integer expression.
     * If it is not, an error report is added.
     *
     * @param arrayAccessExpr The array access expression node to visit.
     * @param table The symbol table.
     * @return null
     */
    private Void visitArrayAccessExpr(JmmNode arrayAccessExpr, SymbolTable table) {
        var index = arrayAccessExpr.getChildren().get(1);

        var indexType = TypeUtils.getExprType(index, table);

        if (!TypeUtils.areTypesAssignable(indexType, TypeUtils.getIntType(), table)) {
            var message = "Array index must be an integer expression. Found: " + TypeUtils.getTypeName(indexType) + ".";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAccessExpr),
                    NodeUtils.getColumn(arrayAccessExpr),
                    message,
                    null)
            );
        }

        return null;
    }

    /**
     * Visits a binary expression node and checks if the types of the operands are compatible with the operator.
     * If they are not, an error report is added.
     *
     * @param binaryExpr The binary expression node to visit.
     * @param table The symbol table.
     * @return null
     */
    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        var left = binaryExpr.getChildren().get(0);
        var right = binaryExpr.getChildren().get(1);

        var leftType = TypeUtils.getExprType(left, table);
        var rightType = TypeUtils.getExprType(right, table);

        // Check if any of the operands is an array, as they are not supported in binary expressions
        if (leftType.isArray() || rightType.isArray()) {
            var message = "Array types are not supported in binary expressions.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(binaryExpr),
                    NodeUtils.getColumn(binaryExpr),
                    message,
                    null)
            );

            return null;
        }

        var operator = binaryExpr.get("op");

        var leftTypeName = TypeUtils.getTypeName(leftType);
        var rightTypeName = TypeUtils.getTypeName(rightType);

        switch(operator) {
            case "+", "-", "*", "/" -> {
                if (!TypeUtils.areTypesAssignable(leftType, TypeUtils.getIntType(), table) || !TypeUtils.areTypesAssignable(rightType, TypeUtils.getIntType(), table)) {
                    var message = "Binary operator '" + operator + "' requires both operands to be of type 'int'. Found: " + leftTypeName + " and " + rightTypeName + ".";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(binaryExpr),
                            NodeUtils.getColumn(binaryExpr),
                            message,
                            null)
                    );
                }
            }
            case "<", "<=", ">", ">=", "==", "!=" -> {
                if (!TypeUtils.areTypesAssignable(leftType, rightType, table)) {
                    var message = "Binary operator '" + operator + "' requires both operands to be of the same type. Found: " + leftTypeName + " and " + rightTypeName + ".";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(binaryExpr),
                            NodeUtils.getColumn(binaryExpr),
                            message,
                            null)
                    );
                }
            }
            case "&&", "||" -> {
                if (!TypeUtils.areTypesAssignable(leftType, TypeUtils.getBooleanType(), table) || !TypeUtils.areTypesAssignable(rightType, TypeUtils.getBooleanType(), table)) {
                    var message = "Binary operator '" + operator + "' requires both operands to be of type 'bool'. Found: " + leftTypeName + " and " + rightTypeName + ".";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(binaryExpr),
                            NodeUtils.getColumn(binaryExpr),
                            message,
                            null)
                    );
                }
            }
        }

        return null;
    }

    /**
     * Visits an assignment statement node and checks if the assigned value is compatible with the variable type.
     * If they are not, an error report is added.
     *
     * @param assignStmt The assignment statement node to visit.
     * @param table The symbol table.
     * @return null
     */
    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        var left = assignStmt.getChildren().get(0);
        var right = assignStmt.getChildren().get(1);

        var leftType = TypeUtils.getExprType(left, table);
        var rightType = TypeUtils.getExprType(right, table);

        if (!TypeUtils.areTypesAssignable(rightType, leftType, table)) {
            var message = "Cannot assign a value of type '" + TypeUtils.getTypeName(rightType) + "' to a variable of type '" + TypeUtils.getTypeName(leftType) + "'.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(assignStmt),
                    NodeUtils.getColumn(assignStmt),
                    message,
                    null)
            );

            return null;
        }

        // Check if both sides are arrays, and if so, check if the array elements are of the correct type
        if (leftType.isArray() && rightType.isArray()) {
            if (Kind.ARRAY_EXPR.check(right)) {
                for (var child : right.getChildren()) {
                    var childType = TypeUtils.getExprType(child, table);

                    var arrayElementType = new Type(leftType.getName(), false);
                    if (!TypeUtils.areTypesAssignable(arrayElementType, childType, table)) {
                        var message = "Array elements must be of type '" + TypeUtils.getTypeName(arrayElementType) + "'. Found: " + TypeUtils.getTypeName(childType) + ".";
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(assignStmt),
                                NodeUtils.getColumn(assignStmt),
                                message,
                                null)
                        );
                    }
                }

            }
        }

        return null;
    }

    /**
     * Visits an array assignment statement node and performs the following checks:
     * - The identifier must be an array variable.
     * - The index must be an integer expression.
     * - The value must be compatible with the array type.
     * If any of these conditions are not met, an error report is added.
     *
     * @param arrayAssignStmt The array assignment statement node to visit.
     * @param table The symbol table.
     * @return null
     */
    private Void visitArrayAssignStmt(JmmNode arrayAssignStmt, SymbolTable table) {
        System.out.println(arrayAssignStmt.toTree());

        var identifier = arrayAssignStmt.getChildren().get(0);
        var index = arrayAssignStmt.getChildren().get(1);
        var value = arrayAssignStmt.getChildren().get(2);

        var identifierType = TypeUtils.getExprType(identifier, table);
        var indexType = TypeUtils.getExprType(index, table);
        var valueType = TypeUtils.getExprType(value, table);

        // Check if the identifier is an array variable
        if (!identifierType.isArray()) {
            var message = "Array assignment must be done to an array variable. Found: " + TypeUtils.getTypeName(identifierType) + ".";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAssignStmt),
                    NodeUtils.getColumn(arrayAssignStmt),
                    message,
                    null)
            );
        }

        // Check if the index is an integer expression
        if (!TypeUtils.areTypesAssignable(indexType, TypeUtils.getIntType(), table)) {
            var message = "Array index must be an integer expression. Found: " + TypeUtils.getTypeName(indexType) + ".";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAssignStmt),
                    NodeUtils.getColumn(arrayAssignStmt),
                    message,
                    null)
            );
        }

        // Check if the value is compatible with the array type
        if (!TypeUtils.areTypesAssignable(valueType, new Type(identifierType.getName(), false), table)) {
            var message = "Cannot assign a value of type '" + TypeUtils.getTypeName(valueType) + "' to an array of type '" + identifierType.getName() + "'.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAssignStmt),
                    NodeUtils.getColumn(arrayAssignStmt),
                    message,
                    null)
            );
        }

        return null;
    }

    /**
     * Visits an if-else statement node and checks if the condition is a boolean expression.
     * If it is not, an error report is added.
     *
     * @param ifStmt The if-else statement node to visit.
     * @param table The symbol table.
     * @return null
     */
    private Void visitIfElseStmt(JmmNode ifStmt, SymbolTable table) {
        var condition = ifStmt.getChildren().get(0);

        var conditionType = TypeUtils.getExprType(condition, table);

        if (!TypeUtils.areTypesAssignable(conditionType, TypeUtils.getBooleanType(), table)) {
            var message = "If condition must be a boolean expression. Found: " + TypeUtils.getTypeName(conditionType) + ".";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(ifStmt),
                    NodeUtils.getColumn(ifStmt),
                    message,
                    null)
            );
        }

        return null;
    }

    /**
     * Visits a while statement node and checks if the condition is a boolean expression.
     * If it is not, an error report is added.
     *
     * @param whileStmt The while statement node to visit.
     * @param table The symbol table.
     * @return null
     */
    private Void visitWhileStmt(JmmNode whileStmt, SymbolTable table) {
        var condition = whileStmt.getChildren().get(0);

        var conditionType = TypeUtils.getExprType(condition, table);

        if (!TypeUtils.areTypesAssignable(conditionType, TypeUtils.getBooleanType(), table)) {
            var message = "While condition must be a boolean expression. Found: " + TypeUtils.getTypeName(conditionType) + ".";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(whileStmt),
                    NodeUtils.getColumn(whileStmt),
                    message,
                    null)
            );
        }

        return null;
    }

    /**
     * Visits a return statement node and checks if the returned value is compatible with the method return type.
     * If they are not, an error report is added.
     *
     * @param returnStmt The return statement node to visit.
     * @param table The symbol table.
     * @return null
     */
    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        var methodReturnType = table.getReturnType(currentMethod.get("name"));

        var returnValue = returnStmt.getChildren().get(0);
        var returnType = TypeUtils.getExprType(returnValue, table);

        if (returnType == null) {
            return null;
        }

        if (!TypeUtils.areTypesAssignable(methodReturnType, returnType, table)) {
            var message = "Cannot return a value of type '" + TypeUtils.getTypeName(returnType) + "' from a method that returns '" + TypeUtils.getTypeName(methodReturnType) + "'.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(returnStmt),
                    NodeUtils.getColumn(returnStmt),
                    message,
                    null)
            );
        }

        return null;
    }

    /**
     * Visits a function expression node and checks if the arguments are compatible with the method parameters.
     * If they are not, an error report is added.
     *
     * @param funcExpr The function expression node to visit.
     * @param table The symbol table.
     * @return null
     */
    public Void visitFuncExpr(JmmNode funcExpr, SymbolTable table) {
        var methodName = funcExpr.get("methodname");
        var callArgs = funcExpr.getChildren().subList(1, funcExpr.getNumChildren());

        if (methodName.equals("length")) {
            return null;
        }

        List<JmmNode> params;
        List<Symbol> tableParams;
        if (table.getMethods().contains(methodName)) {
            params = TypeUtils.getMethodParams(methodName, currentClass);
            tableParams = table.getParameters(methodName);
        }
        else {
            return null;
        }

        // Check if last parameter is vararg
        // If it is, the number of arguments must be at least the number of parameters minus 1
        if (!params.isEmpty() && params.get(params.size() - 1).getChild(0).get("isVarargs").equals("true")) {
            if (params.size() > callArgs.size()) {
                var message = "Method '" + methodName + "' expects at least " + (params.size() - 1) + " arguments, but " + callArgs.size() + " were provided.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(funcExpr),
                        NodeUtils.getColumn(funcExpr),
                        message,
                        null)
                );
            }

            return null;
        }

        // Check if the number of arguments is correct
        if (tableParams.size() != callArgs.size()) {
            var message = "Method '" + methodName + "' expects " + tableParams.size() + " arguments, but " + callArgs.size() + " were provided.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(funcExpr),
                    NodeUtils.getColumn(funcExpr),
                    message,
                    null)
            );
            return null;
        }

        // Check if the arguments are of the correct type
        for (var i = 0; i < tableParams.size(); i++) {
            var param = tableParams.get(i);

            var paramType = param.getType();
            var argType = TypeUtils.getExprType(callArgs.get(i), table);

            if (!TypeUtils.areTypesAssignable(paramType, argType, table)) {
                var message = "Argument " + (i + 1) + " of method '" + methodName + "' must be of type '" + TypeUtils.getTypeName(paramType) + "'. Found: " + argType.getName() + ".";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(callArgs.get(i)),
                        NodeUtils.getColumn(callArgs.get(i)),
                        message,
                        null)
                );
            }
        }

        return null;
    }
}
