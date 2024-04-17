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
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "    ";

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
        generators.put(Field.class, this::generateField);
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

        var modifier = classUnit.getClassAccessModifier() != AccessModifier.DEFAULT ?
                classUnit.getClassAccessModifier().name().toLowerCase() + " " : "public ";

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(modifier).append(className).append(NL);


        // generate super class
        var superClass = ollirResult.getOllirClass().getSuperClass();

        if (Objects.equals(superClass, "Object")) {
            superClass = "java/lang/Object";
        }

        code.append(".super ").append(superClass).append(NL);

        // generate fields
        for (var field : ollirResult.getOllirClass().getFields()) {
            code.append(generators.apply(field));
        }

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

    private String generateField(Field field) {
        var code = new StringBuilder();

        // calculate modifier
        var modifier = field.getFieldAccessModifier() != AccessModifier.DEFAULT ?
                field.getFieldAccessModifier().name().toLowerCase() + " " : "private ";

        if (field.isStaticField()) {
            modifier += "static ";
        }

        if (field.isFinalField()) {
            modifier += "final ";
        }

        code.append(".field ").append(modifier).append(field.getFieldName()).append(" ");

        // Add return type
        var returnType = generateParam(field.getFieldType());
        code.append(returnType).append(NL);

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
        return switch (type) {
            case INT32 -> "I";
            case STRING -> "Ljava/lang/String;";
            case BOOLEAN -> "Z";
            case ARRAYREF -> {
                ArrayType arrayType = (ArrayType) param;
                yield "[" + generateType(arrayType.getElementType());
            }
            case VOID -> "V";
            case OBJECTREF -> {
                ClassType classType = (ClassType) param;
                yield "L" + classType.getName() + ";";
            }
            default -> throw new NotImplementedException(type);
        };
    }

    private String generateInstruction(Instruction instruction) {
        return switch (instruction.getInstType()) {
            case ASSIGN -> generateAssign((AssignInstruction) instruction);
            case UNARYOPER -> generateSingleOp((SingleOpInstruction) instruction);
            case GOTO -> throw new NotImplementedException(instruction.getInstType());
            case BRANCH -> throw new NotImplementedException(instruction.getInstType());
            case RETURN -> generateReturn((ReturnInstruction) instruction);
            case PUTFIELD -> generatePutField((PutFieldInstruction) instruction);
            case GETFIELD -> generateGetField((GetFieldInstruction) instruction);
            case CALL -> generateCall((CallInstruction) instruction);
            case BINARYOPER -> generateBinaryOp((BinaryOpInstruction) instruction);
            case NOPER -> "";
        };
    }

    private String generateGetField(GetFieldInstruction instruction) {
        var code = new StringBuilder();

        // push object onto the stack
        code.append("aload_");
        code.append(currentMethod.getVarTable().get(instruction.getObject().getName()).getVirtualReg());
        code.append(NL);

        // get value from the field
        code.append("getfield ");

        var className = instruction.getObject().toElement().getType();
        var name = className.toString();
        name = name.substring(name.lastIndexOf("(") + 1, name.length() - 1);
        code.append(name).append("/");
        code.append(instruction.getField().getName()).append(" ");

        // Add return type
        var returnType = generateParam(instruction.getField().getType());
        code.append(returnType).append(NL);

        return code.toString();
    }

    private String generatePutField(PutFieldInstruction instruction) {
        var code = new StringBuilder();

        // push object onto the stack
        code.append("aload_");
        code.append(currentMethod.getVarTable().get(instruction.getObject().getName()).getVirtualReg());
        code.append(NL);

        // push value onto the stack
        code.append(generators.apply(instruction.getValue()));

        // store value in the field
        code.append("putfield ");

        var className = instruction.getObject().toElement().getType();
        var name = className.toString();
        name = name.substring(name.lastIndexOf("(") + 1, name.length() - 1);
        code.append(name).append("/");
        code.append(instruction.getField().getName()).append(" ");

        // Add return type
        var returnType = generateParam(instruction.getValue().getType());
        code.append(returnType).append(NL);

        return code.toString();
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

        // push array to the stack
        code.append("aload_");
        code.append(currentMethod.getVarTable().get(instruction.getCaller().toString()).getVirtualReg());
        code.append(NL);

        // get array length
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

        // push object onto the stack
        code.append(generateOperand((Operand) instruction.getCaller()));

        // generate code for loading arguments
        for (var arg : instruction.getArguments()) {
            code.append(generateOperand((Operand) arg));
        }

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

        // push object onto the stack
        var caller = instruction.getCaller();
        code.append(generateOperand((Operand) caller));

        // generate code for loading arguments
        for (var arg : instruction.getArguments()) {
            code.append(generateOperand((Operand) arg));
        }

        // generate code for calling method
        var method = ((LiteralElement) instruction.getMethodName()).getLiteral();
        var methodName = method.substring(1, method.length() - 1);

        var className = caller.getType().toString();
        className = className.substring(className.lastIndexOf("(") + 1, className.length() - 1);

        code.append("invokevirtual ").append(className).append("/");
        code.append(methodName).append("(");

        // generate code for arguments types
        for (var arg : instruction.getArguments()) {
            code.append(generateParam(arg));
        }

        code.append(")").append(generateParam(instruction.getReturnType()));
        code.append(NL);

        return code.toString();
    }

    private String generateInvokeStatic(CallInstruction instruction) {
        var code = new StringBuilder();

        // generate code for loading arguments
        for (var arg : instruction.getArguments()) {
            code.append(generateOperand((Operand) arg));
        }

        // generate code for calling method
        var caller = ((Operand) instruction.getCaller()).getName();
        code.append("invokestatic ").append(caller).append("/");
        var methodName = ((LiteralElement) instruction.getMethodName()).getLiteral();
        methodName = methodName.substring(1, methodName.length() - 1);
        code.append(methodName).append("(");

        // generate code for loading arguments
        for (var arg : instruction.getArguments()) {
            code.append(generateParam(arg));
        }

        code.append(")").append(generateParam(instruction.getReturnType()));
        code.append(NL);

        return code.toString();
    }

    private String generateInvokeSpecial(CallInstruction instruction) {
        // This method is hardcoded for constructors because in JMM
        // constructors don't take arguments and are the only invokespecial
        var code = new StringBuilder();

        // push object onto the stack
        code.append(generateOperand((Operand) instruction.getCaller()));

        // generate code for calling method
        var caller = instruction.getCaller().toString();
        var className = caller.substring(caller.lastIndexOf("(") + 1, caller.length() - 1);
        // init hard coded because only constructors are invokespecial
        code.append("invokespecial ").append(className).append("/<init>");
        code.append("()").append(generateParam(instruction.getReturnType()));
        code.append(NL);

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = (Operand) assign.getDest();
        code.append(generateAssigned(lhs));

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        return switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> "iload_" + reg + NL;
            case STRING, ARRAYREF, OBJECTREF, THIS -> "aload_" + reg + NL;
            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        };
    }

    private String generateAssigned(Operand operand) {
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        return switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> "istore_" + reg + NL;
            case STRING, ARRAYREF, OBJECTREF -> "astore_" + reg + NL;
            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        };
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
            case SUB -> "isub";
            case DIV -> "idiv";
            case SHR -> "ishr";
            case SHL -> "ishl";
            case SHRR -> "iushr";
            case XOR -> "ixor";
            case AND, ANDB, NOTB -> "iand";
            case OR, ORB -> "ior";
            case LTH -> "if_icmplt";
            case GTH -> "if_icmpgt";
            case EQ -> "if_icmpeq";
            case NEQ -> "if_icmpne";
            case LTE -> "if_icmple";
            case GTE -> "if_icmpge";
            case NOT -> "ineg";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction inst) {
        var type = inst.getReturnType().getTypeOfElement();

        return switch (type) {
            case INT32, BOOLEAN -> generators.apply(inst.getOperand()) + "ireturn" + NL;
            case STRING, ARRAYREF, OBJECTREF -> generators.apply(inst.getOperand()) + "areturn" + NL;
            case VOID -> "return" + NL;
            default -> throw new NotImplementedException(type);
        };
    }
}
