package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

/**
 * A pass that checks for invalid array accesses.
 */
public class InvalidArrayAccess extends AnalysisVisitor {

    private JmmNode currentMethod;

    /**
     * Create a new instance of the {@link InvalidArrayAccess} class.
     */
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);

        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
    }

    /**
     * Visits a method declaration node and sets the current method.
     *
     * @param node  The method declaration node.
     * @param table The symbol table.
     * @return null
     */
    private Void visitMethodDecl(JmmNode node, SymbolTable table) {
        currentMethod = node;

        return null;
    }

    /**
     * Visits an array access expression node and checks if the index is within bounds.
     * If the index is out of bounds, a new error report is added.
     *
     * @param arrayAccessExpr The array access expression node.
     * @param table           The symbol table.
     * @return null
     * */
    private Void visitArrayAccessExpr(JmmNode arrayAccessExpr, SymbolTable table) {
        var array = arrayAccessExpr.getChildren().get(0);
        var index = Integer.parseInt(arrayAccessExpr.getChildren().get(1).get("value"));

        // Get all the assign statements in the current method
        var assignStmts = currentMethod.getChildren(Kind.ASSIGN_STMT);

        // Determine the length of the array, if it is initialized
        var arrayLength = 0;
        for (var assignStmt : assignStmts) {
            var identifier = assignStmt.getChildren(Kind.IDENTIFIER).get(0);

            if (identifier.get("name").equals(array.get("name"))) {
                var arrayExpr = assignStmt.getChildren().get(1);

                if (Kind.ARRAY_EXPR.check(arrayExpr)) {

                    // Length of the array is the number of elements in the array expression
                    arrayLength = arrayExpr.getChildren().size();
                }
            }
        }

        // Check if the index is out of bounds
        if (index > arrayLength) {
            var message = "Array index out of bounds: tried to access index " + index + " in an array of length " + arrayLength + ".";
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
}
