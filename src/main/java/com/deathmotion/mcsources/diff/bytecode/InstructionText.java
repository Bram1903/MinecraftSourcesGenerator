package com.deathmotion.mcsources.diff.bytecode;

import com.deathmotion.mcsources.classfile.InternalNames;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InstructionText {
    private static final Pattern OBJECT_TYPE = Pattern.compile("L([^;]+);");
    private static final Pattern ANONYMOUS_NUMBER = Pattern.compile("\\$\\d+");
    private static final int CONSTANT_TEXT_LIMIT = 60;

    private final ClassNode owner;
    private final String topLevel;
    private final Map<String, MethodNode> lambdas = new HashMap<>();

    InstructionText(ClassNode owner) {
        this.owner = owner;
        this.topLevel = InternalNames.topLevel(owner.name);
        for (MethodNode method : owner.methods) {
            if (ClassNames.isLambda(method)) {
                lambdas.put(method.name + method.desc, method);
            }
        }
    }

    static long fingerprint(CharSequence text) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    long fingerprint(MethodNode method) {
        return fingerprint(render(method));
    }

    String render(MethodNode method) {
        StringBuilder out = new StringBuilder();
        append(method, out, new HashSet<>());
        return out.toString();
    }

    Set<String> references(MethodNode method) {
        Set<String> references = new TreeSet<>();
        collect(method, references, new HashSet<>());
        return references;
    }

    String normalize(String internalName) {
        if (internalName == null) {
            return "*";
        }
        if (internalName.startsWith("[")) {
            return normalizeDescriptor(internalName);
        }
        if (internalName.equals(owner.name)) {
            return "#this";
        }
        String name = internalName;
        if (name.equals(topLevel)) {
            return "#top";
        }
        if (name.startsWith(topLevel + "$")) {
            name = "#top" + name.substring(topLevel.length());
        }
        return ANONYMOUS_NUMBER.matcher(name).replaceAll("\\$#");
    }

    String normalizeDescriptor(String descriptor) {
        if (descriptor == null) {
            return "*";
        }
        Matcher matcher = OBJECT_TYPE.matcher(descriptor);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement("L" + normalize(matcher.group(1)) + ";"));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private void append(MethodNode method, StringBuilder out, Set<String> visiting) {
        String key = method.name + method.desc;
        if (!visiting.add(key)) {
            out.append("<recursion>");
            return;
        }
        Map<LabelNode, Integer> labels = labels(method);
        for (AbstractInsnNode instruction : method.instructions) {
            appendInstruction(instruction, labels, out, visiting);
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            out.append("try L").append(labels.get(block.start))
                    .append(" L").append(labels.get(block.end))
                    .append(" L").append(labels.get(block.handler))
                    .append(' ').append(normalize(block.type)).append('\n');
        }
        visiting.remove(key);
    }

    private static Map<LabelNode, Integer> labels(MethodNode method) {
        Map<LabelNode, Integer> labels = new IdentityHashMap<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LabelNode label) {
                labels.put(label, labels.size());
            }
        }
        return labels;
    }

    private void appendInstruction(AbstractInsnNode instruction, Map<LabelNode, Integer> labels, StringBuilder out,
                                   Set<String> visiting) {
        int opcode = instruction.getOpcode();
        switch (instruction) {
            case LineNumberNode ignored -> {
                return;
            }
            case FrameNode ignored -> {
                return;
            }
            case LabelNode label -> out.append('L').append(labels.get(label));
            case IntInsnNode node -> out.append(opcode).append(' ').append(node.operand);
            case VarInsnNode node -> out.append(opcode).append(' ').append(node.var);
            case TypeInsnNode node -> out.append(opcode).append(' ').append(normalize(node.desc));
            case FieldInsnNode node -> out.append(opcode).append(' ').append(normalize(node.owner)).append('.')
                    .append(node.name).append(' ').append(normalizeDescriptor(node.desc));
            case MethodInsnNode node -> out.append(opcode).append(' ').append(normalize(node.owner)).append('.')
                    .append(node.name).append(normalizeDescriptor(node.desc));
            case InvokeDynamicInsnNode node -> {
                out.append(opcode).append(' ').append(node.name).append(normalizeDescriptor(node.desc)).append(' ');
                appendHandle(node.bsm, out, visiting);
                for (Object argument : node.bsmArgs) {
                    out.append(' ');
                    appendArgument(argument, out, visiting);
                }
            }
            case JumpInsnNode node -> out.append(opcode).append(" L").append(labels.get(node.label));
            case LdcInsnNode node -> {
                out.append(opcode).append(' ');
                appendArgument(node.cst, out, visiting);
            }
            case IincInsnNode node -> out.append(opcode).append(' ').append(node.var).append(' ').append(node.incr);
            case TableSwitchInsnNode node -> {
                out.append(opcode).append(' ').append(node.min).append(' ').append(node.max)
                        .append(" L").append(labels.get(node.dflt));
                for (LabelNode label : node.labels) {
                    out.append(" L").append(labels.get(label));
                }
            }
            case LookupSwitchInsnNode node -> {
                out.append(opcode).append(" L").append(labels.get(node.dflt));
                for (int i = 0; i < node.keys.size(); i++) {
                    out.append(' ').append(node.keys.get(i)).append(":L").append(labels.get(node.labels.get(i)));
                }
            }
            case MultiANewArrayInsnNode node ->
                    out.append(opcode).append(' ').append(normalizeDescriptor(node.desc)).append(' ').append(node.dims);
            default -> out.append(opcode);
        }
        out.append('\n');
    }

    private MethodNode lambdaOf(Handle handle) {
        return handle.getOwner().equals(owner.name) ? lambdas.get(handle.getName() + handle.getDesc()) : null;
    }

    private void appendHandle(Handle handle, StringBuilder out, Set<String> visiting) {
        MethodNode lambda = lambdaOf(handle);
        if (lambda != null) {
            out.append("lambda{\n");
            append(lambda, out, visiting);
            out.append('}');
            return;
        }
        out.append('H').append(handle.getTag()).append(' ').append(normalize(handle.getOwner())).append('.')
                .append(handle.getName()).append(normalizeDescriptor(handle.getDesc()));
    }

    private void appendArgument(Object argument, StringBuilder out, Set<String> visiting) {
        switch (argument) {
            case null -> out.append("null");
            case Handle handle -> appendHandle(handle, out, visiting);
            case Type type -> out.append('T').append(normalizeDescriptor(type.getDescriptor()));
            case ConstantDynamic constant -> {
                out.append("CD ").append(constant.getName()).append(normalizeDescriptor(constant.getDescriptor()))
                        .append(' ');
                appendHandle(constant.getBootstrapMethod(), out, visiting);
                for (int i = 0; i < constant.getBootstrapMethodArgumentCount(); i++) {
                    out.append(' ');
                    appendArgument(constant.getBootstrapMethodArgument(i), out, visiting);
                }
            }
            case String string -> out.append('"').append(string).append('"');
            default -> out.append(argument.getClass().getSimpleName().charAt(0)).append(argument);
        }
    }

    private void collect(MethodNode method, Set<String> out, Set<String> visiting) {
        String key = method.name + method.desc;
        if (!visiting.add(key)) {
            return;
        }
        for (AbstractInsnNode instruction : method.instructions) {
            int opcode = instruction.getOpcode();
            switch (instruction) {
                case MethodInsnNode node -> out.add("call " + ClassNames.simple(node.owner) + "." + node.name);
                case FieldInsnNode node -> out.add((opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC
                        ? "write " : "read ") + ClassNames.simple(node.owner) + "." + node.name);
                case TypeInsnNode node -> {
                    switch (opcode) {
                        case Opcodes.NEW -> out.add("new " + ClassNames.simple(node.desc));
                        case Opcodes.INSTANCEOF -> out.add("instanceof " + ClassNames.simple(node.desc));
                        case Opcodes.CHECKCAST -> out.add("cast " + ClassNames.simple(node.desc));
                        default -> {
                        }
                    }
                }
                case LdcInsnNode node -> out.add("const " + constant(node.cst));
                case IntInsnNode node -> {
                    if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                        out.add("const " + node.operand);
                    }
                }
                case InsnNode ignored -> {
                    String constant = implicitConstant(opcode);
                    if (constant != null) {
                        out.add("const " + constant);
                    }
                }
                case InvokeDynamicInsnNode node -> {
                    for (Object argument : node.bsmArgs) {
                        if (argument instanceof Handle handle) {
                            MethodNode lambda = lambdaOf(handle);
                            if (lambda != null) {
                                collect(lambda, out, visiting);
                            } else {
                                out.add("ref " + ClassNames.simple(handle.getOwner()) + "." + handle.getName());
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }
        visiting.remove(key);
    }

    private static String implicitConstant(int opcode) {
        return switch (opcode) {
            case Opcodes.ICONST_M1 -> "-1";
            case Opcodes.ICONST_0 -> "0";
            case Opcodes.ICONST_1 -> "1";
            case Opcodes.ICONST_2 -> "2";
            case Opcodes.ICONST_3 -> "3";
            case Opcodes.ICONST_4 -> "4";
            case Opcodes.ICONST_5 -> "5";
            case Opcodes.LCONST_0 -> "0L";
            case Opcodes.LCONST_1 -> "1L";
            case Opcodes.FCONST_0 -> "0.0F";
            case Opcodes.FCONST_1 -> "1.0F";
            case Opcodes.FCONST_2 -> "2.0F";
            case Opcodes.DCONST_0 -> "0.0";
            case Opcodes.DCONST_1 -> "1.0";
            default -> null;
        };
    }

    private static String constant(Object value) {
        return switch (value) {
            case Float number -> number + "F";
            case Long number -> number + "L";
            case String string -> "\"" + (string.length() > CONSTANT_TEXT_LIMIT
                    ? string.substring(0, CONSTANT_TEXT_LIMIT) + "..." : string) + "\"";
            case Type type -> ClassNames.typeName(type) + ".class";
            default -> String.valueOf(value);
        };
    }
}
