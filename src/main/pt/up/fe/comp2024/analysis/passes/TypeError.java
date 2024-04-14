package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

/**
 * A visitor that checks for type errors in the AST.
 */
public class TypeError extends AnalysisVisitor {
    /**
     * Creates a new instance of the {@link TypeError} class.
     */
    @Override
    public void buildVisitor() {
        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.IF_ELSE_STMT, this::visitIfElseStmt);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
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

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
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

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        return null;
    }
}
