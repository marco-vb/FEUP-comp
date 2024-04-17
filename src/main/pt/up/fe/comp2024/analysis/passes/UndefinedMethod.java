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
 * A pass that checks for undefined methods.
 */
public class UndefinedMethod extends AnalysisVisitor {
    /**
     * Create a new instance of the {@link UndefinedMethod} class.
     */
    @Override
    public void buildVisitor() {
        addVisit(Kind.FUNC_EXPR, this::visitFuncExpr);
    }

    /**
     * Visits a function expression node and checks if the method is defined in the current class or imported.
     * If the method is not defined, a new error report is added.
     *
     * @param funcExpr The function expression node.
     * @param table    The symbol table.
     * @return null
     */
    private Void visitFuncExpr(JmmNode funcExpr, SymbolTable table) {
        var methodName = funcExpr.get("methodname");

        // Check if the method is defined in the current class
        if (table.getMethods().contains(methodName)) {
            return null;
        }

        var caller = funcExpr.getChildren().get(0);
        var callerType = TypeUtils.getExprType(caller, table);

        var imports = table.getImports();
        var superClass = table.getSuper();

        // Check if the class extends an imported class
        if (superClass != null && imports.contains(superClass)) {
            return null;
        }

        // Check if the method is defined in an imported class
        if (imports.contains(callerType.getName())) {
            return null;
        }

        var message = "Method '" + methodName + "' is not defined.";
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(funcExpr),
                NodeUtils.getColumn(funcExpr),
                message,
                null
        ));

        return null;
    }
}