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

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(PAREN_EXPR, this::visitParenExpr);
        // unary?
        addVisit(FUNC_EXPR, this::visitFuncExpr);
        // member expr?
        addVisit(INTEGER_LITERAL, this::visitIntegerLiteral);
        addVisit(BOOLEAN_LITERAL, this::visitBooleanLiteral);

        addVisit(VAR_REF_EXPR, this::visitVariable);
        addVisit(IDENTIFIER, this::visitVariable);
        addVisit(THIS_EXPR, this::visitVariable);

        addVisit(NEW_EXPR, this::visitNewExpr);
        addVisit(BINARY_EXPR, this::visitBinExpr);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitParenExpr(JmmNode jmmNode, Void unused) {
        return visit(jmmNode.getChild(0));
    }

    private OllirExprResult visitFuncExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        OllirExprResult callerExprResult = visit(node.getChild(0));

        String callerCode = callerExprResult.getCode();
        String caller = callerCode.substring(0, callerCode.indexOf("."));
        String type = callerCode.substring(callerCode.indexOf(".") + 1);

        computation.append(computation(callerExprResult));

        ArrayList<OllirExprResult> argumentResult = generateArguments(node);

        for (var arg : argumentResult) {
            computation.append(computation(arg));
        }

        OllirExprResult invocation = generateInvocation(node, callerExprResult, caller, type, argumentResult);

        computation.append(computation(invocation));

        return new OllirExprResult(invocation.getCode(), computation.toString());
    }

    private OllirExprResult generateInvocation(JmmNode node, OllirExprResult callerExprResult, String caller, String type, ArrayList<OllirExprResult> argumentResult) {
        String invoke = caller.equals(type) ? "invokestatic" : "invokevirtual";

        JmmNode parent = node.getParent();

        if (!(parent.isInstance(ASSIGN_STMT) || parent.isInstance(BINARY_EXPR) || parent.isInstance(RETURN_STMT))) {
            return generateVoidInvocation(node, callerExprResult, caller, type, invoke, argumentResult);
        }

        return generateNonVoidInvocation(node, callerExprResult, caller, type, invoke);
    }

    private OllirExprResult generateNonVoidInvocation(JmmNode node, OllirExprResult callerExprResult, String caller, String type, String invoke) {
        return new OllirExprResult("");
    }

    private OllirExprResult generateVoidInvocation(JmmNode node, OllirExprResult callerExprResult, String caller, String type, String invoke, ArrayList<OllirExprResult> argumentResult) {
        StringBuilder code = new StringBuilder();

        code.append(invoke).append("(");

        code.append(caller);

        if (!caller.equals(type)) {
            code.append(".").append(type);
        }

        code.append(", \"").append(node.get("methodname")).append("\"");

        for (var ollirExprResult : argumentResult) {
            code.append(", ");
            code.append(ollirExprResult.getCode());
        }

        code.append(")").append(".V").append(END_STMT);

        return new OllirExprResult(code.toString());
    }

    private OllirExprResult generateStaticInvocation(JmmNode node, OllirExprResult callerExprResult, String caller) {
        StringBuilder computation = new StringBuilder();

        String invoke = "invokestatic";

        JmmNode parent = node.getParent();

        if (!(parent.isInstance(ASSIGN_STMT) || parent.isInstance(BINARY_EXPR) || parent.isInstance(RETURN_STMT))) {
            return generateVoidStaticInvocation(node, callerExprResult, caller);
        }

        return generateNonVoidStaticInvocation(node, callerExprResult, caller, computation, invoke);
    }

    private OllirExprResult generateNonVoidStaticInvocation(JmmNode node, OllirExprResult callerExprResult, String caller, StringBuilder computation, String invoke) {
        String returnType = OptUtils.toOllirType(TypeUtils.getExprType(node, table));

        computation.append(computation(callerExprResult));

        ArrayList<OllirExprResult> argumentResult = generateArguments(node);

        for (var arg : argumentResult) {
            computation.append(computation(arg));
        }

        var temp = OptUtils.getTemp();

        computation.append(temp).append(returnType);
        computation.append(" :=").append(returnType).append(" ");
        computation.append(invoke).append("(");

        computation.append(caller).append(", \"");
        computation.append(node.get("methodname")).append("\"");

        for (var ollirExprResult : argumentResult) {
            computation.append(", ");
            computation.append(ollirExprResult.getCode());
        }

        computation.append(")").append(returnType);

        return new OllirExprResult(temp + returnType, computation.toString());
    }

    private OllirExprResult generateVoidStaticInvocation(JmmNode node, OllirExprResult callerExprResult, String caller) {
        StringBuilder code = new StringBuilder();
        String invoke = "invokestatic";

        code.append(computation(callerExprResult));

        ArrayList<OllirExprResult> argumentResult = generateArguments(node);

        for (var arg : argumentResult) {
            code.append(computation(arg));
        }

        code.append(invoke).append("(");

        code.append(caller).append(", \"");
        code.append(node.get("methodname")).append("\"");

        for (var ollirExprResult : argumentResult) {
            code.append(", ");
            code.append(ollirExprResult.getCode());
        }

        code.append(")").append(".V").append(END_STMT);

        return new OllirExprResult(code.toString());
    }

    private OllirExprResult generateVirtualInvocation(JmmNode node, OllirExprResult callerExprResult, String caller, String type) {
        StringBuilder computation = new StringBuilder();

        String invoke = "invokevirtual";
        String returnType = OptUtils.toOllirType(TypeUtils.getExprType(node, table));

        computation.append(computation(callerExprResult));

        ArrayList<OllirExprResult> argumentResult = generateArguments(node);

        for (var arg : argumentResult) {
            computation.append(computation(arg));
        }

        var temp = OptUtils.getTemp();

        computation.append(temp).append(returnType);
        computation.append(" :=").append(returnType).append(" ");
        computation.append(invoke).append("(");

        computation.append(caller).append(".").append(type).append(", \"");
        computation.append(node.get("methodname")).append("\"");

        for (var ollirExprResult : argumentResult) {
            computation.append(", ");
            computation.append(ollirExprResult.getCode());
        }

        computation.append(")").append(returnType);

        return new OllirExprResult(temp + returnType, computation.toString());
    }

    private  ArrayList<OllirExprResult> generateArguments(JmmNode node) {
        ArrayList<OllirExprResult> argumentResult = new ArrayList<>();
        var n = node.getNumChildren();
        for (var i = 1; i < n; i++) {
            argumentResult.add(visit(node.getChild(i)));
        }
        return argumentResult;
    }

    private OllirExprResult visitIntegerLiteral(JmmNode node, Void unused) {
        String type = OptUtils.toOllirType(new Type(TypeUtils.getIntTypeName(), false));
        return new OllirExprResult(node.get("value") + type);
    }

    private OllirExprResult visitBooleanLiteral(JmmNode node, Void unused) {
        String type = OptUtils.toOllirType(new Type(TypeUtils.getBooleanTypeName(), false));
        return new OllirExprResult(node.get("value") + type);
    }

    private OllirExprResult visitVariable(JmmNode node, Void unused) {
        Type type = TypeUtils.getExprType(node, table);
        String ollirType = OptUtils.toOllirType(type);

        // if variable is field need to getfield
        if (TypeUtils.isField(node, table)) {
            return generateGetField(node, ollirType);
        }

        return new OllirExprResult(node.get("name") + ollirType);
    }

    private static OllirExprResult generateGetField(JmmNode node, String ollirType) {
        var temp = OptUtils.getTemp();
        var computation = new StringBuilder();
        computation.append(temp).append(ollirType).append(" :=");
        computation.append(ollirType).append(" getfield(");
        computation.append("this, ");
        computation.append(node.get("name")).append(ollirType);
        computation.append(")").append(ollirType);

        return new OllirExprResult(temp + ollirType, computation);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        OllirExprResult lhs = visit(node.getJmmChild(0));
        OllirExprResult rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        // compute the children
        computation.append(computation(lhs)).append(computation(rhs));

        // code to compute self
        return getFinalAssignmentCode(node, computation, lhs, rhs);
    }

    private OllirExprResult getFinalAssignmentCode(JmmNode node, StringBuilder computation, OllirExprResult lhs, OllirExprResult rhs) {
        String type = OptUtils.toOllirType(TypeUtils.getExprType(node, table));
        String code = OptUtils.getTemp() + type;

        computation.append(code).append(" :=");
        computation.append(type).append(" ");
        computation.append(lhs.getCode()).append(" ");

        computation.append(node.get("op")).append(type);
        computation.append(" ").append(rhs.getCode());

        return new OllirExprResult(code, computation);
    }

    private String computation(OllirExprResult res) {
        if (OptUtils.notEmptyWS(res.getComputation())) {
            return res.getComputation() + END_STMT;
        }
        return "";
    }

    private OllirExprResult visitNewExpr(JmmNode jmmNode, Void unused) {
        StringBuilder computation = new StringBuilder();
        var temp = OptUtils.getTemp();
        var type = jmmNode.get("classname");
        computation.append(temp).append(".").append(type).append(" :=.").append(type);
        computation.append(" new(").append(type).append(").").append(type);
        computation.append(END_STMT);

        computation.append("        invokespecial(");
        computation.append(temp).append(".").append(type);
        computation.append(", \"<init>\").V");

        return new OllirExprResult(temp + "." + type, computation);
    }

    /**
     * Default visitor. Not used.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        return OllirExprResult.EMPTY;
    }
}
