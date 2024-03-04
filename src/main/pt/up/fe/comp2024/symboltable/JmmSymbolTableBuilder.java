package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {
    public static JmmSymbolTable build(JmmNode root) {
        SpecsCheck.checkArgument(Kind.PROGRAM.check(root), () -> "Expected a valid program.");
        var classDecl = root.getChildren("ClassDeclaration").get(0);

        var className = classDecl.get("name");
        var superClass = classDecl.hasAttribute("ext") ? classDecl.get("ext") : "";
        var imports = buildImports(root.getChildren("ImportDeclaration"));
        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        return new JmmSymbolTable(
                className,
                superClass,
                imports,
                fields,
                methods,
                returnTypes,
                params,
                locals
        );
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {
        var fieldList = classDecl.getChildren("Variable");

        return fieldList.stream().map(
                node -> new Symbol(
                        buildType(node.getObject("typename", JmmNode.class)),
                        node.get("name")
                )
        ).toList();
    }

    private static List<String> buildImports(List<JmmNode> importList) {
        return importList.stream().map(node -> node.get("name")).toList();
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        var methods = classDecl.getChildren("Method");

        methods.forEach(method -> map.put(
                        method.get("name"),
                        buildType(method.getObject("typename", JmmNode.class))));

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        var methods = classDecl.getChildren("Method");

        methods.forEach(method -> map.put(method.get("name"), buildMethodArguments(method)));

        return map;
    }

    private static List<Symbol> buildMethodArguments(JmmNode method) {
        var arguments = method.getChildren("Args");

        if (arguments.size() != 1) {
            throw new RuntimeException("Expected exactly one Args node in method declaration.");
        }

        arguments = arguments.get(0).getChildren("Parameter");

        return arguments.stream().map(
                arg -> new Symbol(
                        buildType(arg.getObject("typename", JmmNode.class)),
                        arg.get("name")
                )
        ).toList();
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();
        var methods = classDecl.getChildren("Method");

        methods.forEach(
                method -> map.put(method.get("name"), getLocalsList(method))
        );

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {
        return classDecl.getChildren("Method").stream().map(node -> node.get("name")).toList();
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        var scope = methodDecl.getChildren("ScopeStmt").get(0);
        System.out.println(scope.toTree());
        var locals = scope.getChildren("Variable");

        return locals.stream().map(node -> new Symbol(
                buildType(node.getObject("typename", JmmNode.class)),
                node.get("name"))).toList();
    }

    private static Type buildType(JmmNode node) {
        return new Type(
                node.get("name"),
                node.get("isArray").equals("true")
        );
    }
}
