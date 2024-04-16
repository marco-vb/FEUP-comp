package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import javax.xml.namespace.QName;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.optimization.OptUtils.toOllirType;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";
    private final SymbolTable table;
    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.table.print();
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(ARGUMENT, this::visitArgs);
        addVisit(SCOPE_STMT, this::visitScopeStmt);
        addVisit(FUNC_EXPR, this::visitFuncExpr);
        addVisit(VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(EXPRESSION_STMT, this::visitExpressionStmt);

        // JmmNode = "Args" -> ARGS
        // JmmNode = "StmtReturn" -> STMT_RETURN

        setDefaultVisit(this::defaultVisit);
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
        //String type = toOllirType(this.table.getLocalVariables(name).get(0).getType());
        code.append(name);
        //.append(type);
        return code.toString();
    }

    private String visitFuncExpr(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        code.append("invokestatic(");

        for (int i  = 0; i < node.getNumChildren(); i++)
        {
            if (node.getChild(i).getKind().equals("Args")) {
                code.append(visit(node.getChild(i)));   // FuncExpr
                continue;
            }

            if (node.getChild(i).get("name").equals("println")) {
                code.append("\"");
                code.append(node.getChild(i).get("name"));
                code.append("\", ");
                continue;
            }


            code.append(node.getChild(i).get("name"));
            code.append(", ");
        }
        code.append(").V");
        code.append(NL);
        //System.out.println(node.getChild(0).getChild(1));
        return code.toString();
    }
    private String visitScopeStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        for (var child : node.getChildren()) {
            code.append(visit(child));
        }

        return code.toString();
    }

    private String visitArgs(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        if (node.getChildren().size() == 0)
            return "";

        for (int i = 0; i < node.getNumChildren()-1; i++){
            String n = visit(node.getJmmChild(i));
            code.append(n);
            code.append(", ");
        }
        code.append(visit(node.getChild(node.getNumChildren()-1)));

        return code.toString();
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        System.out.println("ABC");
        System.out.println(node.getJmmChild(0));
        System.out.println(node.getJmmChild(1));
        var lhs = exprVisitor.visit(node.getJmmChild(0));
        var rhs = exprVisitor.visit(node.getJmmChild(1));

        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = toOllirType(thisType);


        code.append(lhs.getCode());
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

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

        code.append(expr.getComputation());
        code.append("ret");
        code.append(toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        if (code.toString().isEmpty())
            code.append("ret.V");


        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        // Add variable name
        String variableName = node.get("name");
        code.append(variableName);

        // Add type
        var typeCode = node.getChild(0).get("name");
        //System.out.println(typeCode);
        //System.out.println(node.getChild(0));

        switch (typeCode) {
            case "int" -> code.append(".i32");
            case "boolean" -> code.append(".bool");
        }

        if (typeCode.equals("String") && node.getChild(0).get("isArray").equals("false"))
            code.append("String");
        else if (typeCode.equals("String") && node.getChild(0).get("isArray").equals("true"))
            code.append(".array.String");

        return code.toString();
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        // name
        var name = node.get("name");
        code.append(name);

        // param
        var paramCode = visit(node.getJmmChild(1));
        code.append("(" + paramCode + ")");

        // type
        var retType = toOllirType(node.getJmmChild(0));
        code.append(retType);
        code.append(L_BRACKET);


        // rest of its children stmts
        var afterParam = 2;
        for (int i = afterParam; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            var childCode = visit(child);
            code.append(childCode);
        }

        code.append(R_BRACKET);
        code.append(NL);


        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());

        //System.out.println(table.getSuper());
        if (table.getSuper() != null && !table.getSuper().isEmpty())
            code.append(" extends ").append(table.getSuper());
        else
            code.append(" extends Object ").append(table.getSuper());
        code.append(L_BRACKET);

        code.append(NL);

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");
        if (table.getFields() != null && !table.getFields().isEmpty())
            for(int i =  0; i < table.getFields().size(); i++) {
                if (isPublic)
                    code.append(".field public ").append(table.getFields().get(i).getName()).append(toOllirType(table.getFields().get(i).getType())).append(";");
                else
                    code.append(".field ").append(table.getFields().get(i).getName()).append(toOllirType(table.getFields().get(i).getType())).append(";");
                code.append(NL);
            }

        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
