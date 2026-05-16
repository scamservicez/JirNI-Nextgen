package com.javaexternal.jvm;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public  class FieldInfo17 {

    public static final int FIELDINFO_TAG_SIZE = 2;

    public static final int ACCESS_FLAGS_OFFSET   = 0;
    public static final int NAME_INDEX_OFFSET     = 1;
    public static final int SIGNATURE_INDEX_OFFSET = 2;
    public static final int INITVAL_INDEX_OFFSET  = 3;
    public static final int LOW_PACKED_OFFSET     = 4;
    public static final int HIGH_PACKED_OFFSET    = 5;
    public static final int FIELD_SLOTS           = 6;

    public static final long SIZE_BYTES = FIELD_SLOTS * 2L;

    public static final MemoryLayout LAYOUT = MemoryLayout.sequenceLayout(
            FIELD_SLOTS, ValueLayout.JAVA_SHORT);

    public final int[] shorts = new int[FIELD_SLOTS];

    public int nameIndex()      { return shorts[NAME_INDEX_OFFSET]; }
    public int signatureIndex() { return shorts[SIGNATURE_INDEX_OFFSET]; }

    private static int buildIntFromShorts(int low, int high) {
        return (high << 16) | (low & 0xFFFF);
    }

    public int offset() {
        return buildIntFromShorts(shorts[LOW_PACKED_OFFSET], shorts[HIGH_PACKED_OFFSET])
                >> FIELDINFO_TAG_SIZE;
    }

    public static FieldInfo17 fromSegment(MemorySegment seg) {
        FieldInfo17 f = new FieldInfo17();
        for (int i = 0; i < FIELD_SLOTS; i++) {
            f.shorts[i] = seg.get(ValueLayout.JAVA_SHORT, i * 2L) & 0xFFFF;
        }
        return f;
    }
}
