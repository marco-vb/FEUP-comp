package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.ast.TypeUtils.getExprType;
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
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBool);

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

        var invoke = TypeUtils.getInvokeType(node, table);
        var type = TypeUtils.getExprType(node.getChild(0), table).getName();
        var caller = node.getChild(0).get("name");

        var parent = node.getParent();
        String returnType;

        ArrayList<OllirExprResult> argumentResult = new ArrayList<>();

        var n = node.getNumChildren();
        for (var i = 1; i < n; i++) {
            argumentResult.add(visit(node.getChild(i)));
        }

        if (parent.isInstance(ASSIGN_STMT)) {
            returnType = toOllirType(getExprType(parent.getJmmChild(0), table));
        } else if (parent.isInstance(BINARY_EXPR)) {
            returnType = toOllirType(getExprType(parent, table));
        } else {
            return voidInvocation(node, invoke, OptUtils.getTemp(), type, caller, argumentResult);
        }

        for (var arg : argumentResult) {
            if (OptUtils.notEmptyWS(arg.getComputation())) {
                computation.append(arg.getComputation()).append(END_STMT);
            }
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

    private OllirExprResult visitBool(JmmNode jmmNode, Void unused) {
        String type = OptUtils.toOllirType(new Type(TypeUtils.getBooleanTypeName(), false));
        return new OllirExprResult(jmmNode.get("value") + type);
    }

    private OllirExprResult visitVariable(JmmNode node, Void unused) {
        Type type = TypeUtils.getExprType(node, table);
        String ollirType = OptUtils.toOllirType(type);

        // if variable is field need to getfield first
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

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        String type = OptUtils.toOllirType(new Type(TypeUtils.getIntTypeName(), false));
        return new OllirExprResult(node.get("value") + type);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        OllirExprResult lhs = visit(node.getJmmChild(0));
        OllirExprResult rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        if (OptUtils.notEmptyWS(lhs.getComputation())) {
            computation.append(lhs.getComputation()).append(END_STMT);
        }
        if (OptUtils.notEmptyWS(rhs.getComputation())) {
            computation.append(rhs.getComputation()).append(END_STMT);
        }

        // code to compute self
        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(" :=");
        computation.append(resOllirType).append(" ");
        computation.append(lhs.getCode()).append(" ");

        Type type = TypeUtils.getExprType(node, table);
        computation.append(node.get("op")).append(OptUtils.toOllirType(type));
        computation.append(" ").append(rhs.getCode());

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult voidInvocation(JmmNode node, String invoke, String temp, String type, String caller, ArrayList<OllirExprResult> arguments) {
        StringBuilder code = new StringBuilder();

        for (var arg : arguments) {
            if (OptUtils.notEmptyWS(arg.getComputation())) {
                code.append(arg.getComputation()).append(END_STMT);
            }
        }

        code.append(invoke).append("(");
        code.append(caller);

        if (!caller.equals(type)) {
            code.append(".").append(type);
        }

        code.append(", \"");
        code.append(node.get("methodname")).append("\"");

        for (var ollirExprResult : arguments) {
            code.append(", ");
            code.append(ollirExprResult.getCode());
        }

        code.append(").V");

        return new OllirExprResult(code.toString());
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
