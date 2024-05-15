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
    private static final String END_STMT = ";\n";

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

        OllirExprResult invocation = generateInvocation(node, caller, type, argumentResult);

        computation.append(computation(invocation));
        computation.append(END_STMT);

        return new OllirExprResult(invocation.getCode(), computation.toString());
    }

    private OllirExprResult generateInvocation(JmmNode node, String caller, String type, ArrayList<OllirExprResult> argumentResult) {
        String invoke = caller.equals(type) ? "invokestatic" : "invokevirtual";

        JmmNode parent = node.getParent();

        if (!(parent.isInstance(ASSIGN_STMT) || parent.isInstance(BINARY_EXPR) || parent.isInstance(RETURN_STMT))) {
            return generateVoidInvocation(node, caller, type, invoke, argumentResult);
        }

        return generateNonVoidInvocation(node, caller, type, invoke, argumentResult);
    }

    private OllirExprResult generateVoidInvocation(JmmNode node, String caller, String type, String invoke, ArrayList<OllirExprResult> argumentResult) {
        StringBuilder code = new StringBuilder();
        code.append(generateInvocationUntilReturn(node, caller, type, invoke, argumentResult));
        code.append(")").append(".V").append(END_STMT);

        return new OllirExprResult(code.toString());
    }


    private OllirExprResult generateNonVoidInvocation(JmmNode node, String caller, String type, String invoke, ArrayList<OllirExprResult> argumentResult) {
        StringBuilder computation = new StringBuilder();
        String returnType = OptUtils.toOllirType(TypeUtils.getExprType(node.getParent(), table));

        var temp = OptUtils.getTemp();
        computation.append(temp).append(returnType).append(" :=");
        computation.append(returnType).append(" ");
        computation.append(generateInvocationUntilReturn(node, caller, type, invoke, argumentResult));

        computation.append(")");

        computation.append(returnType).append(END_STMT);

        return new OllirExprResult(temp + returnType, computation);
    }

    private String generateInvocationUntilReturn(JmmNode node, String caller, String type, String invoke, ArrayList<OllirExprResult> argumentResult) {
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

        return code.toString();
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
        if (!TypeUtils.isLocal(node, table) && !TypeUtils.isParam(node, table) && TypeUtils.isField(node, table)) {
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
        computation.append(")").append(ollirType).append(END_STMT);

        return new OllirExprResult(temp + ollirType, computation);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        OllirExprResult lhs = visit(node.getJmmChild(0));
        OllirExprResult rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        // compute the children
        computation.append(computation(lhs));
        computation.append(computation(rhs));

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
        computation.append(" ").append(rhs.getCode()).append(END_STMT);

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
        computation.append(", \"<init>\").V").append(END_STMT);

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
