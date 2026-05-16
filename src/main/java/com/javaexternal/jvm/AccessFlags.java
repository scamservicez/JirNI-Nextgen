package com.javaexternal.jvm;

public class AccessFlags {
    public int flags;

    public AccessFlags() { this.flags = 0; }
    public AccessFlags(int flags) { this.flags = flags & 0xFFFF; }
}
