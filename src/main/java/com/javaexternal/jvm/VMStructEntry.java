package com.javaexternal.jvm;


public class VMStructEntry {

    public String typeName;     // example: "Klass"
    public String fieldName;    // example: "_name"
    public String typeString;   // example: "Symbol*"
    public int    isStatic;     // 0 = offset, 1 = absolute address
    public long   offset;       // for non-static fields
    public long   address;      // for static fields

    /**
     * <pre>
     *   char*    typeName     8
     *   char*    fieldName    8
     *   char*    typeString   8
     *   int32_t  isStatic     4
     *   (pad)                 4
     *   uint64_t offset       8
     *   void*    address      8
     *   total               48
     * </pre>
     */
    public static final long NATIVE_SIZE = 48L;
}
