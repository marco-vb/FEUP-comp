package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(Instruction.class, this::generateInstruction);
    }

    private String generateInstruction(Instruction instruction) {
        return switch (instruction.getInstType()) {
//                    ASSIGN,
//                    CALL,
//                    GOTO,
//                    BRANCH,
//                    RETURN,
//                    PUTFIELD,
//                    GETFIELD,
//                    UNARYOPER,
//                    BINARYOPER,
//                    NOPER;
            case ASSIGN -> generateAssign((AssignInstruction) instruction);
            case UNARYOPER -> generateSingleOp((SingleOpInstruction) instruction);
            case GOTO -> throw new NotImplementedException(instruction.getInstType());
            case BRANCH -> throw new NotImplementedException(instruction.getInstType());
            case RETURN -> generateReturn((ReturnInstruction) instruction);
            case PUTFIELD -> throw new NotImplementedException(instruction.getInstType());
            case GETFIELD -> throw new NotImplementedException(instruction.getInstType());
            case CALL -> generateCall((CallInstruction) instruction);
            case BINARYOPER -> generateBinaryOp((BinaryOpInstruction) instruction);
            case NOPER -> "";
        };
    }

    private String generateCall(CallInstruction instruction) {
        var code = new StringBuilder();

        // generate code for loading arguments
        for (var arg : instruction.getArguments()) {
            code.append(generators.apply(arg));
        }

        // generate code for calling method
        code.append("invokestatic ").append(instruction.getMethodName()).append(NL);

        return code.toString();
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            System.out.println(ollirResult.getOllirClass());
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        // TODO: Hardcoded to Object, needs to be expanded
        // verificar nome da classe tem de ser absolute path
        if (ollirResult.getOllirClass().getSuperClass() != null) {
            code.append(".super ").append(ollirResult.getOllirClass().getSuperClass()).append(NL);

            // call constructor of super class
            var superConstructor = """
                ;super constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(ollirResult.getOllirClass().getSuperClass());
            code.append(superConstructor);
        } else {
            code.append(".super java/lang/Object").append(NL);

            // generate a single constructor method
            var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial java/lang/Object/<init>()V
                    return
                .end method
                """;
            code.append(defaultConstructor);
        }


        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }


    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        if (method.isStaticMethod()) {
            modifier += "static ";
        }

        if (method.isFinalMethod()) {
            modifier += "final ";
        }

        var methodName = method.getMethodName();

        // TODO: Hardcoded param types and return type, needs to be expanded
        code.append("\n.method ").append(modifier).append(methodName);

        // Add parameters
        var params = method.getParams().stream()
                .map(this::generateParam).toList();

        code.append("(").append(String.join("", params)).append(")");

        var returnType = generateParam(method.getReturnType());
        code.append(returnType).append(NL);

        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateParam(Element param) {
        ElementType type = param.getType().getTypeOfElement();

        return generateType(type);
    }

    private String generateParam(Type param) {
        ElementType type = param.getTypeOfElement();
        System.out.println(param);

        return generateType(type);
    }

    private String generateType(ElementType type) {
        if (type == ElementType.INT32) {
            return "I";
        } else if (type == ElementType.STRING) {
            return "Ljava/lang/String;";
        } else if (type == ElementType.BOOLEAN) {
            return "Z";
        } else if (type == ElementType.ARRAYREF) {
            return "[Ljava/lang/String;";
        } else if (type == ElementType.VOID) {
            return "V";
        } else {
//             throw new NotImplementedException(type);
            System.out.println("Not implemented type: " + type);
        }
        return "";
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        System.out.println(assign.toTree());

        if (!(lhs instanceof Operand)) {
//            throw new NotImplementedException(lhs.getClass());
            System.out.println("Not implemented type: " + lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        // TODO: Hardcoded for int type, needs to be expanded
        code.append("istore ").append(reg).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        return "iload " + reg + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            default -> "sus"; // throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        // TODO: Hardcoded to int return type, needs to be expanded

        if (returnInst.getReturnType().getTypeOfElement() == ElementType.VOID) {
            code.append("return").append(NL);
            return code.toString();
        }

        code.append(generators.apply(returnInst.getOperand()));
        code.append("ireturn").append(NL);

        return code.toString();
    }

}
