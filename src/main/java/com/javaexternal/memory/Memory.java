package com.javaexternal.memory;

import com.javaexternal.win32.Win32;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;


public class Memory {

    public static volatile MemorySegment targetHandle = MemorySegment.NULL;
    public static volatile long jvm = 0L;

    public static int initAttachToClient(MemorySegment handle, long jvmBasePointer) {
        targetHandle = handle;
        jvm = jvmBasePointer;
        return 0;
    }

    public static MemorySegment getHandle() {
        return targetHandle;
    }

    public static boolean readMemory0(MemorySegment buf, long address, long size,
                                      MemorySegment bytesReadOut) {
        return Win32.readProcessMemory(targetHandle, address, buf, size, bytesReadOut) != 0;
    }

    public static boolean readMemory(MemorySegment buf, long address, long size) {
        return readMemory0(buf, address, size, null);
    }


    public static MemorySegment readMemory(Arena arena, long address, long size) {
        MemorySegment seg = arena.allocate(size);
        readMemory0(seg, address, size, null);
        return seg;
    }

    public static byte readByte(long address) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(ValueLayout.JAVA_BYTE);
            return readMemory0(s, address, 1, null) ? s.get(ValueLayout.JAVA_BYTE, 0) : 0;
        }
    }

    public static short readShort(long address) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(ValueLayout.JAVA_SHORT);
            return readMemory0(s, address, 2, null) ? s.get(ValueLayout.JAVA_SHORT, 0) : 0;
        }
    }

    public static int readInt(long address) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(ValueLayout.JAVA_INT);
            return readMemory0(s, address, 4, null) ? s.get(ValueLayout.JAVA_INT, 0) : 0;
        }
    }

    public static long readLong(long address) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(ValueLayout.JAVA_LONG);
            return readMemory0(s, address, 8, null) ? s.get(ValueLayout.JAVA_LONG, 0) : 0L;
        }
    }

    public static float readFloat(long address) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(ValueLayout.JAVA_FLOAT);
            return readMemory0(s, address, 4, null) ? s.get(ValueLayout.JAVA_FLOAT, 0) : 0f;
        }
    }

    public static double readDouble(long address) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(ValueLayout.JAVA_DOUBLE);
            return readMemory0(s, address, 8, null) ? s.get(ValueLayout.JAVA_DOUBLE, 0) : 0d;
        }
    }

    public static long readPointer(long address) {
        return readLong(address);
    }

    public static byte[] readBytes(long address, int size) {
        if (size <= 0) return new byte[0];
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(size);
            if (!readMemory0(s, address, size, null)) return new byte[size];
            return s.toArray(ValueLayout.JAVA_BYTE);
        }
    }

    public static String readCString(long address, int maxLen) {
        byte[] raw = readBytes(address, maxLen);
        int end = 0;
        while (end < raw.length && raw[end] != 0) end++;
        return new String(raw, 0, end, StandardCharsets.UTF_8);
    }

    public static boolean writeMemory(MemorySegment buf, long address, long size) {
        if (Win32.writeProcessMemory(targetHandle, address, buf, size, null) == 0) {
            buf.fill((byte) 0);
            return false;
        }
        return true;
    }

    public static boolean writeByte(long address, byte v) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(ValueLayout.JAVA_BYTE);
            s.set(ValueLayout.JAVA_BYTE, 0, v);
            return writeMemory(s, address, 1);
        }
    }

    public static boolean writeShort(long address, short v) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(ValueLayout.JAVA_SHORT);
            s.set(ValueLayout.JAVA_SHORT, 0, v);
            return writeMemory(s, address, 2);
        }
    }

    public static boolean writeInt(long address, int v) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(ValueLayout.JAVA_INT);
            s.set(ValueLayout.JAVA_INT, 0, v);
            return writeMemory(s, address, 4);
        }
    }

    public static boolean writeLong(long address, long v) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(ValueLayout.JAVA_LONG);
            s.set(ValueLayout.JAVA_LONG, 0, v);
            return writeMemory(s, address, 8);
        }
    }

    public static boolean writeBytes(long address, byte[] data) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(data.length);
            MemorySegment.copy(data, 0, s, ValueLayout.JAVA_BYTE, 0, data.length);
            return writeMemory(s, address, data.length);
        }
    }
}
