package com.javaexternal.memory;

import com.javaexternal.win32.Win32;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;


public class MemUtil {

    public static long getPointerFromAddress(long address) {
        return Memory.readPointer(address);
    }

    public static int getPidFromProcessName(String name) {
        MemorySegment snap = Win32.createToolhelp32Snapshot(Win32.TH32CS_SNAPPROCESS, 0);
        if (snap.address() == Win32.INVALID_HANDLE_VALUE) return 0;

        try (Arena a = Arena.ofConfined()) {
            MemorySegment entry = a.allocate(Win32.PROCESSENTRY32);
            entry.set(ValueLayout.JAVA_INT,
                    Win32.PROCESSENTRY32_DWSIZE_OFFSET,
                    (int) Win32.PROCESSENTRY32_SIZE);

            boolean ok = Win32.process32First(snap, entry);
            while (ok) {
                String exe = readSzExeFile(entry);
                if (exe.equals(name)) {
                    int pid = entry.get(ValueLayout.JAVA_INT, Win32.PROCESSENTRY32_PID_OFFSET);
                    Win32.closeHandle(snap);
                    return pid;
                }
                ok = Win32.process32Next(snap, entry);
            }
        } finally {
            Win32.closeHandle(snap);
        }
        return 0;
    }

    private static String readSzExeFile(MemorySegment entry) {
        MemorySegment exeSlice =
                entry.asSlice(Win32.PROCESSENTRY32_EXE_OFFSET, Win32.MAX_PATH);
        byte[] bytes = exeSlice.toArray(ValueLayout.JAVA_BYTE);
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) end++;
        return new String(bytes, 0, end, java.nio.charset.StandardCharsets.US_ASCII);
    }

    public static long spoonGetJvmBase(MemorySegment proc) {
        try (Arena a = Arena.ofConfined()) {
            final int max = 128;
            MemorySegment mods = a.allocate(ValueLayout.ADDRESS.byteSize() * max,
                    ValueLayout.ADDRESS.byteAlignment());
            MemorySegment cb   = a.allocate(ValueLayout.JAVA_INT);

            if (!Win32.enumProcessModulesEx(proc, mods, (int) mods.byteSize(), cb,
                    Win32.LIST_MODULES_64BIT)) {
                return 0L;
            }
            int count = cb.get(ValueLayout.JAVA_INT, 0) / (int) ValueLayout.ADDRESS.byteSize();
            MemorySegment nameBuf = a.allocate(255);

            for (int i = 0; i < count; i++) {
                MemorySegment hModule = mods.getAtIndex(ValueLayout.ADDRESS, i);
                if (hModule.address() == 0L) continue;
                nameBuf.fill((byte) 0);
                Win32.getModuleBaseNameA(proc, hModule, nameBuf, 255);
                byte[] raw = nameBuf.toArray(ValueLayout.JAVA_BYTE);
                int end = 0;
                while (end < raw.length && raw[end] != 0) end++;
                String name = new String(raw, 0, end, java.nio.charset.StandardCharsets.US_ASCII);
                if ("jvm.dll".equals(name)) {
                    return hModule.address();
                }
            }
        }
        return 0L;
    }

    public static String getHexOfPointer(long address, int len) {
        byte[] data = Memory.readBytes(address, len);
        StringBuilder sb = new StringBuilder(len * 2);
        for (byte b : data) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
