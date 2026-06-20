package com.deathmotion.mcsources.decompile;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;

final class JavadocSourceProvider implements IFabricJavadocProvider {
    private final JavadocSource source;

    JavadocSourceProvider(JavadocSource source) {
        this.source = source;
    }

    @Override
    public String getClassDoc(StructClass structClass) {
        return source.classDoc(structClass.qualifiedName);
    }

    @Override
    public String getFieldDoc(StructClass structClass, StructField field) {
        return source.fieldDoc(structClass.qualifiedName, field.getName());
    }

    @Override
    public String getMethodDoc(StructClass structClass, StructMethod method) {
        return source.methodDoc(structClass.qualifiedName, method.getName(), method.getDescriptor());
    }
}
