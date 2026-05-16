package com.javaexternal.memory;

import com.javaexternal.win32.Win32;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

public class Scanner {


    public static long scanSignature(String pattern) {
        int[] patternBytes = patternToBytes(pattern);

        try (Arena a = Arena.ofConfined()) {
            MemorySegment si = a.allocate(Win32.SYSTEM_INFO);
            Win32.getSystemInfo(si);

            long start = si.get(ValueLayout.ADDRESS, Win32.SI_MIN_OFFSET).address();
            long end   = si.get(ValueLayout.ADDRESS, Win32.SI_MAX_OFFSET).address();

            MemorySegment mbi = a.allocate(Win32.MEMORY_BASIC_INFORMATION);
            MemorySegment bytesReadOut = a.allocate(ValueLayout.JAVA_LONG);

            while (Long.compareUnsigned(start, end) < 0) {
                long mod = Memory.jvm;
                long queried = Win32.virtualQueryEx(Memory.getHandle(), start, mbi, Win32.MBI_SIZE);
                if (queried == 0) break;

                long regionSize = mbi.get(ValueLayout.JAVA_LONG, Win32.MBI_REGION_OFFSET);
                long baseAddr   = mbi.get(ValueLayout.ADDRESS, Win32.MBI_BASE_OFFSET).address();
                int  state      = mbi.get(ValueLayout.JAVA_INT, Win32.MBI_STATE_OFFSET);
                int  protect    = mbi.get(ValueLayout.JAVA_INT, Win32.MBI_PROTECT_OFFSET);

                if (Long.compareUnsigned(start, mod) < 0) {
                    start += regionSize;
                    continue;
                }

                int accessible = Win32.PAGE_EXECUTE_READWRITE
                        | Win32.PAGE_READWRITE
                        | Win32.PAGE_EXECUTE_READ;

                if (state == Win32.MEM_COMMIT
                        && (protect & accessible) != 0
                        && (protect & Win32.PAGE_GUARD) == 0) {

                    try (Arena page = Arena.ofConfined()) {
                        MemorySegment data = page.allocate(regionSize);
                        if (Memory.readMemory0(data, baseAddr, regionSize, bytesReadOut)) {
                            long bytesRead = bytesReadOut.get(ValueLayout.JAVA_LONG, 0);
                            long limit = bytesRead - patternBytes.length;
                            for (long i = 0; i < limit; i++) {
                                if (compareBytes(data, i, patternBytes)) {
                                    return baseAddr + i;
                                }
                            }
                        }
                    }
                }
                start += regionSize;
            }
        }
        return 0L;
    }

    private static boolean compareBytes(MemorySegment data, long offset, int[] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            int p = pattern[i];
            if (p == -1) continue;
            int b = data.get(ValueLayout.JAVA_BYTE, offset + i) & 0xFF;
            if (b != p) return false;
        }
        return true;
    }

    private static int[] patternToBytes(String pattern) {
        List<Integer> out = new ArrayList<>();
        int i = 0, n = pattern.length();
        while (i < n) {
            char c = pattern.charAt(i);
            if (c == '?') {
                i++;
                if (i < n && pattern.charAt(i) == '?') i++;
                out.add(-1);
            } else if (c == ' ' || c == '\t') {
                i++;
            } else {
                int j = i;
                while (j < n && isHex(pattern.charAt(j))) j++;
                out.add(Integer.parseInt(pattern.substring(i, j), 16));
                i = j;
            }
            while (i < n && pattern.charAt(i) == ' ') i++;
        }
        int[] arr = new int[out.size()];
        for (int k = 0; k < arr.length; k++) arr[k] = out.get(k);
        return arr;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
