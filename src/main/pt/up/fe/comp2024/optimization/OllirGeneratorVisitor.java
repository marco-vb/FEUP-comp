package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.ast.TypeUtils.getExprType;
import static pt.up.fe.comp2024.optimization.OptUtils.toOllirType;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private static final String TAB = "    ";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";

    private static ArrayList<String> indent = new ArrayList<>();
    private final SymbolTable table;
    private final OllirExprGeneratorVisitor exprVisitor;

    private String indentation() {
        return indent.stream().reduce("", String::concat);
    }

    private void removeIndent() {
        indent.remove(indent.size() - 1);
    }

    private void addIndent() {
        indent.add(TAB);
    }

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.table.print();
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }

    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECL, this::visitImport);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethod);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(EXPRESSION_STMT, this::visitExpressionStmt);
        addVisit(VAR_DECL, this::visitVariableDecl);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitVariableDecl(JmmNode jmmNode, Void unused) {
        // do nothing when a variable is declared. Just here to avoid default visit.
        return "";
    }

    /**
     * Default visitor (not used).
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {
        System.out.println("Default visit used in OllirGeneratorVisitor: " + node.getKind());
        return "";
    }

    /**
     * Visits a program node.
     *
     * @param node
     * @param unused
     * @return
     */
    private String visitProgram(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        node.getChildren().stream().map(this::visit).forEach(code::append);

        return code.toString();
    }


    private String visitImport(JmmNode jmmNode, Void unused) {
        List<String> name = jmmNode.getObjectAsList("name", String.class);
        return "import " + String.join(".", name) + END_STMT;
    }

    /**
     * Visits a class node. Generates fields, methods and constructor.
     * @param node
     * @param unused
     * @return
     */
    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // class name
        code.append(table.getClassName());

        // extends super or defaults to Object
        var superClass = table.getSuper().isEmpty() ? "Object" : table.getSuper();
        code.append(" extends ").append(superClass).append(L_BRACKET);

        addIndent();

        // init fields as public
        for (var field : table.getFields()) {
            code.append(indentation());
            code.append(".field public ").append(field.getName());
            code.append(toOllirType(field.getType())).append(END_STMT);
        }

        // constructor
        code.append(NL);
        code.append(indentation()).append(buildConstructor()).append(NL);

        // instantiate methods
        for (var method : node.getChildren()) {
            if (method.getKind().equals("Method")) {
                code.append(indentation()).append(visit(method));
            }
        }

        code.append(R_BRACKET);
        removeIndent();

        return code.toString();
    }

    /**
     * Builds the constructor for the class.
     * @return
     */
    private String buildConstructor() {
        StringBuilder code = new StringBuilder(".construct ");
        code.append(table.getClassName()).append("().V").append(L_BRACKET);

        addIndent();

        code.append(indentation()).append("invokespecial(this, \"<init>\").V");
        code.append(END_STMT);

        removeIndent();
        code.append(indentation()).append(R_BRACKET);

        return code.toString();
    }

    /**
     * Visits a method node. Generates method signature and statements.
     * @param node
     * @param unused
     * @return
     */
    private String visitMethod(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");

        // add modifiers
        if (node.get("isPublic").equals("true")){
            code.append("public ");
        }
        if (node.get("isStatic").equals("true")){
            code.append("static ");
        }

        // add name
        code.append(node.get("name"));

        var params = node.getChild(1).getChildren().stream()
                .map(child -> generateMethodParam(child, null))
                .reduce((a, b) -> a + ", " + b).orElse("");

        code.append("(").append(params).append(")");
        code.append(toOllirType(node.getJmmChild(0))).append(L_BRACKET);

        addIndent();

        // add method statements
        for (int i = 2; i < node.getNumChildren(); i++) {
            var childCode = visit(node.getJmmChild(i));
            if (OptUtils.notEmptyWS(childCode)) {
                code.append(indentation()).append(childCode);
            }
        }

        var returnType = table.getReturnType(node.get("name"));

        // If return is void there is no return statement, so we add it
        if (returnType.getName().equals("void")) {
            code.append(indentation()).append("ret.V").append(END_STMT);
        }

        removeIndent();
        code.append(indentation()).append(R_BRACKET);

        return code.toString();
    }

    /**
     * Visits an assignment statement node.
     * @param node
     * @param unused
     * @return
     */
    private String visitAssignStmt(JmmNode node, Void unused) {
        JmmNode assigned = node.getJmmChild(0);
        JmmNode assignee = node.getJmmChild(1);

        OllirExprResult lhs = exprVisitor.visit(assigned);
        OllirExprResult rhs = exprVisitor.visit(assignee);

        StringBuilder code = new StringBuilder();

        // assignment has type of lhs
        String assignedType = toOllirType(getExprType(assigned, table));

        // if lhs is a field, we use putfield
        if (TypeUtils.isField(assigned, table)) {
            return generatePutField(rhs, code, assigned, assignedType);
        }

        // else we generate a normal assignment
        return generateAssignment(lhs, code, rhs, assignedType);
    }

    private String generateAssignment(OllirExprResult lhs, StringBuilder code, OllirExprResult rhs, String assignedType) {
        if (OptUtils.notEmptyWS(lhs.getComputation())) {
            code.append(lhs.getComputation()).append(END_STMT).append(indentation());
        }
        if (OptUtils.notEmptyWS(rhs.getComputation())) {
            code.append(rhs.getComputation()).append(END_STMT).append(indentation());
        }

        code.append(lhs.getCode());
        code.append(" :=").append(assignedType).append(" ");
        code.append(rhs.getCode()).append(END_STMT);

        return code.toString();
    }

    private String generatePutField(OllirExprResult rhs, StringBuilder code, JmmNode assigned, String typeString) {
        if (OptUtils.notEmptyWS(rhs.getComputation())) {
            code.append(rhs.getComputation()).append(END_STMT).append(indentation());
        }
        code.append("putfield(this, ").append(assigned.get("name")).append(typeString);
        code.append(", ").append(rhs.getCode()).append(").V").append(END_STMT);
        return code.toString();
    }

    /**
     * Visits a return statement node.
     * @param node
     * @param unused
     * @return
     */
    private String visitReturn(JmmNode node, Void unused) {
        String methodName = node.getParent().get("name");
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = exprVisitor.visit(node.getJmmChild(0));

        if (OptUtils.notEmptyWS(expr.getComputation())) {
            code.append(expr.getComputation()).append(END_STMT).append(indentation());
        }

        code.append("ret").append(toOllirType(retType)).append(" ").append(expr.getCode());
        code.append(END_STMT);

        return code.toString();
    }

    /**
     * Visits an expression statement node.
     * @param node
     * @param unused
     * @return
     */
    private String visitExpressionStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        var expr = exprVisitor.visit(node.getJmmChild(0));

        code.append(expr.getCode());
        return code.toString();
    }

    /**
     * Generates a method parameter name + type.
     * @param node
     * @param unused
     * @return
     */
    private String generateMethodParam(JmmNode node, Void unused) {
        return node.get("name") + toOllirType(node.getJmmChild(0));
    }
}
