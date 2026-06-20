package com.deathmotion.mcsources.jar;

import org.objectweb.asm.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ParameterAnnotationFixer {
    private static final int ASM_API = Opcodes.ASM9;

    private ParameterAnnotationFixer() {
    }

    public static void apply(Path jar) throws IOException {
        Path output = jar.resolveSibling(jar.getFileName() + ".fixed");
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(output))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] content = in.readAllBytes();
                if (entry.getName().endsWith(".class")) {
                    content = fix(content);
                }
                out.putNextEntry(new ZipEntry(entry.getName()));
                out.write(content);
                out.closeEntry();
            }
        }
        Files.move(output, jar, StandardCopyOption.REPLACE_EXISTING);
    }

    private static byte[] fix(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassFixer(writer, reader.getClassName(), reader.getAccess()), 0);
        return writer.toByteArray();
    }

    private static final class ClassFixer extends ClassVisitor {
        private final String className;
        private int syntheticCount;
        private String constructorPrefix;

        private ClassFixer(ClassVisitor next, String className, int access) {
            super(ASM_API, next);
            this.className = className;
            if ((access & Opcodes.ACC_ENUM) != 0) {
                this.syntheticCount = 2;
                this.constructorPrefix = "(Ljava/lang/String;I";
            }
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (constructorPrefix == null && name.equals(className) && innerName != null
                    && (access & (Opcodes.ACC_STATIC | Opcodes.ACC_INTERFACE)) == 0) {
                if (outerName != null) {
                    this.syntheticCount = 1;
                    this.constructorPrefix = "(L" + outerName + ";";
                } else {
                    int split = className.lastIndexOf('$');
                    if (split != -1) {
                        this.syntheticCount = 1;
                        this.constructorPrefix = "(L" + className.substring(0, split) + ";";
                    }
                }
            }
            super.visitInnerClass(name, outerName, innerName, access);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (constructorPrefix != null && name.equals("<init>") && descriptor.startsWith(constructorPrefix)) {
                return new ConstructorFixer(delegate, Type.getArgumentCount(descriptor), syntheticCount);
            }
            return delegate;
        }
    }

    private static final class ConstructorFixer extends MethodVisitor {
        private final int parameterCount;
        private final int syntheticCount;
        private boolean fixVisible;
        private boolean fixInvisible;

        private ConstructorFixer(MethodVisitor next, int parameterCount, int syntheticCount) {
            super(ASM_API, next);
            this.parameterCount = parameterCount;
            this.syntheticCount = syntheticCount;
        }

        @Override
        public void visitAnnotableParameterCount(int count, boolean visible) {
            if (count == parameterCount) {
                if (visible) {
                    fixVisible = true;
                } else {
                    fixInvisible = true;
                }
                super.visitAnnotableParameterCount(count - syntheticCount, visible);
            } else {
                super.visitAnnotableParameterCount(count, visible);
            }
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
            boolean fixing = visible ? fixVisible : fixInvisible;
            if (fixing) {
                if (parameter < syntheticCount) {
                    return null;
                }
                return super.visitParameterAnnotation(parameter - syntheticCount, descriptor, visible);
            }
            return super.visitParameterAnnotation(parameter, descriptor, visible);
        }
    }
}
