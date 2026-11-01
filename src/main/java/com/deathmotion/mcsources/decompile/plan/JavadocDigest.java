package com.deathmotion.mcsources.decompile.plan;

import com.deathmotion.mcsources.decompile.javadoc.JavadocSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class JavadocDigest {
    private JavadocDigest() {
    }

    static String of(JavadocSource source, List<String> members, Function<String, byte[]> bytes) {
        if (source == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (String owner : members) {
            append(text, "C", owner, source.classDoc(owner));
            Members declared = Members.of(bytes.apply(owner));
            for (String field : declared.fields) {
                append(text, "F", owner + "#" + field, source.fieldDoc(owner, field));
            }
            for (String[] method : declared.methods) {
                append(text, "M", owner + "#" + method[0] + method[1], source.methodDoc(owner, method[0], method[1]));
            }
        }
        return text.toString();
    }

    private static void append(StringBuilder text, String kind, String key, String doc) {
        if (doc != null) {
            text.append(kind).append(' ').append(key).append(' ').append(doc).append('\n');
        }
    }

    private static final class Members extends ClassVisitor {
        private final List<String> fields = new ArrayList<>();
        private final List<String[]> methods = new ArrayList<>();

        private Members() {
            super(Opcodes.ASM9);
        }

        private static Members of(byte[] bytes) {
            Members members = new Members();
            new ClassReader(bytes).accept(members,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return members;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            fields.add(name);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            methods.add(new String[]{name, descriptor});
            return null;
        }
    }
}
