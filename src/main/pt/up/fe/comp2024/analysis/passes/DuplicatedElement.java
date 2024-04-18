package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

/**
 * A visitor that checks for duplicated elements in the AST.
 */
public class DuplicatedElement extends AnalysisVisitor {

    /**
     * Creates a new instance of the {@link DuplicatedElement} class.
     */
    @Override
    public void buildVisitor() {
        addVisit(Kind.PROGRAM, this::visitProgram);
        addVisit(Kind.CLASS_DECL, this::visitClassDecl);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
    }

    /**
     * Visits a program node and checks for duplicated imports.
     * If there are duplicated imports, an error report is added.
     *
     * @param program The program node to visit.
     * @param table   The symbol table.
     * @return null.
     */
    private Void visitProgram(JmmNode program, SymbolTable table) {
        var imports = program.getChildren(Kind.IMPORT_DECL);

        // Check if there are duplicated imports
        for (int i = 0; i < imports.size(); i++) {
            for (int j = i + 1; j < imports.size(); j++) {
                var import1 = imports.get(i);
                var import2 = imports.get(j);

                if (import1.get("ID").equals(import2.get("ID"))) {
                    var message = "Duplicated import '" + import1.get("ID") + "'.";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(import2),
                            NodeUtils.getColumn(import2),
                            message,
                            null)
                    );

                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Visits a class declaration node and checks for duplicated fields and methods.
     * If there are duplicated fields or methods, an error report is added.
     *
     * @param classDecl The class declaration
     * @param table The symbol table
     * @return null
     */
    private Void visitClassDecl(JmmNode classDecl, SymbolTable table) {
        var fields = classDecl.getChildren(Kind.VAR_DECL);
        var methods = classDecl.getChildren(Kind.METHOD_DECL);

        // Check if there are duplicated fields
        for (int i = 0; i < fields.size(); i++) {
            for (int j = i + 1; j < fields.size(); j++) {
                var field1 = fields.get(i);
                var field2 = fields.get(j);

                if (field1.get("name").equals(field2.get("name"))) {
                    var message = "Duplicated field name '" + field1.get("name") + "'.";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(field2),
                            NodeUtils.getColumn(field2),
                            message,
                            null)
                    );

                    return null;
                }
            }
        }

        // Check if there are duplicated methods
        for (int i = 0; i < methods.size(); i++) {
            for (int j = i + 1; j < methods.size(); j++) {
                var method1 = methods.get(i);
                var method2 = methods.get(j);

                if (method1.get("name").equals(method2.get("name"))) {
                    var message = "Duplicated method name '" + method1.get("name") + "'.";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(method2),
                            NodeUtils.getColumn(method2),
                            message,
                            null)
                    );

                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Visits a method declaration node and checks for duplicated parameters and local variables.
     * If there are duplicated parameters or local variables, an error report is added.
     *
     * @param methodDecl The method declaration
     * @param table The symbol table
     * @return null
     */
    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        var parameters = methodDecl.getChild(1).getChildren(Kind.ARGUMENT);
        var localVariables = methodDecl.getChildren(Kind.VAR_DECL);

        // Check if there are duplicated parameters
        for (int i = 0; i < parameters.size(); i++) {
            for (int j = i + 1; j < parameters.size(); j++) {
                var parameter1 = parameters.get(i);
                var parameter2 = parameters.get(j);

                if (parameter1.get("name").equals(parameter2.get("name"))) {
                    var message = "Duplicated parameter name '" + parameter1.get("name") + "' in method " + methodDecl.get("name") + "'.";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(parameter2),
                            NodeUtils.getColumn(parameter2),
                            message,
                            null)
                    );

                    return null;
                }
            }


        }

        // Check if there are duplicated local variables
        for (int i = 0; i < localVariables.size(); i++) {
            for (int j = i + 1; j < localVariables.size(); j++) {
                var localVariable1 = localVariables.get(i);
                var localVariable2 = localVariables.get(j);

                if (localVariable1.get("name").equals(localVariable2.get("name"))) {
                    var message = "Duplicated local variable name '" + localVariable1.get("name") + " in method " + methodDecl.get("name") + "'.";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(localVariable2),
                            NodeUtils.getColumn(localVariable2),
                            message,
                            null)
                    );

                    return null;
                }
            }
        }

        return null;
    }
}
