package com.deathmotion.mcsources.diff;

public record MemberChange(Member member, Change change, String name, String descriptor, String detail) {

    public enum Member {
        FIELD,
        METHOD
    }

    public enum Change {
        ADDED,
        REMOVED,
        BODY,
        DESCRIPTOR,
        RENAMED,
        TYPE,
        VALUE,
        ACCESS,
        SIGNATURE
    }

    public String display() {
        return member == Member.METHOD
                ? ClassNames.method(name, descriptor)
                : name + " " + ClassNames.typeName(descriptor);
    }
}
