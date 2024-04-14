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


    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    //**
    // * Generates the code for a class unit.
    // * @param classUnit The class unit to generate code for.
    // * @return The generated code.
    // */
    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL);


        // generate super class
        var superClass = ollirResult.getOllirClass().getSuperClass();

        if (superClass == null) {
            superClass = "java/lang/Object";
        }

        code.append(".super ").append(superClass).append(NL);


        // generate code for constructor and methods
        for (var method : ollirResult.getOllirClass().getMethods()) {
            code.append(NL);
            if (method.isConstructMethod()) {
                code.append(generateConstructor(method, superClass));
            } else {
                code.append(generators.apply(method));
            }
            code.append(NL);
        }

        return code.toString();
    }

    //**
    // * Generates the header of a method. e.g. .method public static main([Ljava/lang/String;)V
    // * @param method The method to generate the header for.
    // * @return The generated header.
    // */
    private String generateMethodHeader(Method method) {
        var header = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " : "";

        if (method.isStaticMethod()) {
            modifier += "static ";
        }

        if (method.isFinalMethod()) {
            modifier += "final ";
        }

        header.append(".method ").append(modifier).append(method.getMethodName());

        // Add parameters
        var params = method.getParams().stream()
                .map(this::generateParam).toList();

        header.append("(").append(String.join("", params)).append(")");

        // Add return type
        var returnType = generateParam(method.getReturnType());
        header.append(returnType).append(NL);

        return header.toString();
    }


    private String generateConstructor(Method constructor, String superClass) {
        var code = new StringBuilder();

        // generate constructor header
        code.append(".method public <init>");

        // Add parameters
        var params = constructor.getParams().stream()
                .map(this::generateParam).toList();

        code.append("(").append(String.join("", params)).append(")");

        // Add return type
        var returnType = generateParam(constructor.getReturnType());
        code.append(returnType).append(NL);

        // generate code for calling super constructor
        code.append(TAB).append("aload_0").append(NL);
        code.append(TAB).append("invokespecial ").append(superClass).append("/<init>()V").append(NL);

        code.append(TAB).append("return").append(NL);
        code.append(".end method").append(NL).append(NL);

        return code.toString();
    }

    private String generateMethod(Method method) {
        // set method
        currentMethod = method;

        var code = new StringBuilder(generateMethodHeader(method));

        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }

        code.append(".end method").append(NL).append(NL);

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateParam(Element param) {
        Type type = param.getType();
        return generateType(type);
    }

    private String generateParam(Type param) {
        return generateType(param);
    }

    private String generateType(Type param) {
        ElementType type = param.getTypeOfElement();
        switch (type) {
            case INT32 -> {
                return "I";
            }
            case STRING -> {
                return "Ljava/lang/String;";
            }
            case BOOLEAN -> {
                return "Z";
            }
            case ARRAYREF -> {
                ArrayType arrayType = (ArrayType) param;
                return "[" + generateType(arrayType.getElementType());
            }
            case VOID -> {
                return "V";
            }
            case OBJECTREF -> {
                ClassType classType = (ClassType) param;
                return "L" + classType.getName() + ";";
            }
            default -> {
                System.out.println("Not implemented type: " + type);
                 throw new NotImplementedException(type);
            }
        }
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
        CallType type = instruction.getInvocationType();
        return switch (type) {
            case invokestatic -> generateInvokeStatic(instruction);
            case invokespecial -> generateInvokeSpecial(instruction);
            case invokevirtual -> generateInvokeVirtual(instruction);
            case invokeinterface -> generateInvokeInterface(instruction);
            case NEW -> generateNew(instruction);
            case arraylength -> generateArrayLength(instruction);
            case ldc -> generateLoadConstant(instruction);
            default -> throw new NotImplementedException(type);
        };
    }

    private String generateLoadConstant(CallInstruction instruction) {
        var code = new StringBuilder();

        // generate code for calling method
        var caller = instruction.getCaller().toString();
        var name = caller.substring(caller.lastIndexOf("(") + 1, caller.length() - 1);
        code.append("ldc ").append(name).append(NL);

        return code.toString();
    }

    private String generateArrayLength(CallInstruction instruction) {
        var code = new StringBuilder();

        // generate code for calling method
        var caller = instruction.getCaller().toString();
        var name = caller.substring(caller.lastIndexOf("(") + 1, caller.length() - 1);
        code.append("arraylength").append(NL);

        return code.toString();
    }

    private String generateNew(CallInstruction instruction) {
        var code = new StringBuilder();

        // generate code for calling method
        var caller = instruction.getCaller().toString();
        var name = caller.substring(caller.lastIndexOf("(") + 1, caller.length() - 1);
        code.append("new ").append(name).append(NL);
        code.append("dup").append(NL);

        return code.toString();
    }

    private String generateInvokeInterface(CallInstruction instruction) {
        var code = new StringBuilder();

        // generate code for calling method
        var caller = instruction.getCaller().toString();
        var name = caller.substring(caller.lastIndexOf("(") + 1, caller.length() - 1);
        code.append("invokeinterface ").append(name).append("(");

        // generate code for loading arguments
        for (var arg : instruction.getArguments()) {
            code.append(generateParam(arg));
        }

        code.append(")").append(generateParam(instruction.getReturnType()));

        return code.toString();
    }

    private String generateInvokeVirtual(CallInstruction instruction) {
        var code = new StringBuilder();

        // generate code for calling method
        var caller = instruction.getCaller().toString();
        var name = caller.substring(caller.lastIndexOf("(") + 1, caller.length() - 1);
        code.append("invokevirtual ").append(name).append("(");

        // generate code for loading arguments
        for (var arg : instruction.getArguments()) {
            code.append(generateParam(arg));
        }

        code.append(")").append(generateParam(instruction.getReturnType()));

        return code.toString();
    }

    private String generateInvokeStatic(CallInstruction instruction) {
        var code = new StringBuilder();

        // generate code for calling method
        var caller = instruction.getCaller().toString();
        var name = caller.substring(caller.lastIndexOf("(") + 1, caller.length() - 1);
        code.append("invokestatic ").append(name).append("(");

        // generate code for loading arguments
        for (var arg : instruction.getArguments()) {
            code.append(generateParam(arg));
        }

        code.append(")").append(generateParam(instruction.getReturnType()));

        return code.toString();
    }

    private String generateInvokeSpecial(CallInstruction instruction) {
        var code = new StringBuilder();

        // generate code for calling method
        var caller = instruction.getCaller().toString();
        var name = caller.substring(caller.lastIndexOf("(") + 1, caller.length() - 1);

        // init hard coded because only constructors are invokespecial
        code.append("invokespecial ").append(name).append("/").append("<init>");
        code.append("(");

        // generate code for loading arguments
        for (var arg : instruction.getArguments()) {
            code.append(generateParam(arg));
        }

        code.append(")").append(generateParam(instruction.getReturnType()));
        code.append(NL);

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
//            throw new NotImplementedException(lhs.getClass());
            System.out.println("Not implemented type: " + lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> code.append("istore_").append(reg).append(NL);
            case STRING, ARRAYREF, OBJECTREF -> code.append("astore_").append(reg).append(NL);
            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        }

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
