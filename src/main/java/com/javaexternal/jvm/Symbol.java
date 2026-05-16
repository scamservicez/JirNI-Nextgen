package com.javaexternal.jvm;

import com.javaexternal.memory.Memory;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;


public class Symbol {

    public static final int BODY_CAPACITY = 255;
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("_hash_and_refcount"),
            ValueLayout.JAVA_SHORT.withName("_length"),
            MemoryLayout.sequenceLayout(BODY_CAPACITY, ValueLayout.JAVA_BYTE).withName("_body")
    ).withName("Symbol");

    public static final long HEADER_SIZE = 6L; // 4 + 2

    public int hashAndRefcount;
    public int length;          // logical 0..65535 (stored unsigned)
    public final byte[] body = new byte[BODY_CAPACITY];

    public String asString() {
        if (length <= 0) return "";
        int len = Math.min(length, BODY_CAPACITY);
        return new String(body, 0, len, StandardCharsets.UTF_8);
    }

    public byte byteAt(int idx) {
        return body[idx];
    }

    public boolean isValid() {
        return length > 0;
    }

    public static Symbol fromSegment(MemorySegment seg) {
        Symbol s = new Symbol();
        s.hashAndRefcount = seg.get(ValueLayout.JAVA_INT, 0);
        s.length = seg.get(ValueLayout.JAVA_SHORT, 4) & 0xFFFF;
        long bodyBytes = Math.min(BODY_CAPACITY, seg.byteSize() - HEADER_SIZE);
        if (bodyBytes > 0) {
            MemorySegment.copy(seg, ValueLayout.JAVA_BYTE, HEADER_SIZE,
                    s.body, 0, (int) bodyBytes);
        }
        return s;
    }


    public static Symbol fromAddress(long address) {
        long pointer = Memory.readPointer(address);
        if (pointer == 0L) return new Symbol();
        // Match the C fallback: try full size first, fall back to 32 bytes.
        byte[] full = Memory.readBytes(pointer, BODY_CAPACITY + (int) HEADER_SIZE);
        if (full.length == 0) {
            full = Memory.readBytes(pointer, 32);
        }
        Symbol s = new Symbol();
        if (full.length >= 6) {
            s.hashAndRefcount =
                    ((full[0] & 0xFF))
                  | ((full[1] & 0xFF) << 8)
                  | ((full[2] & 0xFF) << 16)
                  | ((full[3] & 0xFF) << 24);
            s.length = (full[4] & 0xFF) | ((full[5] & 0xFF) << 8);
            int copy = Math.min(BODY_CAPACITY, full.length - 6);
            System.arraycopy(full, 6, s.body, 0, Math.max(0, copy));
        }
        return s;
    }
}
