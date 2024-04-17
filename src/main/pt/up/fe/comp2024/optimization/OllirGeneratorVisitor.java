package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import javax.xml.namespace.QName;

import java.security.spec.EdDSAParameterSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.ast.TypeUtils.getExprType;
import static pt.up.fe.comp2024.ast.TypeUtils.getInvokeType;
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

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.table.print();
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }

    private String indentation() {
        return indent.stream().reduce("", String::concat);
    }

    private void removeIndent() {
        indent.remove(indent.size() - 1);
    }

    private void addIndent() {
        indent.add(TAB);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethod);

        setDefaultVisit(this::defaultVisit);
    }

    /**
     * Default visitor (not used).
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {
        System.out.println("Default visit used: " + node.getKind());
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


    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // get imports
        for (var imp : table.getImports()) {
            code.append("import ").append(imp).append(END_STMT);
        }

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
                .map(child -> visitParam(child, null))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        code.append("(").append(params).append(")");
        code.append(toOllirType(node.getJmmChild(0))).append(L_BRACKET);

        addIndent();

        // add method statements
        for (int i = 2; i < node.getNumChildren(); i++) {
            var childCode = visit(node.getJmmChild(i));
            if (childCode.isEmpty() || childCode.isBlank()) continue;

            code.append(indentation()).append(childCode).append(END_STMT);
        }

        var returnType = table.getReturnType(node.get("name"));

        // If return is void there is no return statement so we add it
        if (returnType.getName().equals("void")) {
            code.append(indentation()).append("ret.V").append(END_STMT);
        }

        removeIndent();
        code.append(indentation()).append(R_BRACKET);
        
        return code.toString();
    }

    private String buildConstructor() {
        return TAB + ".construct " + table.getClassName() +
                "().V" + L_BRACKET + TAB + TAB +
                "invokespecial(this, \"<init>\").V;" + NL +
                TAB + R_BRACKET;
    }

    private String visitIdentifier(JmmNode node, Void unused){
        return node.get("name");
    }

    private String visitExpressionStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < node.getNumChildren(); i++)
            code.append(visit(node.getChild(i)));
        return code.toString();
    }
    private String visitVarRefExpr(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        String name = node.get("name");
        var parent = node.getParent();
        while (!parent.isInstance(METHOD_DECL)) parent = parent.getParent();
        var locals = table.getLocalVariables(parent.get("name"))
                .stream().filter(v -> v.getName().equals(name)).toList();

        // if size not 1 error
        if (locals.size() != 1) {
            System.out.println("Error: " + locals.size());
            return "";
        }

        var type = locals.get(0).getType();
        code.append(name).append(toOllirType(type));
        return code.toString();
    }

    private String visitFuncExpr(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        var invoke = getInvokeType(node, table);
        code.append(invoke).append("(");

        var type = node.getChild(0).get("name");
        code.append(type).append(", \"");
        code.append(node.get("methodname")).append("\"");

        for (var i = 1; i < node.getNumChildren(); i++) {
            if (i == 1) code.append(", ");
            var expr = exprVisitor.visit(node.getChild(i)).getCode();
            code.append(expr);
        }

        code.append(")");
        var parent = node.getParent();

        if (parent.isInstance(ASSIGN_STMT)) {
            var returnType = getExprType(parent.getJmmChild(0), table);
            code.append(toOllirType(returnType));
        } else {
            code.append(".V");
        }

        return code.toString();
    }
    private String visitScopeStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        for (var child : node.getChildren()) {
            code.append(toOllirType(child));
        }

        return code.toString();
    }

    private String visitArgs(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        if (node.getChildren().isEmpty())
            return "";

        for (int i = 0; i < node.getNumChildren()-1; i++){
            code.append(visit(node.getChild(i)));
            code.append(", ");
        }
        code.append(visit(node.getChild(node.getNumChildren()-1)));

        return code.toString();
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        var lhs = exprVisitor.visit(node.getJmmChild(0));
        var varType = getExprType(node.getJmmChild(0), table);
        var ollirType = OptUtils.toOllirType(varType);
        var rhs = exprVisitor.visit(node.getJmmChild(1));

        StringBuilder code = new StringBuilder();

        // code to compute self
        // statement has type of lhs
        Type thisType = getExprType(node.getJmmChild(0), table);
        String typeString = toOllirType(thisType);


        code.append(lhs.getComputation()).append(TAB + TAB);
        code.append(rhs.getComputation()).append(TAB + TAB);
        code.append(lhs.getCode()).append(SPACE);
        code.append(ASSIGN).append(typeString).append(SPACE);
        code.append(rhs.getCode());


        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation()).append(TAB + TAB);
        code.append("ret").append(toOllirType(retType));
        code.append(SPACE).append(expr.getCode());

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {
        return node.get("name") + toOllirType(node.getJmmChild(0));
    }
}
