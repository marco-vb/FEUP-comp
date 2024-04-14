package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

/**
 * A utility class for working with types.
 */
public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String BOOLEAN_TYPE_NAME = "boolean";

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
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case ARRAY_ACCESS_EXPR -> new Type(INT_TYPE_NAME, true);
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            case BOOLEAN_LITERAL -> new Type(BOOLEAN_TYPE_NAME, false);
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

        throw new RuntimeException("Variable '" + varName + "' not found in the symbol table");
    }


    /**
     * Checks if the source type can be assigned to the destination type.
     *
     * @param sourceType      The type of the source expression.
     * @param destinationType The type of the destination expression.
     * @return true if the source type can be assigned to the destination type, false otherwise.
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        return sourceType.getName().equals(destinationType.getName());
    }
}
