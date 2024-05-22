package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.ast.TypeUtils.getExprType;
import static pt.up.fe.comp2024.optimization.OptUtils.toOllirType;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {
    private static final String END_STMT = ";\n";
    private final SymbolTable table;
    private final OllirExprGeneratorVisitor ev;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        ev = new OllirExprGeneratorVisitor(table);
    }

    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECL, this::visitImport);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethod);
        addVisit(ARGUMENT, this::visitMethodArgument);
        addVisit(RETURN_STMT, this::visitReturnStmt);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(FIELD_ASSIGN_STMT, this::visitFieldAssignStmt);
        addVisit(EXPRESSION_STMT, this::visitExpressionStmt);
        addVisit(SCOPE_STMT, this::visitScopeStmt);

        setDefaultVisit(this::defaultVisit);
    }

    /**
     * Default visitor (not used).
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void u) {
        return "";
    }

    private String format(String code) {
        int lvl = 0;

        String[] lines = code.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            if (line.contains("}")) {
                lvl--;
            }
            sb.append("    ".repeat(lvl));
            sb.append(line).append("\n");
            if (line.contains("{")) {
                lvl++;
            }
        }

        return sb.toString();
    }

    /**
     * Visits a program node.
     *
     * @param node
     * @param unused
     * @return
     */
    private String visitProgram(JmmNode node, Void u) {
        StringBuilder code = new StringBuilder();
        node.getChildren().stream().map(this::visit).forEach(code::append);

        String c = format(code.toString());
        System.out.println(c);
        return c;
    }

    private String visitImport(JmmNode node, Void u) {
        List<String> name = node.getObjectAsList("name", String.class);
        return "import " + String.join(".", name) + END_STMT;
    }

    /**
     * Visits a class node. Generates fields, methods and constructor.
     *
     * @param node
     * @param unused
     * @return
     */
    private String visitClass(JmmNode node, Void u) {
        StringBuilder code = new StringBuilder();

        // class name
        code.append(table.getClassName());

        // extends super or defaults to Object
        var superClass = table.getSuper().isEmpty() ? "Object" : table.getSuper();
        code.append(" extends ").append(superClass).append("{\n");
        // init fields as public
        for (var field : table.getFields()) {
            code.append(".field public ").append(field.getName());
            code.append(toOllirType(field.getType())).append(END_STMT);
        }

        // constructor
        code.append(visitConstructor());

        // instantiate methods
        for (var method : node.getChildren()) {
            if (method.getKind().equals("Method")) {
                code.append(visit(method));
            }
        }

        code.append("}");
        return code.toString();
    }

    private String visitConstructor() {
        return ".construct " + table.getClassName() + "().V {\n" + "invokespecial(this, \"<init>\").V;\n}\n";
    }

    private String visitMethod(JmmNode node, Void u) {
        StringBuilder code = new StringBuilder(".method ");

        // add modifiers
        if (node.get("isPublic").equals("true")) {
            code.append("public ");
        } else {
            code.append("private ");
        }

        if (node.get("isStatic").equals("true")) {
            code.append("static ");
        }

        // add name
        code.append(node.get("name"));

        String params = node.getChild(1).getChildren().stream()
                .map(child -> visit(child, null))
                .reduce((a, b) -> a + ", " + b).orElse("");

        code.append("(").append(params).append(")");
        code.append(toOllirType(node.getJmmChild(0))).append(" {\n");

        // add method statements
        for (int i = 2; i < node.getNumChildren(); i++) {
            code.append(visit(node.getJmmChild(i)));
        }

        // If return is void there is no return statement, so we add it
        Type returnType = table.getReturnType(node.get("name"));
        if (returnType.getName().equals("void")) {
            code.append("ret.V;\n");
        }

        code.append("}\n");

        return code.toString();
    }

    private String visitMethodArgument(JmmNode node, Void u) {
        return node.get("name") + toOllirType(node.getJmmChild(0));
    }

    private String visitReturnStmt(JmmNode node, Void u) {
        // return <expr>
        var expr = ev.visit(node.getJmmChild(0));
        // return type (same as method return type)
        Type rType = table.getReturnType(node.getParent().get("name"));

        return expr.getComputation() + "ret" + toOllirType(rType) + " " + expr.getCode() + END_STMT;
    }

    private String visitAssignStmt(JmmNode node, Void u) {
        // <id> = <expr>;
        JmmNode assigned = node.getJmmChild(0);
        JmmNode assignee = node.getJmmChild(1);

        // assignment has type of lhs
        String type = toOllirType(getExprType(assigned, table)) + " ";

        // if variable is field need to getfield
        if (!TypeUtils.isLocal(assigned, table)
                && !TypeUtils.isParam(assigned, table)
                && TypeUtils.isField(assigned, table)) {
            return generateFieldAssignStmt(node);
        }

        OllirExprResult var = ev.visit(assigned);

        // evaluate expression on the right
        OllirExprResult expr = ev.visit(assignee);

        String ret = var.getComputation() + expr.getComputation() + var.getCode() + " :=" + type + expr.getCode();

        if (ret.endsWith(END_STMT)) return ret;
        return ret + END_STMT;
    }

    private String generateFieldAssignStmt(JmmNode node) {
        // this.<field> = <expr>;
        JmmNode field = node.getJmmChild(0);
        JmmNode assignee = node.getJmmChild(1);
        var expr = ev.visit(assignee);


        Type fieldType = null;
        for (var f : table.getFields()) {
            if (f.getName().equals(field.get("name"))) {
                fieldType = f.getType();
            }
        }

        assert fieldType != null;
        String type = toOllirType(fieldType);

        return expr.getComputation() + "putfield(this, " + field.get("name") + type + ", " + expr.getCode() + ").V" + END_STMT;
    }

    private String visitFieldAssignStmt(JmmNode node, Void u) {
        return generateFieldAssignStmt(node);
    }

    private String visitExpressionStmt(JmmNode node, Void u) {
        var expr = ev.visit(node.getJmmChild(0));
        return expr.getComputation();
    }

    private String visitScopeStmt(JmmNode node, Void u) {
        StringBuilder code = new StringBuilder();
        for (var child : node.getChildren()) {
            code.append(visit(child, null));
        }
        return code.toString();
    }
}
