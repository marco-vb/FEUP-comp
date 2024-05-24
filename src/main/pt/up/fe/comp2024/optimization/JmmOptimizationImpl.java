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
import pt.up.fe.comp2024.CompilerConfig;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class JmmOptimizationImpl implements JmmOptimization {

    ArrayList<Kind> assignments;

    public JmmOptimizationImpl() {
        assignments = new ArrayList<>();
        assignments.add(ASSIGN_STMT);
        assignments.add(ARRAY_ASSIGN_STMT);
        assignments.add(FIELD_ASSIGN_STMT);
    }

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
        var config = semanticsResult.getConfig();
        var optimize = CompilerConfig.getOptimize(config);

        if (optimize) {
            optimizeConstantPropAndFold(semanticsResult.getRootNode(), semanticsResult.getSymbolTable());
        }
        inPlaceArrayForVarArgs(semanticsResult.getRootNode(), semanticsResult.getSymbolTable());
        return semanticsResult;
    }

    private void optimizeConstantPropAndFold(JmmNode node, SymbolTable table) {
        while (propagate(node, table) || fold(node, table)) ;
    }

    private boolean propagate(JmmNode node, SymbolTable table) {
        var cl = node.getChildren(CLASS_DECL).get(0);
        var methods = cl.getChildren(METHOD_DECL);

        boolean ret = false;

        for (var method : methods) {
            ret |= propagateInMethod(method, table);
        }

        return false;
    }

    private boolean propagateInMethod(JmmNode node, SymbolTable table) {
        HashMap<String, Integer> integers = new HashMap<>();
        HashMap<String, Boolean> booleans = new HashMap<>();

        boolean ret = false;

        for (var stmt : node.getChildren()) {
            Kind kind;
            try {
                kind = Kind.fromString(stmt.getKind());
                if (!kind.isStmt()) continue;
            } catch (Exception ignored) {
                continue;
            }

            if (kind.isAssign()) {
                var var = stmt.getChild(0).get("name");
                var expr = stmt.getChild(stmt.getNumChildren() - 1);
                ret |= addAndReplace(var, expr, integers, booleans);
            }
            if (stmt.isInstance(RETURN_STMT)) {
                var expr = stmt.getChild(0);
                ret |= tryReplace(expr, integers, booleans);
            }
            if (stmt.isInstance(IF_ELSE_STMT)) {
                var expr = stmt.getChild(0);
                ret |= checkIfStmt(stmt, integers, booleans);
                ret |= tryReplace(expr, integers, booleans);
            }
            if (stmt.isInstance(WHILE_STMT)) {
                var expr = stmt.getChild(0);
                ret |= checkWhileStmt(stmt, integers, booleans);
                ret |= tryReplace(expr, integers, booleans);
            }
        }

        return false;
    }

    private boolean checkWhileStmt(JmmNode stmt, HashMap<String, Integer> integers, HashMap<String, Boolean> booleans) {
        boolean ret = false;
        var expr = stmt.getChild(0);
        var body = stmt.getChild(1);

        for (var key : integers.keySet()) {
            boolean updated = varUpdated(key, body);
            if (!updated) {
                for (var child : body.getChildren()) {
                    HashMap<String, Integer> mi = new HashMap<>();
                    mi.put(key, integers.get(key));
                    HashMap<String, Boolean> mb = new HashMap<>();
                    ret |= tryReplace(child, mi, mb);
                }
            } else {
                integers.remove(key);
            }
        }

        for (var key : booleans.keySet()) {
            boolean updated = varUpdated(key, body);
            if (!updated) {
                for (var child : body.getChildren()) {
                    HashMap<String, Boolean> mb = new HashMap<>();
                    mb.put(key, booleans.get(key));
                    HashMap<String, Integer> mi = new HashMap<>();
                    ret |= tryReplace(child, mi, mb);
                }
            } else {
                booleans.remove(key);
            }
        }

        return ret;
    }

    private boolean checkIfStmt(JmmNode stmt, HashMap<String, Integer> integers, HashMap<String, Boolean> booleans) {
        var expr = stmt.getChild(0);
        var ifBody = stmt.getChild(1);
        var elseBody = stmt.getChild(2);

        boolean ret = false;

        for (var key : integers.keySet()) {
            boolean updated = varUpdated(key, ifBody);
            if (!updated) {
                for (var child : ifBody.getChildren()) {
                    HashMap<String, Integer> mi = new HashMap<>();
                    mi.put(key, integers.get(key));
                    HashMap<String, Boolean> mb = new HashMap<>();
                    ret |= tryReplace(child, mi, mb);
                }
            }

            updated |= varUpdated(key, elseBody);
            if (!updated) {
                for (var child : elseBody.getChildren()) {
                    HashMap<String, Integer> mi = new HashMap<>();
                    mi.put(key, integers.get(key));
                    HashMap<String, Boolean> mb = new HashMap<>();
                    ret |= tryReplace(child, mi, mb);
                }
            } else {
                integers.remove(key);
            }
        }

        for (var key : booleans.keySet()) {
            boolean updated = varUpdated(key, ifBody);
            if (!updated) {
                for (var child : ifBody.getChildren()) {
                    HashMap<String, Boolean> mb = new HashMap<>();
                    mb.put(key, booleans.get(key));
                    HashMap<String, Integer> mi = new HashMap<>();
                    ret |= tryReplace(child, mi, mb);
                }
            }

            updated |= varUpdated(key, elseBody);
            if (!updated) {
                for (var child : elseBody.getChildren()) {
                    HashMap<String, Boolean> mb = new HashMap<>();
                    mb.put(key, booleans.get(key));
                    HashMap<String, Integer> mi = new HashMap<>();
                    ret |= tryReplace(child, mi, mb);
                }
            } else {
                booleans.remove(key);
            }
        }

        return ret;
    }

    private boolean varUpdated(String key, JmmNode stmt) {
        if (stmt.isInstance(SCOPE_STMT)) {
            boolean ret = false;
            for (var child : stmt.getChildren()) {
                ret |= varUpdated(key, child);
            }
            return ret;
        }

        Kind kind;
        try {
            kind = Kind.fromString(stmt.getKind());
        } catch (Exception ignored) {
            return true;
        }

        if (kind.isAssign()) {
            var name = stmt.getChild(0).get("name");
            var expr = stmt.getChild(stmt.getNumChildren() - 1);
            return name.equals(key) && !canEvaluate(expr);
        }

        return false;
    }

    private boolean tryReplace(JmmNode expr, HashMap<String, Integer> integers, HashMap<String, Boolean> booleans) {
        if (expr.isInstance(PAREN_EXPR)) {
            return tryReplace(expr.getChild(0), integers, booleans);
        }
        if (expr.isInstance(UNARY_EXPR)) {
            return tryReplace(expr.getChild(0), integers, booleans);
        }
        if (expr.isInstance(BINARY_EXPR)) {
            return tryReplace(expr.getChild(0), integers, booleans) ||
                    tryReplace(expr.getChild(1), integers, booleans);
        }
        if (expr.isInstance(VAR_REF_EXPR)) {
            return replaceVar(expr, integers, booleans);
        }

        boolean ret = false;
        for (var child : expr.getChildren()) {
            ret |= tryReplace(child, integers, booleans);
        }

        return ret;
    }

    private boolean replaceVar(JmmNode expr, HashMap<String, Integer> integers, HashMap<String, Boolean> booleans) {
        JmmNode parent = expr.getParent();
        String name = expr.get("name");
        AJmmNode add;

        if (integers.containsKey(name)) {
            add = new JmmNodeImpl(INTEGER_LITERAL.toString());
            add.put("value", integers.get(name).toString());
        } else if (booleans.containsKey(name)) {
            add = new JmmNodeImpl(BOOLEAN_LITERAL.toString());
            add.put("value", booleans.get(name).toString());
        } else {
            return false;
        }

        int idx = expr.getIndexOfSelf();
        expr.detach();
        parent.add(add, idx);
        return true;
    }

    private boolean addAndReplace(String var, JmmNode expr, HashMap<String, Integer> integers, HashMap<String, Boolean> booleans) {
        if (expr.isInstance(INTEGER_LITERAL)) {
            integers.put(var, Integer.parseInt(expr.get("value")));
            return false;   // did not replace constant
        }
        if (expr.isInstance(BOOLEAN_LITERAL)) {
            booleans.put(var, Boolean.parseBoolean(expr.get("value")));
            return false;   // did not replace constant
        }
        if (expr.isInstance(PAREN_EXPR)) {
            return addAndReplace(var, expr.getChild(0), integers, booleans);
        }
        if (expr.isInstance(UNARY_EXPR)) {
            return addAndReplace(var, expr.getChild(0), integers, booleans);
        }
        if (expr.isInstance(BINARY_EXPR)) {
            return addAndReplace(var, expr.getChild(0), integers, booleans) ||
                    addAndReplace(var, expr.getChild(1), integers, booleans);
        }
        if (expr.isInstance(VAR_REF_EXPR)) {
            // if something like i = 1; i = i + 1; cannot propagate i;
            if (var.equals(expr.get("name"))) return false;
            return replaceVar(expr, integers, booleans);
        }

        return false;
    }

    private boolean fold(JmmNode node, SymbolTable table) {
        boolean ret = false;

        try {
            if (assignments.contains(Kind.fromString(node.getKind()))) {
                ret |= foldExpr(node, table);
            }
        } catch (Exception e) {
            // thrown by Kind.fromString, ignore
        }

        for (var child : node.getChildren()) {
            ret |= fold(child, table);
        }

        return ret;
    }

    private boolean foldExpr(JmmNode node, SymbolTable table) {
        JmmNode expr = node.getChild(node.getNumChildren() - 1);

        if (expr.isInstance(INTEGER_LITERAL) || expr.isInstance(BOOLEAN_LITERAL)) return false;
        if (!canEvaluate(expr)) return false;

        int value = evaluateExpr(expr);
        String result = String.valueOf(value);

        Type type = TypeUtils.getExprType(expr, table);
        AJmmNode add;
        if (type.equals(TypeUtils.getBooleanType())) {
            result = value == 1 ? "true" : "false";
            add = new JmmNodeImpl(BOOLEAN_LITERAL.toString());
        } else {
            add = new JmmNodeImpl(INTEGER_LITERAL.toString());
        }
        add.put("value", result);
        expr.detach();
        node.add(add);

        return true;
    }

    private int evaluateExpr(JmmNode node) {
        if (node.isInstance(INTEGER_LITERAL))
            return Integer.parseInt(node.get("value"));
        if (node.isInstance(BOOLEAN_LITERAL))
            return node.get("value").equals("true") ? 1 : 0;
        if (node.isInstance(PAREN_EXPR))
            return evaluateExpr(node.getChild(0));
        if (node.isInstance(UNARY_EXPR))
            return 1 - evaluateExpr(node.getChild(0));
        if (node.isInstance(BINARY_EXPR)) {
            int left = evaluateExpr(node.getChild(0));
            int right = evaluateExpr(node.getChild(1));

            String operator = node.get("op");
            if ("+-*/".contains(operator)) {
                return switch (operator) {
                    case "+" -> left + right;
                    case "-" -> left - right;
                    case "*" -> left * right;
                    case "/" -> left / right;
                    default -> 0;
                };
            } else if (operator.equals("&&") || operator.equals("||")) {
                boolean res = switch (operator) {
                    case "&&" -> left == 1 && right == 1;
                    case "||" -> left == 1 || right == 1;
                    default -> false;
                };

                return res ? 1 : 0;
            } else {
                boolean res = switch (operator) {
                    case "<" -> left < right;
                    case "<=" -> left <= right;
                    case ">" -> left > right;
                    case ">=" -> left >= right;
                    case "==" -> left == right;
                    default -> false;
                };

                return res ? 1 : 0;
            }
        }
        return 0;
    }

    private boolean canEvaluate(JmmNode node) {
        if (node.isInstance(INTEGER_LITERAL))
            return true;
        if (node.isInstance(BOOLEAN_LITERAL))
            return true;
        if (node.isInstance(PAREN_EXPR))
            return canEvaluate(node.getChild(0));
        if (node.isInstance(BINARY_EXPR))
            return canEvaluate(node.getChild(0)) && canEvaluate(node.getChild(1));
        if (node.isInstance(UNARY_EXPR))
            return canEvaluate(node.getChild(0));
        return false;
    }

    // this pass will transform calls to varargs methods with calls with arrays
    // example:
    // public int foo(int... a) { return a[0]; }
    // this.foo(1, 2, 3); -> this.foo([1,2,3]);
    private void inPlaceArrayForVarArgs(JmmNode node, SymbolTable table) {
        dfsReplaceVarArgCalls(node, table);
    }

    private void dfsReplaceVarArgCalls(JmmNode node, SymbolTable table) {
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
            dfsReplaceVarArgCalls(child, table);
        }
    }

    private void checkVarArgsAndReplace(JmmNode node, SymbolTable table) {
        var params = table.getParameters(node.get("methodname"));
        if (params.isEmpty()) return;

        Symbol lastParam = params.get(params.size() - 1);
        JmmNode lastChild = node.getChild(node.getNumChildren() - 1);

        // last argument is not array, no change needed
        if (!lastParam.getType().isArray()) return;

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
