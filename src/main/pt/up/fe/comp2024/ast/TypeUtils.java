package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import static pt.up.fe.comp2024.ast.Kind.METHOD_DECL;

/**
 * A utility class for working with types.
 */
public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String BOOLEAN_TYPE_NAME = "boolean";
    private static final String VOID_TYPE_NAME = "void";

    /**
     * Gets the name of the int type.
     *
     * @return The name of the int type.
     */
    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    /**
     * Gets the name of the boolean type.
     *
     * @return The name of the boolean type.
     */
    public static String getBooleanTypeName() {
        return BOOLEAN_TYPE_NAME;
    }

    /**
     * Gets the name of the void type.
     *
     * @return The name of the void type.
     */
    public static String getVoidTypeName() {
        return VOID_TYPE_NAME;
    }

    /**
     * Gets the type of the int type.
     *
     * @return The type of the int type.
     */
    public static Type getIntType() {
        return new Type(INT_TYPE_NAME, false);
    }

    /**
     * Gets the type of the boolean type.
     *
     * @return The type of the boolean type.
     */
    public static Type getBooleanType() {
        return new Type(BOOLEAN_TYPE_NAME, false);
    }

    /**
     * Gets the type of the void type.
     *
     * @return The type of the void type.
     */
    public static Type getVoidType() {
        return new Type(VOID_TYPE_NAME, false);
    }

    /**
     * Gets the type of an array type.
     *
     * @return The type of the array type.
     */
    public static Type getArrayType() {
        return new Type(INT_TYPE_NAME, true);
    }

    /**
     * Gets the type of an expression.
     *
     * @param expr  The expression node.
     * @param table The symbol table.
     * @return The type of the expression.
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        var kind = Kind.fromString(expr.getKind());

        return switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case VAR_REF_EXPR, IDENTIFIER -> getVarExprType(expr, table);
            case FUNC_EXPR -> getFuncExprType(expr, table);
            case ARGUMENT -> getArgumentType(expr, table);
            case NEW_EXPR -> getNewExprType(expr);
            case ARRAY_EXPR, NEW_ARRAY_EXPR -> new Type(INT_TYPE_NAME, true);   // Array expressions are always of type int[]
            case INTEGER_LITERAL, ARRAY_ACCESS_EXPR -> getIntType();
            case BOOLEAN_LITERAL -> getBooleanType();
            case THIS_EXPR -> new Type(table.getClassName(), false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };
    }

    /**
     * Gets the type of a binary expression.
     *
     * @param binaryExpr The binary expression node.
     * @return The type of the binary expression.
     */
    private static Type getBinExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "-", "*", "/" -> new Type(INT_TYPE_NAME, false);
            case "<", "<=", ">", ">=", "==", "!=", "&&", "||" -> new Type(BOOLEAN_TYPE_NAME, false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }

    /**
     * Gets the type of a variable reference expression.
     *
     * @param varRefExpr The variable reference expression node.
     * @param table      The symbol table.
     * @return The type of the variable reference expression.
     */
    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        // Get the current method
        var currentMethod = varRefExpr.getAncestor(Kind.METHOD_DECL).orElseThrow().get("name");

        // Get the class fields, method parameters and local variables
        var fields = table.getFields();
        var params = table.getParameters(currentMethod);
        var locals = table.getLocalVariables(currentMethod);

        var varName = varRefExpr.get("name");

        // Check if the variable is a field
        for (var field : fields) {
            if (field.getName().equals(varName)) {
                return field.getType();
            }
        }

        // Check if the variable is a parameter
        for (var param : params) {
            if (param.getName().equals(varName)) {
                return param.getType();
            }
        }

        // Check if the variable is a local variable
        for (var local : locals) {
            if (local.getName().equals(varName)) {
                return local.getType();
            }
        }

        // Check if the variable is an imported class
        if (table.getImports().contains(varName)) {
            return new Type(varName, false);
        }

        return null;
    }

    /**
     * Gets the type of a function expression.
     *
     * @param funcExpr The function expression node.
     * @param table    The symbol table.
     * @return The type of the function expression.
     */
    private static Type getFuncExprType(JmmNode funcExpr, SymbolTable table) {
        var methodName = funcExpr.get("methodname");
        var methods = table.getMethods();

        // Search for the method in the symbol table
        for (var method : methods) {
            if (method.equals(methodName)) {
                return table.getReturnType(method);
            }
        }

        // If the method is not found, assume type is import
        return new Type("any", false);
    }

    /**
     * Gets the type of a new expression.
     *
     * @param newExpr The new expression node.
     * @return The type of the new expression.
     */
    private static Type getNewExprType(JmmNode newExpr) {
        var className = newExpr.get("classname");

        return new Type(className, false);
    }

    /**
     * Gets the type of an argument.
     *
     * @param argument The argument node.
     * @param table    The symbol table.
     * @return The type of the argument.
     */
    private static Type getArgumentType(JmmNode argument, SymbolTable table) {
        var type = argument.getChildren().get(0);

        return new Type(type.get("name"), Boolean.parseBoolean(type.get("isArray")));
    }

    /**
     * Checks if the source type can be assigned to the destination type.
     *
     * @param sourceType      The type of the source expression.
     * @param destinationType The type of the destination expression.
     * @return true if the source type can be assigned to the destination type, false otherwise.
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType, SymbolTable table) {
        var sourceTypeName = sourceType.getName();
        var destinationTypeName = destinationType.getName();

        if (sourceTypeName.equals("any") || destinationTypeName.equals("any")) {
            return true;
        }

        if (sourceTypeName.equals(table.getClassName()) && destinationTypeName.equals(table.getSuper())) {
            return true;
        }

        var imports = table.getImports();
        if (imports.contains(sourceTypeName) && imports.contains(destinationTypeName)) {
            return true;
        }

        return sourceType.equals(destinationType) || sourceType.getName().equals("any") || destinationType.getName().equals("any");
    }

    public static String getVarKind(String name, String method, SymbolTable table) {
        // check if is a class name or variable name
        var fields = table.getFields();
        var params = table.getParameters(method);
        var locals = table.getLocalVariables(method);
        if (fields.stream().anyMatch(f -> f.getName().equals(name))) {
            return "variable";
        } else if (params.stream().anyMatch(p -> p.getName().equals(name))) {
            return "variable";
        } else if (locals.stream().anyMatch(l -> l.getName().equals(name))) {
            return "variable";
        } else if (name.equals("this")) {
            return "variable";
        }
        return "class";
    }

    public static String getInvokeType(JmmNode node, SymbolTable table) {
        // get right type between invokespecial, invokestatic and invokevirtual

        var method = node.getParent();
        while (!method.isInstance(METHOD_DECL)) method = method.getParent();

        var name = node.getChild(0).get("name");
        var methodName = method.get("name");
        var variableKind = getVarKind(name, methodName, table);

        if (variableKind.equals("variable")) {
            return "invokevirtual";
        }
        return "invokestatic";
    }

    /**
     * Gets the name of a type.
     *
     * @param type The type.
     * @return The name of the type.
     */
    public static String getTypeName(Type type) {
        return type.getName() + (type.isArray() ? "[]" : "");
    }

    public static boolean isField(JmmNode node, SymbolTable table) {
        var fields = table.getFields();
        var name = node.get("name");
        return fields.stream().anyMatch(f -> f.getName().equals(name));
    }
}
