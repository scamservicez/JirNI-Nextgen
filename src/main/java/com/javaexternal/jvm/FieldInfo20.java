package com.javaexternal.jvm;

public  class FieldInfo20 {

    public int index;
    public int nameIndex;          // uint16
    public int signatureIndex;     // uint16
    public int offset;             // uint32 (stored as signed int)
    public AccessFlags accessFlags = new AccessFlags();
    public FieldFlags  fieldFlags  = new FieldFlags();
    public int initializerIndex;
    public int genericSignatureIndex;
    public int contentionGroup;

    public int nameIndex()      { return nameIndex; }
    public int signatureIndex() { return signatureIndex; }
    public int offset()         { return offset; }
}
