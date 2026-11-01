package com.deathmotion.mcsources.decompile.plan;

import com.deathmotion.mcsources.io.Hashing;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

record ClassStructure(List<String> supertypesAndOuter, boolean synthetic, Set<String> syntheticMembers,
                      String hash) {
    static final ClassStructure ABSENT = new ClassStructure(List.of(), false, Set.of(), "absent");

    static ClassStructure of(byte[] bytes) {
        Fingerprint fingerprint = new Fingerprint();
        new ClassReader(bytes).accept(fingerprint,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassStructure(List.copyOf(fingerprint.supertypesAndOuter), fingerprint.synthetic,
                Set.copyOf(fingerprint.syntheticMembers), Hashing.sha256Hex(fingerprint.text.toString()));
    }

    static String member(String name, String descriptor) {
        return name + descriptor;
    }

    boolean declaresSynthetic(String name, String descriptor) {
        return syntheticMembers.contains(member(name, descriptor));
    }

    private static final class Fingerprint extends ClassVisitor {
        private final StringBuilder text = new StringBuilder();
        private final List<String> supertypesAndOuter = new ArrayList<>();
        private final Set<String> syntheticMembers = new HashSet<>();
        private boolean synthetic;

        private Fingerprint() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                          String[] interfaces) {
            line("C", access, name, signature, superName, Arrays.toString(interfaces));
            synthetic = (access & Opcodes.ACC_SYNTHETIC) != 0;
            if (superName != null) {
                supertypesAndOuter.add(superName);
            }
            if (interfaces != null) {
                supertypesAndOuter.addAll(Arrays.asList(interfaces));
            }
            int dollar = name.lastIndexOf('$');
            if (dollar > 0) {
                supertypesAndOuter.add(name.substring(0, dollar));
            }
        }

        @Override
        public void visitOuterClass(String owner, String name, String descriptor) {
            line("O", owner, name, descriptor);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            line("I", name, outerName, innerName, access);
        }

        @Override
        public void visitNestHost(String nestHost) {
            line("H", nestHost);
        }

        @Override
        public void visitPermittedSubclass(String permittedSubclass) {
            line("P", permittedSubclass);
        }

        @Override
        public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
            line("R", name, descriptor, signature);
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            line("F", access, name, descriptor, signature, value);
            recordSynthetic(access, name, descriptor);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            line("M", access, name, descriptor, signature, Arrays.toString(exceptions));
            recordSynthetic(access, name, descriptor);
            return null;
        }

        private void recordSynthetic(int access, String name, String descriptor) {
            if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
                syntheticMembers.add(member(name, descriptor));
            }
        }

        private void line(String kind, Object... parts) {
            text.append(kind);
            for (Object part : parts) {
                text.append(' ').append(part);
            }
            text.append('\n');
        }
    }
}
