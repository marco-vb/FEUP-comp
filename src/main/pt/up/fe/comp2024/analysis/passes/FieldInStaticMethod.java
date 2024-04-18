package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;


/**
 * A visitor that checks if a field is being accessed from a static method.
 */
public class FieldInStaticMethod extends AnalysisVisitor {

    private JmmNode currentMethod;

    /**
     * Creates a new instance of the {@link FieldInStaticMethod} class.
     */
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(Kind.IDENTIFIER, this::visitIdentifier);
    }

    /**
     * Visits a method declaration node and sets the current method.
     *
     * @param method The method declaration node.
     * @param table  The symbol table.
     * @return null
     */
    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method;

        return null;
    }

    /**
     * Visits a variable reference expression node and checks if the field is being accessed from a static method.
     * If it is, an error is added to the reports.
     *
     * @param varRef The variable reference expression node.
     * @param table  The symbol table.
     * @return null
     */
    private Void visitVarRefExpr(JmmNode varRef, SymbolTable table) {
        if (currentMethod.get("isStatic").equals("false")) {
            return null;
        }

        if (isField(varRef, table)) {
            var message = "Field '" + varRef.get("name") + "' cannot be accessed from a static method.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(varRef),
                    NodeUtils.getColumn(varRef),
                    message,
                    null)
            );
        }

        return null;
    }

    /**
     * Visits an identifier node and checks if the field is being accessed from a static method.
     * If it is, an error is added to the reports.
     *
     * @param identifier The identifier node.
     * @param table      The symbol table.
     * @return null
     */
    private Void visitIdentifier(JmmNode identifier, SymbolTable table) {
        if (currentMethod.get("isStatic").equals("false")) {
            return null;
        }

        if (isField(identifier, table)) {
            var message = "Field '" + identifier.get("name") + "' cannot be accessed from a static method.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(identifier),
                    NodeUtils.getColumn(identifier),
                    message,
                    null)
            );
        }

        return null;
    }

    /**
     * A helper method that checks if a variable reference node is a field.
     *
     * @param node  The variable reference node.
     * @param table The symbol table.
     * @return True if the variable reference node is a field, false otherwise.
     */
    private Boolean isField(JmmNode node, SymbolTable table) {
        var varName = node.get("name");
        var fields = table.getFields();
        var params = table.getParameters(currentMethod.get("name"));
        var locals = table.getLocalVariables(currentMethod.get("name"));

        for (var local : locals) {
            if (local.getName().equals(varName)) {
                return false;
            }
        }

        for (var param : params) {
            if (param.getName().equals(varName)) {
                return false;
            }
        }

        for (var field : fields) {
            if (field.getName().equals(varName)) {
                return true;
            }
        }

        return false;
    }
}
