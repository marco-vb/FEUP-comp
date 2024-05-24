package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2024.optimization.OptUtils;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.rmi.server.ExportException;
import java.util.*;
import java.util.stream.Collectors;

import static org.specs.comp.ollir.ElementType.*;
import static org.specs.comp.ollir.OperationType.*;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "    ";
    private static final Set<OperationType> comparators = Set.of(
            LTE, LTH, GTH, GTE, EQ, NEQ
    );

    private final OllirResult ollirResult;
    private final HashMap<String, String> importFullNames = new HashMap<>();
    private final FunctionClassMap<TreeNode, String> generators;
    List<Report> reports;
    String code;
    Method currentMethod;
    private int stack = 0;
    private int maxStack = 0;
    private int maxLocals = 0;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::getOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(CondBranchInstruction.class, this::generateConditional);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(Instruction.class, this::generateInstruction);
    }

    private static String fieldClassAndName(Operand opClass, Operand field) {
        var className = opClass.toElement().getType();
        var name = className.toString();
        name = name.substring(name.lastIndexOf("(") + 1, name.length() - 1);
        return name + "/" + field.getName();
    }

    private void updateStack(int inc) {
        stack += inc;
        if (stack < 0) System.out.println("ERROR in stack size");
        maxStack = Math.max(maxStack, stack);
    }

    private void updateLocal(int vr) {
        maxLocals = Math.max(maxLocals, vr + 1);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return formatJasmin(code);
    }

    private String formatJasmin(String code) {
        var lines = code.split("\n");

        var formatted = new StringBuilder();

        var indent = 0;
        for (var line : lines) {
            if (line.startsWith(".end")) {
                indent--;
            }

            formatted.append(TAB.repeat(indent)).append(line).append(NL);

            if (line.startsWith(".method")) {
                indent++;
            }
        }

        System.out.println(formatted.toString());
        return formatted.toString();
    }

    private void addImportFullNames(ClassUnit classUnit) {
        for (var imp : classUnit.getImports()) {
            String importNonQualified = imp.substring(imp.lastIndexOf(".") + 1);
            imp = imp.replace(".", "/");
            importFullNames.put(importNonQualified, imp);
        }
    }

    //**
    // * Generates the code for a class unit.
    // * @param classUnit The class unit to generate code for.
    // * @return The generated code.
    // */
    private String generateClassUnit(ClassUnit classUnit) {
        addImportFullNames(classUnit);

        var code = new StringBuilder();

        var modifier = classUnit.getClassAccessModifier() != AccessModifier.DEFAULT ?
                classUnit.getClassAccessModifier().name().toLowerCase() + " " : "public ";

        // generate class name
        var className = classUnit.getClassName();
        code.append(".class ").append(modifier).append(className).append(NL);


        // generate super class
        var superClass = getSuperClassName();
        code.append(".super ").append(superClass).append(NL).append(NL);

        // generate fields
        for (var field : classUnit.getFields()) {
            code.append(getField(field)).append(NL);
        }

        // generate code for constructor and methods
        for (var method : classUnit.getMethods()) {
            if (method.isConstructMethod()) {
                code.append(getConstructor(superClass));
            } else {
                code.append(generators.apply(method));
            }
            code.append(NL).append(NL);
        }

        return code.toString();
    }

    private String getSuperClassName() {
        var superClass = ollirResult.getOllirClass().getSuperClass();

        if (superClass == null || superClass.equals("Object")) {
            superClass = "java/lang/Object";
        }

        superClass = importFullNames.getOrDefault(superClass, superClass);
        return superClass;
    }

    private String getField(Field field) {
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

        // Add type
        var type = generateParam(field.getFieldType());
        code.append(type).append(NL);

        return code.toString();
    }

    private String getConstructor(String superClass) {
        return """
                .method public <init>()V
                aload_0
                invokespecial %s/<init>()V
                return
                .end method
                """.formatted(superClass);
    }

    //**
    // * Generates the header of a method. e.g. .method public static main([Ljava/lang/String;)V
    // * @param method The method to generate the header for.
    // * @return The generated header.
    // */
    private String getMethodHeader(Method method) {
        var header = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " : "public ";

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

    private String generateMethod(Method method) {
        // set method
        currentMethod = method;

        var code = new StringBuilder(getMethodHeader(method));
        updateLocal(method.getParams().size());
        var instructions = new StringBuilder();

        for (var inst : method.getInstructions()) {
            var labels = method.getLabels(inst);

            for (var label : labels) {
                instructions.append(label).append(":\n");
            }

            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL, "", NL));

            instructions.append(instCode);

            while (this.stack > 0) {
                instructions.append("pop\n");
                this.stack--;
            }
        }

        code.append(".limit stack ").append(maxStack).append(NL);
        code.append(".limit locals ").append(maxLocals).append(NL);
        code.append(instructions.toString());
        code.append(".end method");

        // unset method
        currentMethod = null;
        this.maxStack = 0;
        this.maxLocals = 0;

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
                String name = classType.getName();
                yield "L" + importFullNames.getOrDefault(name, name) + ";";
            }
            default -> throw new NotImplementedException(type);
        };
    }

    private String generateInstruction(Instruction instruction) {
        System.out.println("Instruction not implemented: " + instruction);
        return "";
    }

    private String generateGetField(GetFieldInstruction instruction) {
        var code = new StringBuilder();

        int vr = currentMethod.getVarTable().get(instruction.getObject().getName()).getVirtualReg();
        updateLocal(vr);

        String aload = vr <= 3 ? "aload_" : "aload ";

        // push object onto the stack
        code.append(aload).append(vr).append(NL);

        updateStack(1);

        // get value from the field
        code.append("getfield ");

        code.append(fieldClassAndName(instruction.getObject(), instruction.getField()));
        code.append(" ");

        // Add return type
        var returnType = generateParam(instruction.getField().getType());
        code.append(returnType).append(NL);

        return code.toString();
    }

    private String generatePutField(PutFieldInstruction instruction) {
        var code = new StringBuilder();

        int vr = currentMethod.getVarTable().get(instruction.getObject().getName()).getVirtualReg();
        updateLocal(vr);

        String aload = vr <= 3 ? "aload_" : "aload ";

        // push object onto the stack
        code.append(aload).append(vr).append(NL);
        updateStack(1);

        // push value onto the stack
        code.append(generators.apply(instruction.getValue()));

        // store value in the field
        code.append("putfield ");

        code.append(fieldClassAndName(instruction.getObject(), instruction.getField()));
        code.append(" ");


        // Add return type
        var returnType = generateParam(instruction.getValue().getType());
        code.append(returnType).append(NL);
        updateStack(-2);

        return code.toString();
    }

    private String generateCall(CallInstruction instruction) {
        CallType type = instruction.getInvocationType();
        return switch (type) {
            case invokestatic, invokespecial, invokevirtual -> generateInvoke(instruction);
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
        updateStack(1);

        return code.toString();
    }

    private String generateArrayLength(CallInstruction instruction) {
        var code = new StringBuilder();
        Operand caller = (Operand) instruction.getCaller();
        int vr = currentMethod.getVarTable().get(caller.getName()).getVirtualReg();

        String aload = vr <= 3 ? "aload_" : "aload ";
        // push array to the stack
        code.append(aload).append(vr).append(NL);
        // get array length
        code.append("arraylength\n");

        updateLocal(vr);
        updateStack(1);


        return code.toString();
    }

    private String generateNew(CallInstruction instruction) {
        Type returnType = instruction.getReturnType();

        if (returnType instanceof ArrayType) {
            return generateNewArray(instruction);
        }

        var code = new StringBuilder();

        // generate code for calling method
        var caller = instruction.getCaller().toString();
        var name = caller.substring(caller.lastIndexOf("(") + 1, caller.length() - 1);
        name = importFullNames.getOrDefault(name, name);
        code.append("new ").append(name).append(NL);

        updateStack(1);

        return code.toString();
    }

    private String generateNewArray(CallInstruction instruction) {
        Element argument = instruction.getArguments().get(0);
        updateStack(1);
        updateStack(-1);

        return getOperand(argument) + "newarray int\n";
    }

    private String getInvokeVirtual(CallInstruction instruction, Type type) {
        var code = new StringBuilder();
        var className = type.toString();
        className = className.substring(className.lastIndexOf("(") + 1, className.length() - 1);
        className = importFullNames.getOrDefault(className, className);

        code.append("invokevirtual ").append(className).append("/");

        var methodName = ((LiteralElement) instruction.getMethodName()).getLiteral();
        methodName = methodName.substring(1, methodName.length() - 1);

        code.append(methodName);
        return code.toString();
    }

    private String getInvokeStatic(CallInstruction instruction, String caller) {
        var code = new StringBuilder();
        caller = importFullNames.getOrDefault(caller, caller);
        code.append("invokestatic ").append(caller).append("/");

        var methodName = ((LiteralElement) instruction.getMethodName()).getLiteral();
        methodName = methodName.substring(1, methodName.length() - 1);

        code.append(methodName);
        return code.toString();
    }

    private String getInvokeSpecial(Type type) {
        // This method is hardcoded for constructors because in JMM
        // constructors don't take arguments and are the only invokespecial
        // init hard coded because only constructors are invokespecial
        var className = type.toString();
        className = className.substring(className.lastIndexOf("(") + 1, className.length() - 1);
        className = importFullNames.getOrDefault(className, className);

        return "invokespecial " + className + "/<init>";
    }

    private String generateInvoke(CallInstruction instruction) {
        var code = new StringBuilder();

        // push object onto the stack
        Operand caller = (Operand) instruction.getCaller();
        code.append(getOperand(caller));

        int numArgs = instruction.getArguments().size();
        // generate code for loading arguments
        for (var arg : instruction.getArguments()) {
            code.append(getOperand(arg));
        }

        updateStack(-numArgs);

        switch (instruction.getInvocationType()) {
            case invokestatic -> code.append(getInvokeStatic(instruction, caller.getName()));
            case invokespecial -> {
                code.append(getInvokeSpecial(caller.getType()));
                updateStack(-1);
            }
            case invokevirtual -> {
                code.append(getInvokeVirtual(instruction, caller.getType()));
                updateStack(-1);
            }
            default -> throw new NotImplementedException(instruction.getInvocationType());
        }

        code.append("(");

        // generate code for loading arguments
        for (var arg : instruction.getArguments()) {
            code.append(generateParam(arg));
        }

        String returnType = generateParam(instruction.getReturnType());
        code.append(")").append(returnType);
        code.append(NL);

        if (!returnType.equals("V")) {
            updateStack(1);
        }

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        if ((assign.getDest()) instanceof ArrayOperand arrayOperand) {
            code.append(generateArrayAssigned(assign, arrayOperand));
        } else {
            if (canBeIncInst(assign)) {
                return generateIIncInst((BinaryOpInstruction) assign.getRhs());
            }
            // generate code for loading what's on the right
            code.append(generators.apply(assign.getRhs()));

            // store value in the stack in destination
            var lhs = (Operand) assign.getDest();
            code.append(generateAssigned(lhs));
        }

        return code.toString();
    }

    private String generateArrayAssigned(AssignInstruction inst, ArrayOperand operand) {
        var variable = currentMethod.getVarTable().get(operand.getName());
        var reg = variable.getVirtualReg();
        updateLocal(reg);

        StringBuilder code = new StringBuilder();

        String aload = reg <= 3 ? "aload_" : "aload ";
        code.append(aload).append(reg).append(NL);
        updateStack(1);

        var offset = operand.getIndexOperands().get(0);
        code.append(getOperand(offset));
        // stack updated in getOperand

        // generate code for loading what's on the right
        code.append(generators.apply(inst.getRhs()));
        // stack updated in generator

        code.append("iastore\n");

        updateStack(-3);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        updateStack(1);
        int n = Integer.parseInt(literal.getLiteral());

        if (n == -1) return "iconst_m1" + NL;
        if (n >= 0 && n <= 5) return "iconst_" + n + NL;
        if (n >= Byte.MIN_VALUE && n <= Byte.MAX_VALUE) return "bipush " + n + NL;
        if (n >= Short.MIN_VALUE && n <= Short.MAX_VALUE) return "sipush " + n + NL;

        return "ldc " + n + NL;
    }

    private String getOperand(Element operand) {
        if (operand instanceof ArrayOperand op) {
            return getArrayOperand(op);
        }
        if (operand instanceof Operand op) {
            return getOperand(op);
        }
        return generators.apply(operand);
    }

    private String getArrayOperand(ArrayOperand operand) {
        var variable = currentMethod.getVarTable().get(operand.getName());
        var reg = variable.getVirtualReg();
        StringBuilder code = new StringBuilder();

        updateLocal(reg);
        updateStack(1);

        String aload = reg <= 3 ? "aload_" : "aload ";

        code.append(aload).append(reg).append(NL);
        code.append(getOperand(operand.getIndexOperands().get(0)));
        code.append("iaload\n");

        updateStack(-1);

        return code.toString();
    }

    private String getOperand(Operand operand) {
        if (operand instanceof ArrayOperand op) {
            return getArrayOperand(op);
        }

        var variable = currentMethod.getVarTable().get(operand.getName());

        if (variable == null) {
            return "";
        }

        var reg = variable.getVirtualReg();

        updateLocal(reg);
        updateStack(1);

        return switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                String iload = reg <= 3 ? "iload_" : "iload ";
                yield iload + reg + NL;
            }
            case STRING, ARRAYREF, OBJECTREF, THIS -> {
                String aload = reg <= 3 ? "aload_" : "aload ";
                yield aload + reg + NL;
            }
            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        };
    }

    private String generateAssigned(Operand operand) {
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        updateLocal(reg);
        updateStack(-1);

        return switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                String istore = reg <= 3 ? "istore_" : "istore ";
                yield istore + reg + NL;
            }
            case STRING, ARRAYREF, OBJECTREF -> {
                String astore = reg <= 3 ? "astore_" : "astore ";
                yield astore + reg + NL;
            }
            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        };
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        OperationType type = binaryOp.getOperation().getOpType();
        // apply operation
        var op = switch (type) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case SUB, LTE, LTH, GTH, GTE, EQ, NEQ -> "isub";
            case DIV -> "idiv";
            case XOR -> "ixor";
            case AND, ANDB, NOTB -> "iand";
            case OR, ORB -> "ior";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        if (comparators.contains(type)) {
            String ifInst = switch (type) {
                case LTE -> "ifle ";
                case LTH -> "iflt ";
                case GTE -> "ifge ";
                case GTH -> "ifgt ";
                case EQ -> "ifeq ";
                case NEQ -> "ifne ";
                default -> throw new NotImplementedException(type);
            };

            String trueLabel = OptUtils.getLabel("L_fact");
            String endLabel = OptUtils.getLabel("L_end");

            code.append(ifInst).append(trueLabel).append("\n");
            code.append("iconst_0\ngoto ").append(endLabel).append("\n");
            code.append(trueLabel).append(":\niconst_1\n");
            code.append(endLabel).append(":\n");
        }

        updateStack(-1);

        return code.toString();
    }

    private boolean canBeIncInst(AssignInstruction assign) {
        if (!(assign.getRhs() instanceof BinaryOpInstruction inst)) {
            return false;
        }

        if (!(inst.getOperation().getOpType().equals(ADD) || inst.getOperation().getOpType().equals(SUB))) {
            return false;
        }

        Operand assignee = (Operand) assign.getDest();
        String name = assignee.getName();

        boolean leftLiteral = inst.getLeftOperand().isLiteral();
        boolean rightLiteral = inst.getRightOperand().isLiteral();

        boolean leftInt = inst.getLeftOperand().getType().getTypeOfElement().equals(INT32);
        boolean rightInt = inst.getRightOperand().getType().getTypeOfElement().equals(INT32);

        if (leftLiteral) {
            LiteralElement left = (LiteralElement) inst.getLeftOperand();
            int val = Integer.parseInt(left.getLiteral());
            if (val < Byte.MIN_VALUE || val > Byte.MAX_VALUE || rightLiteral) return false;

            Operand right = (Operand) inst.getRightOperand();
            if (!right.getName().equals(name)) return false;
        }

        if (rightLiteral) {
            LiteralElement right = (LiteralElement) inst.getRightOperand();
            int val = Integer.parseInt(right.getLiteral());
            if (val < Byte.MIN_VALUE || val > Byte.MAX_VALUE) return false;

            Operand left = (Operand) inst.getLeftOperand();
            if (!left.getName().equals(name)) return false;
        }

        return leftInt && rightInt && (leftLiteral ^ rightLiteral);
    }

    private String generateIIncInst(BinaryOpInstruction inst) {
        if (inst.getLeftOperand().isLiteral()) {
            Operand operand = (Operand) inst.getRightOperand();
            var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
            var left = (LiteralElement) inst.getLeftOperand();
            var val = Integer.parseInt(left.getLiteral());

            if (inst.getOperation().getOpType().equals(SUB)) val = -val;

            return "iinc " + reg + " " + val + NL;
        }
        Operand operand = (Operand) inst.getLeftOperand();
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        var right = (LiteralElement) inst.getRightOperand();
        var val = Integer.parseInt(right.getLiteral());
        if (inst.getOperation().getOpType().equals(SUB)) val = -val;

        return "iinc " + reg + " " + val + NL;
    }

    private String generateUnaryOp(UnaryOpInstruction inst) {
        var operand = inst.getOperand();
        String operandCode = generators.apply(operand);
        updateStack(1);     // +1 for iconst_1
        updateStack(-1);    // -2 + 1 for ixor
        return operandCode + "iconst_1\nixor\n";
    }

    private String generateReturn(ReturnInstruction inst) {
        var type = inst.getReturnType().getTypeOfElement();

        String ret = switch (type) {
            case INT32, BOOLEAN -> generators.apply(inst.getOperand()) + "ireturn" + NL;
            case STRING, ARRAYREF, OBJECTREF -> getOperand((Operand) inst.getOperand()) + "areturn" + NL;
            case VOID -> "return" + NL;
            default -> throw new NotImplementedException(type);
        };

        if (type != VOID) {
            updateStack(-1);
        }

        return ret;
    }

    private String generateConditional(CondBranchInstruction inst) {
        if (inst instanceof OpCondInstruction opCondInstruction) {
            return generateOpConditional(opCondInstruction);
        }
        return generateSingleOpConditional((SingleOpCondInstruction) inst);
    }

    private String generateOpConditional(OpCondInstruction inst) {
        StringBuilder code = new StringBuilder();
        Instruction condition = inst.getCondition();

        String ifType = null;

        if (condition instanceof BinaryOpInstruction binaryOp) {
            code.append(generators.apply(condition));
            OperationType operation = binaryOp.getOperation().getOpType();
            ifType = "ifne ";
        } else if (condition instanceof UnaryOpInstruction unaryOp) {
            var operand = unaryOp.getOperand();
            code.append(generators.apply(operand));

            // no need to add 1 and xor, just see if equals 0
            ifType = "ifeq ";
        }

        assert ifType != null;
        updateStack(-1);
        code.append(ifType).append(inst.getLabel());
        return code.toString();
    }

    private String generateSingleOpConditional(SingleOpCondInstruction inst) {
        StringBuilder code = new StringBuilder();
        Instruction condition = inst.getCondition();
        code.append(generators.apply(condition));
        updateStack(-1);
        code.append("ifne ").append(inst.getLabel());

        return code.toString();
    }

    private String generateGoto(GotoInstruction inst) {
        String label = inst.getLabel();

        return "goto " + label;
    }
}
