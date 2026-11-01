package com.deathmotion.mcsources.decompile.plan;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashSet;
import java.util.Set;

record References(Set<String> types, Set<Member> members) {

    static References of(byte[] bytes) {
        Set<String> types = new HashSet<>();
        Set<Member> members = new HashSet<>();
        Remapper collector = new Remapper() {
            @Override
            public String map(String internalName) {
                types.add(internalName);
                return internalName;
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                members.add(new Member(owner, name, descriptor));
                return name;
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                members.add(new Member(owner, name, descriptor));
                return name;
            }
        };
        new ClassReader(bytes).accept(new ClassRemapper(new ClassNode(), collector), 0);
        return new References(types, members);
    }

    record Member(String owner, String name, String descriptor) {
    }
}
