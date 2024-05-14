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
 * A pass that checks for invalid array accesses.
 */
public class InvalidArrayAccess extends AnalysisVisitor {

    /**
     * Create a new instance of the {@link InvalidArrayAccess} class.
     */
    @Override
    public void buildVisitor() {
        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
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
        var exprType = TypeUtils.getExprType(arrayAccessExpr.getChild(0), table);
        var arrayType = TypeUtils.getArrayType();

        if (!TypeUtils.areTypesAssignable(exprType, arrayType, table)) {
            var message = "Array access must be done over an array.";
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
