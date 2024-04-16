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

    private JmmNode currentMethod;

    /**
     * Creates a new instance of the {@link TypeError} class.
     */
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);

        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.IF_ELSE_STMT, this::visitIfElseStmt);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
        addVisit(Kind.FUNC_EXPR, this::visitFuncExpr);
    }

    /**
     * Visits a method declaration node and sets the current method.
     * Also checks if the vararg parameter is the last parameter in the method declaration.
     * If it is not, an error report is added.
     *
     * @param method The method declaration node to visit.
     * @param table The symbol table.
     * @return null
     */
    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method;
        System.out.println(method.toTree());

        var arguments = method.getChildren().get(1).getChildren();
        for (var i = 0; i < arguments.size() - 1; i++) {
            var argType = TypeUtils.getExprType(arguments.get(i), table);

            if (argType.isArray()) {
                var message = "Vararg parameter must be last in a method declaration.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(arguments.get(i)),
                        NodeUtils.getColumn(arguments.get(i)),
                        message,
                        null)
                );
            }
        }

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

        if (!indexType.getName().equals(TypeUtils.getIntTypeName())) {
            var message = "Array index must be an integer expression. Found: " + indexType.getName() + ".";
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
        }

        var operator = binaryExpr.get("op");

        var leftTypeName = leftType.getName();
        var rightTypeName = rightType.getName();

        switch(operator) {
            case "+", "-", "*", "/" -> {
                if (!leftTypeName.equals(TypeUtils.getIntTypeName()) || !rightTypeName.equals(TypeUtils.getIntTypeName())) {
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
                if (!leftTypeName.equals(rightTypeName)) {
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
                if (!leftTypeName.equals(TypeUtils.getBooleanTypeName()) || !rightTypeName.equals(TypeUtils.getBooleanTypeName())) {
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

        if (!TypeUtils.areTypesAssignable(leftType, rightType)) {
            var message = "Cannot assign a value of type '" + rightType.getName() + "' to a variable of type '" + leftType.getName() + "'.";
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
                    if (!TypeUtils.areTypesAssignable(arrayElementType, childType)) {
                        var message = "Array elements must be of type '" + arrayElementType.getName() + "'. Found: " + childType.getName() + ".";
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

        if (!conditionType.getName().equals(TypeUtils.getBooleanTypeName())) {
            var message = "If condition must be a boolean expression. Found: " + conditionType.getName() + ".";
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

        if (!conditionType.getName().equals(TypeUtils.getBooleanTypeName())) {
            var message = "While condition must be a boolean expression. Found: " + conditionType.getName() + ".";
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

        // Check if the method returns 'void', in which case it should not return any value
        if (methodReturnType.getName().equals(TypeUtils.getVoidTypeName())) {
            if (returnStmt.getNumChildren() > 0) {
                var message = "Cannot return a value from a method that returns 'void'.";
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

        var returnValue = returnStmt.getChildren().get(0);
        var returnType = TypeUtils.getExprType(returnValue, table);

        if (!TypeUtils.areTypesAssignable(methodReturnType, returnType)) {
            var message = "Cannot return a value of type '" + returnType.getName() + "' from a method that returns '" + methodReturnType.getName() + "'.";
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

        List<Symbol> params;
        if (table.getMethods().contains(methodName)) {
            params = table.getParameters(methodName);
        }
        else {
            return null;
        }

        // Check if last parameter is vararg
        // If it is, the number of arguments must be at least the number of parameters minus 1
        if (params.get(params.size() - 1).getType().isArray()) {
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
        if (params.size() != callArgs.size()) {
            var message = "Method '" + methodName + "' expects " + params.size() + " arguments, but " + callArgs.size() + " were provided.";
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
        for (var i = 0; i < params.size(); i++) {
            var param = params.get(i);

            var paramType = param.getType();
            var argType = TypeUtils.getExprType(callArgs.get(i), table);

            if (!TypeUtils.areTypesAssignable(paramType, argType)) {
                var message = "Argument " + (i + 1) + " of method '" + methodName + "' must be of type '" + paramType.getName() + "'. Found: " + argType.getName() + ".";
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
