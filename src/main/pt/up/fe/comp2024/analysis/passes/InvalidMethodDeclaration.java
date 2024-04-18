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
 * A visitor that checks for invalid method declarations.
 */
public class InvalidMethodDeclaration extends AnalysisVisitor {

    /**
     * Creates a new instance of the {@link InvalidMethodDeclaration} class.
     */
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
    }

    /**
     * Visits a method declaration node and checks the correctness of the method declaration.
     *
     * @param methodDecl The method declaration node to visit.
     * @param table The symbol table.
     * @return null
     */
    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        var returnStmts = methodDecl.getChildren(Kind.RETURN_STMT);
        var returnType = table.getReturnType(methodDecl.get("name"));

        // Check if the main method is correctly declared
        if (methodDecl.get("name").equals("main")) {
            if (!Boolean.parseBoolean(methodDecl.get("isStatic"))) {
                var message = "Main method must be declared as static.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodDecl),
                        NodeUtils.getColumn(methodDecl),
                        message,
                        null
                ));
            }

            if (!returnType.equals(TypeUtils.getVoidType())) {
                var message = "Main method must be declared as void.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodDecl),
                        NodeUtils.getColumn(methodDecl),
                        message,
                        null
                ));
            }

            var parameters = table.getParameters(methodDecl.get("name"));
            if (parameters.size() != 1 || !TypeUtils.getTypeName(parameters.get(0).getType()).equals("String[]")) {
                var message = "Main method must have a single parameter of type String[].";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodDecl),
                        NodeUtils.getColumn(methodDecl),
                        message,
                        null
                ));
            }

        } else if (Boolean.parseBoolean(methodDecl.get("isStatic"))) {  // Other methods should not be declared as static
            var message = "Method '" + methodDecl.get("name") + "' is declared as static but is not the main method.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    message,
                    null
            ));

            return null;
        }

        // Void methods should not have return statements
        if (returnType.getName().equals(TypeUtils.getVoidTypeName())) {
            if (!returnStmts.isEmpty()) {
                var message = "Method '" + methodDecl.get("name") + "' is declared as void but has a return statement.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodDecl),
                        NodeUtils.getColumn(methodDecl),
                        message,
                        null
                ));
            }

            return null;
        }

        // Check if the method has a single return statement
        if (returnStmts.size() > 1) {
            var message = "Method '" + methodDecl.get("name") + "' has more than one return statement.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    message,
                    null
            ));

            return null;

        } else if (returnStmts.isEmpty()) {
            var message = "Method '" + methodDecl.get("name") + "' is declared as '" + returnType.getName() + "' but has no return statement.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    message,
                    null
            ));

            return null;
        }

        // Check if the return statement is last in the method declaration
        var lastStmt = methodDecl.getChildren().get(methodDecl.getChildren().size() - 1);
        if (!lastStmt.equals(returnStmts.get(0))) {
            var message = "Return statement must be the last statement in the method '" + methodDecl.get("name") + "'.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(returnStmts.get(0)),
                    NodeUtils.getColumn(returnStmts.get(0)),
                    message,
                    null
            ));
        }

        return null;
    }
}
