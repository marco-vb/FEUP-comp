package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

/**
 * A pass that checks for the use of 'this' in static methods.
 */
public class ThisInStaticMethod extends AnalysisVisitor {
    /**
     * Create a new instance of the {@link ThisInStaticMethod} class.
     */
    @Override
    public void buildVisitor() {
        addVisit(Kind.THIS, this::visitThis);
    }

    /**
     * Visits a 'this' node and checks if it is used in a static method.
     * If it is used in a static method, a new error report is added.
     *
     * @param thisExpr The 'this' node.
     * @param table    The symbol table.
     * @return null
     */
    private Void visitThis(JmmNode thisExpr, SymbolTable table) {
        var currentMethod = thisExpr.getAncestor(Kind.METHOD_DECL).orElseThrow();
        var isStatic = Boolean.parseBoolean(currentMethod.get("isStatic"));

        if (isStatic) {
            var message = "Cannot use 'this' in a static method.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(thisExpr),
                    NodeUtils.getColumn(thisExpr),
                    message,
                    null
            ));
        }

        return null;
    }
}
