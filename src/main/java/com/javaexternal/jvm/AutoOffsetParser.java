package com.javaexternal.jvm;

import com.javaexternal.memory.Memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;


public class AutoOffsetParser {

    private AutoOffsetParser() {}

    public static final List<ExportFunction> exportFuncs = new ArrayList<>();
    public static final List<VMStructEntry>  vmStructs   = new ArrayList<>();

    public static boolean compressedOop = false;   // jbool copressedOpp
    public static int     shift         = 0;       // jint   shift
    public static long    base          = 0L;      // void*  base
    private static final int E_LFANEW_OFFSET = 0x3C;
    public static List<ExportFunction> parseExportTable(long baseAddress) {
        List<ExportFunction> exports = new ArrayList<>();

        int dosMagic = Memory.readShort(baseAddress) & 0xFFFF;
        if (dosMagic != 0x5A4D) {
            System.err.println("Not a valid PE file (DOS signature)");
            return exports;
        }
        int eLfanew = Memory.readInt(baseAddress + E_LFANEW_OFFSET);
        long ntHeadersAddr = baseAddress + eLfanew;

        int ntSig = Memory.readInt(ntHeadersAddr);
        if (ntSig != 0x00004550) {
            System.err.println("Not a valid PE file (NT signature)");
            return exports;
        }

        long optionalHeader = ntHeadersAddr + 24;
        long dataDirectory  = optionalHeader + 112;
        long exportEntry    = dataDirectory + 16L * Win32Constants.IMAGE_DIRECTORY_ENTRY_EXPORT;

        int exportRVA  = Memory.readInt(exportEntry);
        int exportSize = Memory.readInt(exportEntry + 4);
        if (exportSize == 0) {
            System.err.println("No export directory");
            return exports;
        }

        long exportDirAddr = baseAddress + exportRVA;

        // IMAGE_EXPORT_DIRECTORY layout (40 bytes):
        //   DWORD Characteristics            0
        //   DWORD TimeDateStamp              4
        //   WORD  MajorVersion               8
        //   WORD  MinorVersion              10
        //   DWORD Name                      12
        //   DWORD Base                      16
        //   DWORD NumberOfFunctions         20
        //   DWORD NumberOfNames             24
        //   DWORD AddressOfFunctions        28
        //   DWORD AddressOfNames            32
        //   DWORD AddressOfNameOrdinals     36
        int base_ord            = Memory.readInt(exportDirAddr + 16);
        int numberOfFunctions   = Memory.readInt(exportDirAddr + 20);
        int numberOfNames       = Memory.readInt(exportDirAddr + 24);
        int addrOfFunctions     = Memory.readInt(exportDirAddr + 28);
        int addrOfNames         = Memory.readInt(exportDirAddr + 32);
        int addrOfNameOrdinals  = Memory.readInt(exportDirAddr + 36);

        try (Arena a = Arena.ofConfined()) {
            MemorySegment names     = a.allocate((long) numberOfNames * 4);
            MemorySegment ordinals  = a.allocate((long) numberOfNames * 2);
            MemorySegment functions = a.allocate((long) numberOfFunctions * 4);

            if (!Memory.readMemory(names,     baseAddress + addrOfNames,        names.byteSize())
             || !Memory.readMemory(ordinals,  baseAddress + addrOfNameOrdinals, ordinals.byteSize())
             || !Memory.readMemory(functions, baseAddress + addrOfFunctions,    functions.byteSize())) {
                System.err.println("Can't read export arrays");
                return exports;
            }

            for (int i = 0; i < numberOfNames; i++) {
                int nameRva = names.getAtIndex(ValueLayout.JAVA_INT, i);
                int ord     = ordinals.getAtIndex(ValueLayout.JAVA_SHORT, i) & 0xFFFF;
                int funcRva = functions.getAtIndex(ValueLayout.JAVA_INT, ord);
                String name = Memory.readCString(baseAddress + nameRva, 256);
                exports.add(new ExportFunction(ord + base_ord, funcRva, name));
            }
        }
        return exports;
    }

    public static long getPointerOfExport(String text) {
        for (ExportFunction f : exportFuncs) {
            if (f.name.equals(text)) {
                return Memory.jvm + Integer.toUnsignedLong(f.address);
            }
        }
        return 0L;
    }
    public static VMStructEntry findVMStructEntry(String typeName, String fieldName) {
        for (VMStructEntry e : vmStructs) {
            if (typeName.equals(e.typeName) && fieldName.equals(e.fieldName)) {
                return e;
            }
        }
        return null;
    }

    public static int initOffsetParser(long hmodule) {
        exportFuncs.clear();
        exportFuncs.addAll(parseExportTable(hmodule));

        long gHotSpotVMStructsAddr = getPointerOfExport("gHotSpotVMStructs");
        if (gHotSpotVMStructsAddr == 0L) {
            System.err.println("Export gHotSpotVMStructs not found");
            return 1;
        }
        long tablePtr = Memory.readPointer(gHotSpotVMStructsAddr);
        if (tablePtr == 0L) return 1;

        try (Arena a = Arena.ofConfined()) {
            MemorySegment entryBuf = a.allocate(VMStructEntry.NATIVE_SIZE);

            for (int i = 0; i < 788; i++) {
                long structAddr = tablePtr + (long) i * VMStructEntry.NATIVE_SIZE;
                if (!Memory.readMemory(entryBuf, structAddr, VMStructEntry.NATIVE_SIZE)) break;

                long typeNamePtr   = entryBuf.get(ValueLayout.ADDRESS, 0).address();
                long fieldNamePtr  = entryBuf.get(ValueLayout.ADDRESS, 8).address();
                long typeStringPtr = entryBuf.get(ValueLayout.ADDRESS, 16).address();
                int  isStatic      = entryBuf.get(ValueLayout.JAVA_INT, 24);
                long offset        = entryBuf.get(ValueLayout.JAVA_LONG, 32);
                long address       = entryBuf.get(ValueLayout.ADDRESS, 40).address();

                if (typeNamePtr == 0L) break;

                VMStructEntry out = new VMStructEntry();
                out.typeName   = Memory.readCString(typeNamePtr, 255);
                out.fieldName  = Memory.readCString(fieldNamePtr, 255);
                out.typeString = Memory.readCString(typeStringPtr, 255);
                out.isStatic   = isStatic;
                out.offset     = offset;
                out.address    = address;
                vmStructs.add(out);
            }
        }

        VMStructEntry oopBase = findVMStructEntry("CompressedOops", "_narrow_oop._base");
        VMStructEntry oopShift = findVMStructEntry("CompressedOops", "_narrow_oop._shift");
        VMStructEntry useCompressed = findVMStructEntry(
                "CompressedOops", "_narrow_oop._use_implicit_null_checks");

        if (oopBase != null)        base          = Memory.readPointer(oopBase.address);
        if (oopShift != null)       shift         = Memory.readInt(oopShift.address);
        if (useCompressed != null)  compressedOop = (Memory.readByte(useCompressed.address) & 0xFF) != 0;

        return 0;
    }

    private static final class Win32Constants {
        static final int IMAGE_DIRECTORY_ENTRY_EXPORT = 0;
    }
}
