package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.optimization.OptUtils.toOllirType;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {
    private static final String END_STMT = ";\n";
    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(PAREN_EXPR, this::visitParenExpr);
        addVisit(UNARY_EXPR, this::visitUnaryExpr);
        addVisit(BINARY_EXPR, this::visitBinaryExpr);
        addVisit(FUNC_EXPR, this::visitFuncExpr);
        addVisit(MEMBER_EXPR, this::visitMemberExpr);
        addVisit(NEW_EXPR, this::visitNewExpr);
        addVisit(VAR_REF_EXPR, this::visitVariable);
        addVisit(IDENTIFIER, this::visitVariable);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult defaultVisit(JmmNode node, Void u) {
        return OllirExprResult.EMPTY;
    }

    private OllirExprResult visitParenExpr(JmmNode node, Void u) {
        return visit(node.getChild(0));
    }

    private OllirExprResult visitUnaryExpr(JmmNode node, Void u) {
        StringBuilder computation = new StringBuilder();

        // ! <expr>
        String type = ".bool";
        String code = OptUtils.getTemp() + type;

        OllirExprResult expr = visit(node.getChild(0));

        computation.append(expr.getComputation());
        computation.append(code).append(" :=");
        computation.append(type).append(" ");
        computation.append(expr.getCode()).append(type).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitBinaryExpr(JmmNode node, Void u) {
        StringBuilder computation = new StringBuilder();
        OllirExprResult left = visit(node.getChild(0));
        OllirExprResult right = visit(node.getChild(1));

        computation.append(left.getComputation());
        computation.append(right.getComputation());

        String type = toOllirType(TypeUtils.getExprType(node, table));
        String temp = OptUtils.getTemp("tmp") + type;
        computation.append(temp).append(" :=").append(type).append(" ");
        computation.append(left.getCode()).append(" ");
        computation.append(node.get("op")).append(type).append(" ");
        computation.append(right.getCode()).append(END_STMT);

        return new OllirExprResult(temp, computation);
    }

    private OllirExprResult visitFuncExpr(JmmNode node, Void u) {
        JmmNode first = node.getChild(0);

        // this.method() or this.field
        if (first.isInstance(THIS_EXPR)) {
            return visitThisExpr(node);
        }

        // (new A()).foo();
        if (first.isInstance(PAREN_EXPR)) {
            return visitVirtualExpr(node);
        }

        var imports = table.getImports();
        for (var imp : imports) {
            if (imp.equals(first.get("name"))) {
                return visitImportStaticExpr(node);
            }
        }

        return visitVirtualExpr(node);
    }

    private OllirExprResult visitVirtualExpr(JmmNode node) {
        StringBuilder computation = new StringBuilder();
        String type = toOllirType(getReturnType(node));
        var temp = OptUtils.getTemp("tmp") + type;

        ArrayList<OllirExprResult> args = new ArrayList<>();
        for (int i = 1; i < node.getNumChildren(); i++) {
            args.add(visit(node.getChild(i)));
        }

        for (var arg : args) {
            computation.append(arg.getComputation());
        }

        OllirExprResult expr = visit(node.getChild(0));
        computation.append(expr.getComputation());
        String caller = expr.getCode();
        String varType = toOllirType(TypeUtils.getExprType(node.getChild(0), table));

        if (!type.equals(".V")) {
            computation.append(temp).append(" :=").append(type).append(" ");
        }

        computation.append("invokevirtual(").append(caller);
        computation.append(", \"");
        computation.append(node.get("methodname")).append("\"");

        for (var arg : args) {
            computation.append(", ").append(arg.getCode());
        }

        computation.append(")").append(type).append(END_STMT);

        return new OllirExprResult(temp, computation.toString());
    }

    private OllirExprResult visitImportStaticExpr(JmmNode node) {
        StringBuilder computation = new StringBuilder();
        String type = toOllirType(getReturnType(node));
        var temp = OptUtils.getTemp("tmp") + type;

        ArrayList<OllirExprResult> args = new ArrayList<>();
        for (int i = 1; i < node.getNumChildren(); i++) {
            args.add(visit(node.getChild(i)));
        }

        for (var arg : args) {
            computation.append(arg.getComputation());
        }

        String imp = node.getChild(0).get("name");

        if (!type.equals(".V")) {
            computation.append(temp).append(" :=").append(type).append(" ");
        }

        computation.append("invokestatic(").append(imp).append(", \"");
        computation.append(node.get("methodname")).append("\"");

        for (var arg : args) {
            computation.append(", ").append(arg.getCode());
        }

        computation.append(")").append(type).append(END_STMT);

        return new OllirExprResult(temp, computation.toString());
    }

    private OllirExprResult visitThisExpr(JmmNode node) {
        String method = node.get("methodname");
        var fields = table.getFields();
        var methods = table.getMethods();

        for (var field : fields) {
            if (field.getName().equals(method)) {
                String type = OptUtils.toOllirType(field.getType());
                return visitThisField(node, type);
            }
        }

        return visitThisMethodExpr(node);
    }

    private OllirExprResult visitThisField(JmmNode node, String type) {
        var temp = OptUtils.getTemp("tmp") + type;
        var computation = new StringBuilder();
        computation.append(temp).append(" :=");
        computation.append(type).append(" getfield(");
        computation.append("this, ");

        if (node.hasAttribute("methodname")) {
            computation.append(node.get("methodname"));
        } else {
            computation.append(node.get("name"));
        }

        computation.append(type).append(")").append(type).append(END_STMT);

        return new OllirExprResult(temp, computation);
    }

    private OllirExprResult visitThisMethodExpr(JmmNode node) {
        String method = node.get("methodname");
        String type = toOllirType(table.getReturnType(method));
        var computation = new StringBuilder();

        ArrayList<OllirExprResult> args = new ArrayList<>();
        for (int i = 1; i < node.getNumChildren(); i++) {
            args.add(visit(node.getChild(i)));
        }

        for (var arg : args) {
            computation.append(arg.getComputation());
        }

        var temp = OptUtils.getTemp("tmp") + type;

        if (!type.equals(".V")) {
            computation.append(temp).append(" :=").append(type).append(" ");
        }
        computation.append("invokevirtual(this, \"");
        computation.append(method).append("\"");

        for (var arg : args) {
            computation.append(", ").append(arg.getCode());
        }

        computation.append(")").append(type).append(END_STMT);
        return new OllirExprResult(temp, computation);
    }

    private OllirExprResult visitNewExpr(JmmNode node, Void u) {
        StringBuilder computation = new StringBuilder();
        var type = node.get("classname");
        var temp = OptUtils.getTemp("tmp") + "." + type;

        computation.append(temp).append(" :=.").append(type).append(" new(");
        computation.append(type).append(").").append(type).append(END_STMT);

        computation.append("invokespecial(").append(temp);
        computation.append(", \"<init>\").V").append(END_STMT);

        return new OllirExprResult(temp, computation);
    }

    private OllirExprResult visitMemberExpr(JmmNode node, Void u) {
        return OllirExprResult.EMPTY;
    }

    private OllirExprResult visitVariable(JmmNode node, Void u) {
        Type type = TypeUtils.getExprType(node, table);
        String ollirType = OptUtils.toOllirType(type);

        // if variable is field need to getfield
        if (!TypeUtils.isLocal(node, table) && !TypeUtils.isParam(node, table) && TypeUtils.isField(node, table)) {
            return visitThisField(node, ollirType);
        }

        return new OllirExprResult(node.get("name") + ollirType);
    }

    private OllirExprResult visitInteger(JmmNode node, Void u) {
        String type = OptUtils.toOllirType(new Type(TypeUtils.getIntTypeName(), false));
        return new OllirExprResult(node.get("value") + type);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        String type = OptUtils.toOllirType(new Type(TypeUtils.getBooleanTypeName(), false));
        return new OllirExprResult(node.get("value") + type);
    }

    private Type getReturnType(JmmNode node) {
        JmmNode parent = node.getParent();

        if (parent.isInstance(ASSIGN_STMT)) {
            return TypeUtils.getExprType(parent.getChild(0), table);
        }

        var returnType = table.getReturnType(node.get("methodname"));

        if (returnType != null) {
            return returnType;
        }

        if (parent.isInstance(BINARY_EXPR)) {
            if ("+-*/".contains(parent.get("op"))) {
                return TypeUtils.getIntType();
            }
            return TypeUtils.getBooleanType();
        }

        if (parent.isInstance(FUNC_EXPR)) {
            var params = table.getParameters(parent.get("methodname"));
            for (int i = 0; i < params.size() + 1; i++) {
                if (parent.getChild(i + 1).equals(node)) {
                    return params.get(i).getType();
                }
            }
        }

        if (parent.isInstance(PAREN_EXPR)) {
            return getReturnType(parent);
        }

        return TypeUtils.getVoidType();
    }
}
