package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.Instruction;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2024.ast.Kind.TYPE;

public class OptUtils {
    private static int tempNumber = -1;

    public static String getTemp() {

        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {

        return prefix + getNextTempNum();
    }

    public static int getNextTempNum() {

        tempNumber += 1;
        return tempNumber;
    }

    public static String toOllirType(JmmNode typeNode) {
        TYPE.checkOrThrow(typeNode);
        String typeName = toOllirType(typeNode.get("name"));
        if (typeNode.get("isArray").equals("true")) {
            typeName = ".array" + typeName;
        }
        return typeName;
    }

    public static String toOllirType(Type type) {
        if (type.isArray()) {
            return ".array" + toOllirType(type.getName());
        }
        return toOllirType(type.getName());
    }

    private static String toOllirType(String typeName) {

        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "String" -> "String";
            case "void" -> "V";
            default -> typeName;
        };

        // imported type
        return type;
    }

    public static boolean notEmptyWS(String str) {
        return str != null && !str.isBlank();
    }
}