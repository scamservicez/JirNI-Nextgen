package com.javaexternal.jvm;

public  class ExportFunction {
    public int ordinal;
    public int address;
    public String name;

    public ExportFunction(int ordinal, int address, String name) {
        this.ordinal = ordinal;
        this.address = address;
        this.name = name;
    }
}
