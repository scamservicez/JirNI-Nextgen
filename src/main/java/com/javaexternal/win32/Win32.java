package com.javaexternal.win32;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

public class Win32 {

    public static final ValueLayout.OfByte    C_CHAR   = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfShort   C_SHORT  = ValueLayout.JAVA_SHORT;
    public static final ValueLayout.OfInt     C_INT    = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfLong    C_LONG64 = ValueLayout.JAVA_LONG;
    public static final AddressLayout         C_POINTER = ValueLayout.ADDRESS;
    public static final int  PROCESS_ALL_ACCESS         = 0x001FFFFF;
    public static final int  LIST_MODULES_64BIT         = 0x00000002;
    public static final int  TH32CS_SNAPPROCESS         = 0x00000002;
    public static final long INVALID_HANDLE_VALUE       = -1L;
    public static final int  MAX_PATH                   = 260;

    public static final int  MEM_COMMIT                 = 0x00001000;
    public static final int  PAGE_NOACCESS              = 0x01;
    public static final int  PAGE_READONLY              = 0x02;
    public static final int  PAGE_READWRITE             = 0x04;
    public static final int  PAGE_EXECUTE               = 0x10;
    public static final int  PAGE_EXECUTE_READ          = 0x20;
    public static final int  PAGE_EXECUTE_READWRITE     = 0x40;
    public static final int  PAGE_GUARD                 = 0x100;

    public static final int  IMAGE_DOS_SIGNATURE        = 0x5A4D;       // 'MZ'
    public static final int  IMAGE_NT_SIGNATURE         = 0x00004550;   // 'PE\0\0'
    public static final int  IMAGE_DIRECTORY_ENTRY_EXPORT = 0;

    public static final MemoryLayout PROCESSENTRY32 = MemoryLayout.structLayout(
            C_INT.withName("dwSize"),
            C_INT.withName("cntUsage"),
            C_INT.withName("th32ProcessID"),
            MemoryLayout.paddingLayout(4),
            C_LONG64.withName("th32DefaultHeapID"),
            C_INT.withName("th32ModuleID"),
            C_INT.withName("cntThreads"),
            C_INT.withName("th32ParentProcessID"),
            C_INT.withName("pcPriClassBase"),
            C_INT.withName("dwFlags"),
            MemoryLayout.sequenceLayout(MAX_PATH, C_CHAR).withName("szExeFile")
    ).withName("PROCESSENTRY32");

    public static final long PROCESSENTRY32_SIZE = PROCESSENTRY32.byteSize();
    public static final long PROCESSENTRY32_DWSIZE_OFFSET =
            PROCESSENTRY32.byteOffset(PathElement.groupElement("dwSize"));
    public static final long PROCESSENTRY32_PID_OFFSET =
            PROCESSENTRY32.byteOffset(PathElement.groupElement("th32ProcessID"));
    public static final long PROCESSENTRY32_EXE_OFFSET =
            PROCESSENTRY32.byteOffset(PathElement.groupElement("szExeFile"));

    public static final MemoryLayout SYSTEM_INFO = MemoryLayout.structLayout(
            C_INT.withName("dwOemId"),
            C_INT.withName("dwPageSize"),
            C_POINTER.withName("lpMinimumApplicationAddress"),
            C_POINTER.withName("lpMaximumApplicationAddress"),
            C_LONG64.withName("dwActiveProcessorMask"),
            C_INT.withName("dwNumberOfProcessors"),
            C_INT.withName("dwProcessorType"),
            C_INT.withName("dwAllocationGranularity"),
            C_SHORT.withName("wProcessorLevel"),
            C_SHORT.withName("wProcessorRevision")
    ).withName("SYSTEM_INFO");

    public static final long SI_MIN_OFFSET =
            SYSTEM_INFO.byteOffset(PathElement.groupElement("lpMinimumApplicationAddress"));
    public static final long SI_MAX_OFFSET =
            SYSTEM_INFO.byteOffset(PathElement.groupElement("lpMaximumApplicationAddress"));

    public static final MemoryLayout MEMORY_BASIC_INFORMATION = MemoryLayout.structLayout(
            C_POINTER.withName("BaseAddress"),
            C_POINTER.withName("AllocationBase"),
            C_INT.withName("AllocationProtect"),
            C_SHORT.withName("PartitionId"),
            MemoryLayout.paddingLayout(2),
            C_LONG64.withName("RegionSize"),
            C_INT.withName("State"),
            C_INT.withName("Protect"),
            C_INT.withName("Type"),
            MemoryLayout.paddingLayout(4)
    ).withName("MEMORY_BASIC_INFORMATION");

    public static final long MBI_SIZE = MEMORY_BASIC_INFORMATION.byteSize();
    public static final long MBI_BASE_OFFSET =
            MEMORY_BASIC_INFORMATION.byteOffset(PathElement.groupElement("BaseAddress"));
    public static final long MBI_REGION_OFFSET =
            MEMORY_BASIC_INFORMATION.byteOffset(PathElement.groupElement("RegionSize"));
    public static final long MBI_STATE_OFFSET =
            MEMORY_BASIC_INFORMATION.byteOffset(PathElement.groupElement("State"));
    public static final long MBI_PROTECT_OFFSET =
            MEMORY_BASIC_INFORMATION.byteOffset(PathElement.groupElement("Protect"));

    private static final Linker LINKER = Linker.nativeLinker();
    private static final Arena GLOBAL = Arena.ofShared();

    private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32", GLOBAL);
    private static final SymbolLookup PSAPI    = lookupPsapi();

    private static SymbolLookup lookupPsapi() {
        try {
            return SymbolLookup.libraryLookup("psapi", GLOBAL);
        } catch (IllegalArgumentException e) {
            return KERNEL32;
        }
    }

    private static MethodHandle k32(String name, FunctionDescriptor desc) {
        return LINKER.downcallHandle(
                KERNEL32.find(name).orElseThrow(
                        () -> new RuntimeException("kernel32: symbol not found: " + name)),
                desc);
    }

    private static MethodHandle psapi(String unprefixedName, FunctionDescriptor desc) {
        String prefixed = "K32" + unprefixedName;
        return LINKER.downcallHandle(
                PSAPI.find(unprefixedName)
                        .or(() -> KERNEL32.find(prefixed))
                        .or(() -> KERNEL32.find(unprefixedName))
                        .orElseThrow(() -> new RuntimeException(
                                "Symbol not found: " + unprefixedName)),
                desc);
    }

    private static final MethodHandle MH_OpenProcess = k32("OpenProcess",
            FunctionDescriptor.of(C_POINTER, C_INT, C_INT, C_INT));

    private static final MethodHandle MH_CloseHandle = k32("CloseHandle",
            FunctionDescriptor.of(C_INT, C_POINTER));

    private static final MethodHandle MH_ReadProcessMemory = k32("ReadProcessMemory",
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_LONG64, C_POINTER));

    private static final MethodHandle MH_WriteProcessMemory = k32("WriteProcessMemory",
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_LONG64, C_POINTER));

    private static final MethodHandle MH_CreateToolhelp32Snapshot = k32("CreateToolhelp32Snapshot",
            FunctionDescriptor.of(C_POINTER, C_INT, C_INT));

    private static final MethodHandle MH_Process32First = k32("Process32First",
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER));

    private static final MethodHandle MH_Process32Next = k32("Process32Next",
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER));

    private static final MethodHandle MH_GetSystemInfo = k32("GetSystemInfo",
            FunctionDescriptor.ofVoid(C_POINTER));

    private static final MethodHandle MH_VirtualQueryEx = k32("VirtualQueryEx",
            FunctionDescriptor.of(C_LONG64, C_POINTER, C_POINTER, C_POINTER, C_LONG64));

    private static final MethodHandle MH_EnumProcessModulesEx = psapi("EnumProcessModulesEx",
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_POINTER, C_INT));

    private static final MethodHandle MH_GetModuleBaseNameA = psapi("GetModuleBaseNameA",
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT));

    private static final MethodHandle MH_GetLastError = k32("GetLastError",
            FunctionDescriptor.of(C_INT));

    public static MemorySegment openProcess(int access, boolean inherit, int pid) {
        try {
            return (MemorySegment) MH_OpenProcess.invokeExact(access, inherit ? 1 : 0, pid);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static boolean closeHandle(MemorySegment handle) {
        try {
            return ((int) MH_CloseHandle.invokeExact(handle)) != 0;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static int readProcessMemory(MemorySegment hProcess, long address,
                                        MemorySegment buffer, long size,
                                        MemorySegment bytesReadOut) {
        MemorySegment outArg = (bytesReadOut == null) ? MemorySegment.NULL : bytesReadOut;
        MemorySegment addrArg = MemorySegment.ofAddress(address);
        try {
            return (int) MH_ReadProcessMemory.invokeExact(
                    hProcess, addrArg, buffer, size, outArg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static int writeProcessMemory(MemorySegment hProcess, long address,
                                         MemorySegment buffer, long size,
                                         MemorySegment bytesWrittenOut) {
        MemorySegment outArg = (bytesWrittenOut == null) ? MemorySegment.NULL : bytesWrittenOut;
        MemorySegment addrArg = MemorySegment.ofAddress(address);
        try {
            return (int) MH_WriteProcessMemory.invokeExact(
                    hProcess, addrArg, buffer, size, outArg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static MemorySegment createToolhelp32Snapshot(int flags, int pid) {
        try {
            return (MemorySegment) MH_CreateToolhelp32Snapshot.invokeExact(flags, pid);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static boolean process32First(MemorySegment snap, MemorySegment entry) {
        try {
            return ((int) MH_Process32First.invokeExact(snap, entry)) != 0;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static boolean process32Next(MemorySegment snap, MemorySegment entry) {
        try {
            return ((int) MH_Process32Next.invokeExact(snap, entry)) != 0;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void getSystemInfo(MemorySegment out) {
        try {
            MH_GetSystemInfo.invokeExact(out);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static long virtualQueryEx(MemorySegment hProcess, long address,
                                      MemorySegment mbi, long mbiSize) {
        try {
            return (long) MH_VirtualQueryEx.invokeExact(
                    hProcess,
                    MemorySegment.ofAddress(address),
                    mbi,
                    mbiSize);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static boolean enumProcessModulesEx(MemorySegment hProcess,
                                               MemorySegment moduleArray,
                                               int cb,
                                               MemorySegment lpcbNeededOut,
                                               int filterFlag) {
        try {
            return ((int) MH_EnumProcessModulesEx.invokeExact(
                    hProcess, moduleArray, cb, lpcbNeededOut, filterFlag)) != 0;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static int getModuleBaseNameA(MemorySegment hProcess, MemorySegment hModule,
                                         MemorySegment buffer, int size) {
        try {
            return (int) MH_GetModuleBaseNameA.invokeExact(hProcess, hModule, buffer, size);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static int getLastError() {
        try {
            return (int) MH_GetLastError.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
