package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmNode;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

import java.util.ArrayList;
import java.util.Collections;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {

        //TODO: Do your OLLIR-based optimizations here

        return ollirResult;
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        return this.inPlaceArrayForVarArgs(semanticsResult);
    }

    // this pass will transform calls to varargs methods with calls with arrays
    // example:
    // public int foo(int... a) { return a[0]; }
    // this.foo(1, 2, 3); -> this.foo([1,2,3]);
    private JmmSemanticsResult inPlaceArrayForVarArgs(JmmSemanticsResult node) {
        JmmNode root = node.getRootNode();
        SymbolTable table = node.getSymbolTable();
        dfs(root, table);
        return node;
    }

    private void dfs(JmmNode node, SymbolTable table) {
        if (node.isInstance(FUNC_EXPR)) {
            var methods = table.getMethods();
            for (var method : methods) {
                if (node.get("methodname").equals(method)) {
                    checkVarArgsAndReplace(node, table);
                }
            }
        }

        // if not func expr continue visit
        for (var child : node.getChildren()) {
            dfs(child, table);
        }
    }

    private void checkVarArgsAndReplace(JmmNode node, SymbolTable table) {
        var params = table.getParameters(node.get("methodname"));
        if (params.isEmpty()) return;

        Symbol lastParam = params.get(params.size() - 1);
        JmmNode lastChild = node.getChild(node.getNumChildren() - 1);

        // last argument is array but last child is also array, no need to change
        if (lastParam.getType().isArray() && lastChild.isInstance(ARRAY_EXPR)) return;

        // from now on we know lastParam is varargs and we must alter tree
        int start = params.size();
        ArrayList<JmmNode> arrayExprs = new ArrayList<>();

        for (int i = start; i < node.getNumChildren(); i++) {
            JmmNode child = node.getChild(i);
            arrayExprs.add(child);
        }

        for (var c : arrayExprs) {
            c.detach();
        }

        JmmNode arrayExpr = new JmmNodeImpl(ARRAY_EXPR.toString());

        for (var c : arrayExprs) {
            arrayExpr.add(c);
        }

        node.add(arrayExpr);
    }
}
