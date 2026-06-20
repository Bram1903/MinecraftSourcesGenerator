package com.deathmotion.mcsources.jar;

import com.deathmotion.mcsources.mapping.parchment.ParchmentData;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ParameterNameInjector {
    private ParameterNameInjector() {
    }

    public static void apply(Path jar, ParchmentData parchment) throws IOException {
        Path output = jar.resolveSibling(jar.getFileName() + ".named");
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(output))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] content = in.readAllBytes();
                if (entry.getName().endsWith(".class")) {
                    content = rewrite(content, parchment);
                }
                out.putNextEntry(new ZipEntry(entry.getName()));
                out.write(content);
                out.closeEntry();
            }
        }
        Files.move(output, jar, StandardCopyOption.REPLACE_EXISTING);
    }

    private static byte[] rewrite(byte[] classBytes, ParchmentData parchment) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        for (MethodNode method : node.methods) {
            method.localVariables = null;
            Type[] arguments = Type.getArgumentTypes(method.desc);
            if (arguments.length == 0) {
                continue;
            }

            Map<Integer, String> parchmentNames = parchment == null
                    ? Map.of()
                    : parchment.parameterNames(node.name, method.name, method.desc);
            boolean hasBody = method.instructions != null && method.instructions.size() > 0;

            List<LocalVariableNode> locals = new ArrayList<>();
            List<ParameterNode> parameters = new ArrayList<>();
            LabelNode start = new LabelNode();
            LabelNode end = new LabelNode();
            Set<String> used = new HashSet<>();
            int localIndex = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;

            for (Type argument : arguments) {
                String name = parchmentNames.get(localIndex);
                if (name == null) {
                    name = generateName(argument, used);
                }
                used.add(name);
                parameters.add(new ParameterNode(name, 0));
                if (hasBody) {
                    locals.add(new LocalVariableNode(name, argument.getDescriptor(), null, start, end, localIndex));
                }
                localIndex += argument.getSize();
            }

            method.parameters = parameters;
            if (hasBody) {
                method.instructions.insert(start);
                method.instructions.add(end);
                method.localVariables = locals;
            }
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String generateName(Type type, Set<String> used) {
        String base = baseName(type);
        String name = base;
        int suffix = 1;
        while (used.contains(name)) {
            name = base + (++suffix);
        }
        return name;
    }

    private static String baseName(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN -> "flag";
            case Type.BYTE -> "b";
            case Type.CHAR -> "c";
            case Type.SHORT -> "s";
            case Type.INT -> "i";
            case Type.LONG -> "l";
            case Type.FLOAT -> "f";
            case Type.DOUBLE -> "d";
            case Type.ARRAY -> baseName(type.getElementType()) + "s";
            case Type.OBJECT -> {
                String className = type.getClassName();
                String simple = className.substring(className.lastIndexOf('.') + 1).replace('$', '_');
                yield simple.toLowerCase();
            }
            default -> "value";
        };
    }
}
