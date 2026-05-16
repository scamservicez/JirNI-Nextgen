package com.javaexternal.jvm;

import java.nio.charset.StandardCharsets;
public  class JavaLang {

    public static String jString2String(long obj) {
        long strKlass = Jvm.findClass("java/lang/String");
        int valuesField = Jvm.findLocalField(strKlass, "value", "[B");
        long values = Jvm.getObjectField(obj, valuesField);

        int size = Jvm.getArrayLength(values);
        byte[] bytes = Jvm.getByteArrayElement(values, 0, size);
        return new String(bytes, 0, Math.min(bytes.length, size), StandardCharsets.UTF_8);
    }
}
