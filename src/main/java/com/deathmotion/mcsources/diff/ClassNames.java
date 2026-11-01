package com.deathmotion.mcsources.diff;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ClassNames {
    private static final Pattern ANONYMOUS_SEGMENT = Pattern.compile("\\$\\d");

    private ClassNames() {
    }

    static boolean isAnonymous(String internalName) {
        return ANONYMOUS_SEGMENT.matcher(internalName).find();
    }

    static String packageOf(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? "(default)" : internalName.substring(0, slash).replace('/', '.');
    }

    static String simple(String internalName) {
        if (internalName == null) {
            return "none";
        }
        if (internalName.startsWith("[")) {
            return typeName(internalName);
        }
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }

    static String typeName(Type type) {
        String name = type.getClassName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    static String typeName(String descriptor) {
        return typeName(Type.getType(descriptor));
    }

    static String method(String name, String descriptor) {
        Type type = Type.getMethodType(descriptor);
        List<String> arguments = new ArrayList<>();
        for (Type argument : type.getArgumentTypes()) {
            arguments.add(typeName(argument));
        }
        return name + "(" + String.join(", ", arguments) + ") " + typeName(type.getReturnType());
    }

    static boolean isLambda(MethodNode method) {
        return (method.access & Opcodes.ACC_SYNTHETIC) != 0 && method.name.startsWith("lambda$");
    }

    static String access(int access) {
        List<String> words = new ArrayList<>();
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            words.add("public");
        }
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            words.add("protected");
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            words.add("private");
        }
        if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE)) == 0) {
            words.add("package");
        }
        if ((access & Opcodes.ACC_STATIC) != 0) {
            words.add("static");
        }
        if ((access & Opcodes.ACC_FINAL) != 0) {
            words.add("final");
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            words.add("abstract");
        }
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            words.add("interface");
        }
        if ((access & Opcodes.ACC_ENUM) != 0) {
            words.add("enum");
        }
        if ((access & Opcodes.ACC_RECORD) != 0) {
            words.add("record");
        }
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
            words.add("synthetic");
        }
        return String.join(" ", words);
    }
}
