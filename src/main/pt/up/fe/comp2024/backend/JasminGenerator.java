package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.specs.comp.ollir.ElementType.*;

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

    private int stack = 0;

    private final HashMap<String, String> importFullNames = new HashMap<>();

    private final FunctionClassMap<TreeNode, String> generators;

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

        return formatted.toString();
    }

    private void addImportFullNames(ClassUnit classUnit) {
        for (var imp : classUnit.getImports()) {
            String importNonQualified = imp.substring(imp.lastIndexOf(".") + 1);
            imp = imp.replace(".", "/");
            importFullNames.put(importNonQualified, imp);
        }
    }

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
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(Instruction.class, this::generateInstruction);
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

        // Add limits
        code.append(".limit stack 99").append(NL);
        code.append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL, "", NL));

            if (instCode.contains("return")) {
                while (this.stack > 0) {
                    code.append("pop\n");
                    this.stack--;
                }
            }
            code.append(instCode);
        }

        code.append(".end method");

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

        // push object onto the stack
        code.append("aload ");
        code.append(currentMethod.getVarTable().get(instruction.getObject().getName()).getVirtualReg());
        code.append(NL);

        // get value from the field
        code.append("getfield ");

        code.append(fieldClassAndName(instruction.getObject(), instruction.getField()));
        code.append(" ");

        // Add return type
        var returnType = generateParam(instruction.getField().getType());
        code.append(returnType).append(NL);

        this.stack++;

        return code.toString();
    }

    private String generatePutField(PutFieldInstruction instruction) {
        var code = new StringBuilder();

        // push object onto the stack
        code.append("aload ");
        code.append(currentMethod.getVarTable().get(instruction.getObject().getName()).getVirtualReg());
        code.append(NL);
        this.stack++;

        // push value onto the stack
        code.append(generators.apply(instruction.getValue()));

        // store value in the field
        code.append("putfield ");

        code.append(fieldClassAndName(instruction.getObject(), instruction.getField()));
        code.append(" ");

        // Add return type
        var returnType = generateParam(instruction.getValue().getType());
        code.append(returnType).append(NL);
        this.stack -= 2;

        return code.toString();
    }

    private static String fieldClassAndName(Operand opClass, Operand field) {
        var className = opClass.toElement().getType();
        var name = className.toString();
        name = name.substring(name.lastIndexOf("(") + 1, name.length() - 1);
        return name + "/" + field.getName();
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

        return code.toString();
    }

    private String generateArrayLength(CallInstruction instruction) {
        var code = new StringBuilder();

        // push array to the stack
        code.append("aload ");
        code.append(currentMethod.getVarTable().get(instruction.getCaller().toString()).getVirtualReg());
        code.append(NL);

        // get array length
        code.append("arraylength");
        code.append(NL);

        return code.toString();
    }

    private String generateNew(CallInstruction instruction) {
        var code = new StringBuilder();

        // generate code for calling method
        var caller = instruction.getCaller().toString();
        var name = caller.substring(caller.lastIndexOf("(") + 1, caller.length() - 1);
        name = importFullNames.getOrDefault(name, name);
        code.append("new ").append(name).append(NL);

        return code.toString();
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

        // generate code for loading arguments
        for (var arg : instruction.getArguments()) {
            code.append(getOperand(arg));
            this.stack--;
        }

        switch (instruction.getInvocationType()) {
            case invokestatic -> code.append(getInvokeStatic(instruction, caller.getName()));
            case invokespecial -> {
                code.append(getInvokeSpecial(caller.getType()));
                this.stack--;
            }
            case invokevirtual -> {
                code.append(getInvokeVirtual(instruction, caller.getType()));
                this.stack--;
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
            this.stack++;
        }

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
        this.stack++;
        return "ldc " + literal.getLiteral() + NL;
    }

    private String getOperand(Element operand) {
        if (operand instanceof Operand) {
            return getOperand((Operand) operand);
        } else {
            return generators.apply(operand);
        }
    }
    private String getOperand(Operand operand) {
        var variable = currentMethod.getVarTable().get(operand.getName());

        if (variable == null) {
            return "";
        }

        var reg = variable.getVirtualReg();

        this.stack++;

        return switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> "iload " + reg + NL;
            case STRING, ARRAYREF, OBJECTREF, THIS -> "aload " + reg + NL;
            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        };
    }

    private String generateAssigned(Operand operand) {
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        this.stack--;

        return switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> "istore " + reg + NL;
            case STRING, ARRAYREF, OBJECTREF -> "astore " + reg + NL;
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
            case XOR -> "ixor";
            case AND, ANDB, NOTB -> "iand";
            case OR, ORB -> "ior";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        this.stack--;

        return code.toString();
    }

    private String generateReturn(ReturnInstruction inst) {
        var type = inst.getReturnType().getTypeOfElement();

        if (type != VOID) {
            this.stack--;
        }

        return switch (type) {
            case INT32, BOOLEAN -> generators.apply(inst.getOperand()) + "ireturn" + NL;
            case STRING, ARRAYREF, OBJECTREF -> getOperand((Operand) inst.getOperand()) + "areturn" + NL;
            case VOID -> "return" + NL;
            default -> throw new NotImplementedException(type);
        };
    }
}
