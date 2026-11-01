package com.deathmotion.mcsources.decompile.vineflower;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class SwitchTables {
    private static final int STATIC_FINAL_SYNTHETIC = Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;

    private SwitchTables() {
    }

    static Set<String> standalone(Map<String, byte[]> classes) {
        return classes.entrySet().parallelStream()
                .filter(entry -> entry.getKey().indexOf('$') < 0 && holdsTable(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean holdsTable(byte[] bytes) {
        Detector detector = new Detector();
        new ClassReader(bytes).accept(detector,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return detector.syntheticClass && detector.table;
    }

    private static final class Detector extends ClassVisitor {
        private boolean syntheticClass;
        private boolean table;

        private Detector() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                          String[] interfaces) {
            syntheticClass = (access & Opcodes.ACC_SYNTHETIC) != 0;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if ((access & STATIC_FINAL_SYNTHETIC) == STATIC_FINAL_SYNTHETIC && descriptor.equals("[I")) {
                table = true;
            }
            return null;
        }
    }
}
