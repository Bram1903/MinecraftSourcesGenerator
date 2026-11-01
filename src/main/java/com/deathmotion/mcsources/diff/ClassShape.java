package com.deathmotion.mcsources.diff;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

record ClassShape(String name,
                  int access,
                  String superName,
                  List<String> interfaces,
                  String signature,
                  List<String> permittedSubclasses,
                  Map<String, FieldShape> fields,
                  Map<String, MethodShape> methods,
                  long fingerprint) {

    static final int HEADER_ACCESS_MASK = ~(Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC);

    record FieldShape(String name, String descriptor, int access, String signature, Object value) {
    }

    record MethodShape(String name, String descriptor, int access, String signature, long fingerprint, int size) {
        String key() {
            return name + descriptor;
        }
    }

    static ClassShape of(ClassNode node) {
        InstructionText text = new InstructionText(node);
        StringBuilder identity = new StringBuilder()
                .append(node.access & HEADER_ACCESS_MASK).append(' ')
                .append(text.normalize(node.superName)).append('\n');
        for (String implemented : node.interfaces) {
            identity.append("implements ").append(text.normalize(implemented)).append('\n');
        }

        Map<String, FieldShape> fields = new TreeMap<>();
        for (FieldNode field : node.fields) {
            fields.put(field.name, new FieldShape(field.name, field.desc, field.access, field.signature, field.value));
            identity.append("field ").append(field.name).append(' ').append(text.normalizeDescriptor(field.desc))
                    .append(' ').append(field.access).append(' ').append(field.value).append('\n');
        }

        Map<String, MethodShape> methods = new TreeMap<>();
        for (MethodNode method : node.methods) {
            if (ClassNames.isLambda(method)) {
                continue;
            }
            long fingerprint = text.fingerprint(method);
            methods.put(method.name + method.desc, new MethodShape(method.name, method.desc, method.access,
                    method.signature, fingerprint, method.instructions.size()));
        }
        methods.values().forEach(method -> identity.append("method ").append(method.name())
                .append(text.normalizeDescriptor(method.descriptor())).append(' ').append(method.access())
                .append(' ').append(method.fingerprint()).append('\n'));

        List<String> permitted = node.permittedSubclasses == null ? List.of() : List.copyOf(node.permittedSubclasses);
        return new ClassShape(node.name, node.access, node.superName, List.copyOf(node.interfaces), node.signature,
                permitted, fields, methods, InstructionText.fingerprint(identity));
    }
}
