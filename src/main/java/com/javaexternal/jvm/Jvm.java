package com.javaexternal.jvm;

import com.javaexternal.memory.Memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public  class Jvm {

    private static final Map<String, Long>  klassesByName       = new HashMap<>();
    private static final Map<Long,   Symbol> klassesName        = new HashMap<>();
    private static final Map<Long,   Long>  klassesOOP          = new HashMap<>();
    private static final Map<Long,   Long>  klassesCompressed   = new HashMap<>();
    private static final Map<Long,   Boolean> instanceOfCache   = new ConcurrentHashMap<>();
    private static VMStructEntry vsObjectKlass;
    private static VMStructEntry vsClassLoaderData_klasses;
    private static VMStructEntry vsKlass_nextLink;
    private static VMStructEntry vsKlass_super;
    private static VMStructEntry vsKlass_classLoaderData;
    private static VMStructEntry vsClassLoaderData_classLoader;
    private static VMStructEntry vsInstanceKlass_localInterfaces;
    private static VMStructEntry vsClassLoaderData_next;
    private static VMStructEntry vsKlass_name;
    private static VMStructEntry vsClassLoaderDataGraph_head;
    private static VMStructEntry vsKlass_javaMirror;
    private static VMStructEntry vsInstanceKlass_fieldinfoStream;
    private static VMStructEntry vsInstanceKlass_constants;
    private static VMStructEntry vsConstantPool_length;
    private static VMStructEntry vsInstanceKlass_fields;
    private static VMStructEntry vsInstanceKlass_javaFieldsCount;
    private static VMStructEntry vsKlassBaseAddress;
    private static VMStructEntry vsKlassShiftAddress;

    private static VMStructEntry vs(String typeName, String fieldName, VMStructEntry cached) {
        if (cached != null) return cached;
        return AutoOffsetParser.findVMStructEntry(typeName, fieldName);
    }

    public static boolean useCompressedOop() {
        return AutoOffsetParser.compressedOop;
    }

    public static long getObjectKlass() {
        if (vsObjectKlass == null) {
            vsObjectKlass = AutoOffsetParser.findVMStructEntry(
                    "vmClasses", "_klasses[static_cast<int>(vmClassID::Object_klass_knum)]");
        }
        if (vsObjectKlass == null) {
            System.err.println("Error on getting object_klass");
            return 0L;
        }
        return Memory.readPointer(vsObjectKlass.address);
    }

    public static long getFirstClassFromClassLoader(long classLoaderDataPtr) {
        if (vsClassLoaderData_klasses == null) {
            vsClassLoaderData_klasses = AutoOffsetParser.findVMStructEntry(
                    "ClassLoaderData", "_klasses");
        }
        return Memory.readPointer(classLoaderDataPtr + vsClassLoaderData_klasses.offset);
    }

    public static long getNextClassFromClass(long klass) {
        if (vsKlass_nextLink == null) {
            vsKlass_nextLink = AutoOffsetParser.findVMStructEntry("Klass", "_next_link");
        }
        return Memory.readPointer(klass + vsKlass_nextLink.offset);
    }

    public static long getSuperClassFromClass(long klass) {
        if (vsKlass_super == null) {
            vsKlass_super = AutoOffsetParser.findVMStructEntry("Klass", "_super");
        }
        return Memory.readPointer(klass + vsKlass_super.offset);
    }

    public static long getClassLoaderFromClass(long klass) {
        if (vsKlass_classLoaderData == null) {
            vsKlass_classLoaderData = AutoOffsetParser.findVMStructEntry(
                    "Klass", "_class_loader_data");
        }
        return Memory.readPointer(klass + vsKlass_classLoaderData.offset);
    }

    public static long getClassLoaderObjectFromClassLoader(long classLoaderDataPtr) {
        if (vsClassLoaderData_classLoader == null) {
            vsClassLoaderData_classLoader = AutoOffsetParser.findVMStructEntry(
                    "ClassLoaderData", "_class_loader");
        }
        long objClassLoader = classLoaderDataPtr + vsClassLoaderData_classLoader.offset;
        return Memory.readPointer(Memory.readPointer(objClassLoader));
    }

    public static List<Long> getInterfaces(long klass) {
        if (vsInstanceKlass_localInterfaces == null) {
            vsInstanceKlass_localInterfaces = AutoOffsetParser.findVMStructEntry(
                    "InstanceKlass", "_local_interfaces");
        }
        long pointer = Memory.readPointer(klass + vsInstanceKlass_localInterfaces.offset);
        int length = Memory.readInt(pointer);
        pointer += 4;                // sizeof(int) for the leading length
        List<Long> out = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            long addr = Memory.readPointer(pointer + (long) i * 8L);
            out.add(addr);
        }
        return out;
    }

    public static long getNextClassLoaderFromClassLoader(long classLoaderDataPtr) {
        if (vsClassLoaderData_next == null) {
            vsClassLoaderData_next = AutoOffsetParser.findVMStructEntry(
                    "ClassLoaderData", "_next");
        }
        return Memory.readPointer(classLoaderDataPtr + vsClassLoaderData_next.offset);
    }

    public static Symbol getClassName(long klass) {
        Symbol cached = klassesName.get(klass);
        if (cached != null) return cached;
        if (vsKlass_name == null) {
            vsKlass_name = AutoOffsetParser.findVMStructEntry("Klass", "_name");
        }
        Symbol s = Symbol.fromAddress(klass + vsKlass_name.offset);
        klassesName.put(klass, s);
        return s;
    }

    public static long findClass(String name) {
        Long cached = klassesByName.get(name);
        if (cached != null && cached != 0L) return cached;

        for (long cl = getSystemClassloader(); cl != 0L; cl = getNextClassLoaderFromClassLoader(cl)) {
            for (long klass = getFirstClassFromClassLoader(cl);
                 klass != 0L;
                 klass = getNextClassFromClass(klass)) {
                Symbol n = getClassName(klass);
                if (name.equals(n.asString())) {
                    klassesByName.put(name, klass);
                    return klass;
                }
            }
        }
        return 0L;
    }

    public static long getSystemClassloader() {
        if (vsClassLoaderDataGraph_head == null) {
            vsClassLoaderDataGraph_head = AutoOffsetParser.findVMStructEntry(
                    "ClassLoaderDataGraph", "_head");
        }
        if (vsClassLoaderDataGraph_head == null) {
            System.err.println("Error on getting ClassLoaderDataGraph_head");
            return 0L;
        }
        return Memory.readPointer(vsClassLoaderDataGraph_head.address);
    }

    public static int findLocalField(long klass, String fieldName, String fieldDesc) {
        if (vsInstanceKlass_fieldinfoStream == null) {
            vsInstanceKlass_fieldinfoStream = AutoOffsetParser.findVMStructEntry(
                    "InstanceKlass", "_fieldinfo_stream");
        }
        if (vsInstanceKlass_constants == null) {
            vsInstanceKlass_constants = AutoOffsetParser.findVMStructEntry(
                    "InstanceKlass", "_constants");
        }
        if (vsConstantPool_length == null) {
            vsConstantPool_length = AutoOffsetParser.findVMStructEntry(
                    "ConstantPool", "_length");
        }

        long constantPoolSize = vsConstantPool_length.offset + 4 + 8;
        long constants = Memory.readPointer(klass + vsInstanceKlass_constants.offset);
        long baseAddr = constants + constantPoolSize;

        if (vsInstanceKlass_fieldinfoStream != null) {
            long fieldInfo = Memory.readPointer(klass + vsInstanceKlass_fieldinfoStream.offset);
            List<FieldInfo20> fields = new ArrayList<>();
            if (!extractFieldinfoList(fieldInfo, fields)) return 0;

            for (FieldInfo20 f : fields) {
                Symbol nm = Symbol.fromAddress(baseAddr + (long) f.nameIndex() * 8L);
                Symbol ds = Symbol.fromAddress(baseAddr + (long) f.signatureIndex() * 8L);
                if (fieldName.equals(nm.asString()) && fieldDesc.equals(ds.asString())) {
                    return f.offset();
                }
            }
        } else {
            if (vsInstanceKlass_fields == null) {
                vsInstanceKlass_fields = AutoOffsetParser.findVMStructEntry(
                        "InstanceKlass", "_fields");
            }
            if (vsInstanceKlass_javaFieldsCount == null) {
                vsInstanceKlass_javaFieldsCount = AutoOffsetParser.findVMStructEntry(
                        "InstanceKlass", "_java_fields_count");
            }

            long fields = Memory.readPointer(klass + vsInstanceKlass_fields.offset);
            int  fieldCount = Memory.readShort(klass + vsInstanceKlass_javaFieldsCount.offset) & 0xFFFF;

            fields += 4; // skip the leading jint length
            try (Arena a = Arena.ofConfined()) {
                MemorySegment buf = a.allocate(FieldInfo17.SIZE_BYTES);
                for (int i = 0; i < fieldCount; i++) {
                    long addr = fields + (long) i * FieldInfo17.SIZE_BYTES;
                    if (!Memory.readMemory(buf, addr, FieldInfo17.SIZE_BYTES)) continue;
                    FieldInfo17 info = FieldInfo17.fromSegment(buf);
                    Symbol nm = Symbol.fromAddress(baseAddr + (long) info.nameIndex() * 8L);
                    Symbol ds = Symbol.fromAddress(baseAddr + (long) info.signatureIndex() * 8L);
                    if (fieldName.equals(nm.asString()) && fieldDesc.equals(ds.asString())) {
                        return info.offset();
                    }
                }
            }
        }
        return 0;
    }

    public static int findInterfaceField(long klass, String fieldName, String fieldDesc) {
        List<Long> interfaces = getInterfaces(klass);
        for (long iface : interfaces) {
            long localKlass = Memory.readPointer(iface);
            int off = findLocalField(localKlass, fieldName, fieldDesc);
            if (off != 0) return off;
            off = findInterfaceField(localKlass, fieldName, fieldDesc);
            if (off != 0) return off;
        }
        return 0;
    }

    public static int findField(long klass, String fieldName, String fieldDesc) {
        if (klass == 0L) return 0;
        int off = findLocalField(klass, fieldName, fieldDesc);
        if (off != 0) return off;
        long sup = getSuperClassFromClass(klass);
        if (sup == 0L || sup == klass) return 0;
        return findField(sup, fieldName, fieldDesc);
    }

    public static long encodeOop(long oop) {
        long encoded = oop;
        if (useCompressedOop()) {
            encoded -= AutoOffsetParser.base;
            encoded >>>= AutoOffsetParser.shift;
        }
        return encoded;
    }

    public static long decodeOop(long oop) {
        long decoded = oop;
        if (useCompressedOop()) {
            decoded <<= AutoOffsetParser.shift;
            decoded += AutoOffsetParser.base;
        }
        return decoded;
    }

    public static long getClassOOP(long klass) {
        Long cached = klassesOOP.get(klass);
        if (cached != null) return cached;
        if (vsKlass_javaMirror == null) {
            vsKlass_javaMirror = AutoOffsetParser.findVMStructEntry("Klass", "_java_mirror");
        }
        long classoop = Memory.readPointer(klass + vsKlass_javaMirror.offset);
        long mirror = Memory.readPointer(classoop);
        klassesOOP.put(klass, mirror);
        return mirror;
    }

    public static int readUnsigned5(byte[] data, int[] posRef) {
        int pos = posRef[0];
        int sum = (data[pos++] & 0xFF) - 1;
        if (sum < 191) { posRef[0] = pos; return sum; }
        int shift = 6;
        while (true) {
            int b = data[pos++] & 0xFF;
            sum += (b - 1) << shift;
            if (b < (1 + 191)) break;
            shift += 6;
        }
        posRef[0] = pos;
        return sum;
    }

    public static void readFieldInfo(FieldInfo20 out, byte[] buffer, int[] posRef) {
        out.index = 0;
        out.nameIndex      = readUnsigned5(buffer, posRef) & 0xFFFF;
        out.signatureIndex = readUnsigned5(buffer, posRef) & 0xFFFF;
        out.offset         = readUnsigned5(buffer, posRef);
        out.accessFlags    = new AccessFlags(readUnsigned5(buffer, posRef) & 0xFFFF);
        out.fieldFlags     = new FieldFlags(readUnsigned5(buffer, posRef));
        out.initializerIndex      = out.fieldFlags.isInitialized()
                ? readUnsigned5(buffer, posRef) & 0xFFFF : 0;
        out.genericSignatureIndex = out.fieldFlags.isGeneric()
                ? readUnsigned5(buffer, posRef) & 0xFFFF : 0;
        out.contentionGroup       = out.fieldFlags.isContended()
                ? readUnsigned5(buffer, posRef) & 0xFFFF : 0;
    }

    public static boolean extractFieldinfoList(long fieldinfoArrayAddr, List<FieldInfo20> out) {
        int length = Memory.readInt(fieldinfoArrayAddr);
        if (length <= 0) return false;
        byte[] buffer = Memory.readBytes(fieldinfoArrayAddr + 4, length);
        if (buffer.length == 0) return false;

        int[] pos = { 0 };
        int javaCount     = readUnsigned5(buffer, pos);
        int injectedCount = readUnsigned5(buffer, pos);
        int total = javaCount + injectedCount;

        out.clear();
        for (int i = 0; i < total; i++) {
            FieldInfo20 f = new FieldInfo20();
            readFieldInfo(f, buffer, pos);
            out.add(f);
        }
        return true;
    }

    public static long getStaticObjectField(long klass, int fieldid) {
        if (fieldid == 0) return 0L;
        int raw = Memory.readInt(getClassOOP(klass) + fieldid);
        return decodeOop(Integer.toUnsignedLong(raw));
    }

    public static void setStaticObjectField(long klass, int fieldid, long value) {
        if (fieldid == 0) return;
        int encoded = (int) encodeOop(value);
        Memory.writeInt(getClassOOP(klass) + fieldid, encoded);
    }

    public static long getObjectField(long oop, int fieldid) {
        if (fieldid == 0) return 0L;
        int raw = Memory.readInt(oop + fieldid);
        return decodeOop(Integer.toUnsignedLong(raw));
    }

    public static void setObjectField(long oop, int fieldid, long value) {
        if (fieldid == 0) return;
        int encoded = (int) encodeOop(value);
        Memory.writeInt(oop + fieldid, encoded);
    }

    public static int getIntField(long oop, int fieldid)        { return fieldid == 0 ? 0 : Memory.readInt(oop + fieldid); }
    public static long getLongField(long oop, int fieldid)      { return fieldid == 0 ? 0 : Memory.readLong(oop + fieldid); }
    public static short getShortField(long oop, int fieldid)    { return fieldid == 0 ? 0 : Memory.readShort(oop + fieldid); }
    public static byte getByteField(long oop, int fieldid)      { return fieldid == 0 ? 0 : Memory.readByte(oop + fieldid); }
    public static boolean getBooleanField(long oop, int fieldid){ return fieldid != 0 && (Memory.readByte(oop + fieldid) & 0xFF) != 0; }
    public static float getFloatField(long oop, int fieldid)    { return fieldid == 0 ? 0 : Memory.readFloat(oop + fieldid); }
    public static double getDoubleField(long oop, int fieldid)  { return fieldid == 0 ? 0 : Memory.readDouble(oop + fieldid); }

    public static void setIntField(long oop, int fieldid, int v)        { if (fieldid != 0) Memory.writeInt(oop + fieldid, v); }
    public static void setLongField(long oop, int fieldid, long v)      { if (fieldid != 0) Memory.writeLong(oop + fieldid, v); }
    public static void setShortField(long oop, int fieldid, short v)    { if (fieldid != 0) Memory.writeShort(oop + fieldid, v); }
    public static void setByteField(long oop, int fieldid, byte v)      { if (fieldid != 0) Memory.writeByte(oop + fieldid, v); }
    public static void setBooleanField(long oop, int fieldid, boolean v){ if (fieldid != 0) Memory.writeByte(oop + fieldid, (byte)(v ? 1 : 0)); }

    public static int getStaticIntField(long klass, int fieldid)   { return fieldid == 0 ? 0 : Memory.readInt(getClassOOP(klass) + fieldid); }
    public static long getStaticLongField(long klass, int fieldid) { return fieldid == 0 ? 0 : Memory.readLong(getClassOOP(klass) + fieldid); }
    public static void setStaticIntField(long klass, int fieldid, int v)   { if (fieldid != 0) Memory.writeInt(getClassOOP(klass) + fieldid, v); }
    public static void setStaticLongField(long klass, int fieldid, long v) { if (fieldid != 0) Memory.writeLong(getClassOOP(klass) + fieldid, v); }
    public static int getArrayLength(long oop) {
        int off = useCompressedOop() ? 0xC : 0x10;
        return Memory.readInt(oop + off);
    }


    public static long[] getObjectArrayElement(long oop, int start, int end) {
        int base = useCompressedOop() ? 0x10 : 0x18;
        int length = getArrayLength(oop);
        int total = end + start;
        long[] out = new long[end];
        for (int i = start; i < total && i < length; i++) {
            out[i - start] = getObjectField(oop, base + i * 4);
        }
        return out;
    }

    public static void setObjectArrayElement(long oop, long[] values, int start, int end) {
        int base = useCompressedOop() ? 0x10 : 0x18;
        int length = getArrayLength(oop);
        int total = end + start;
        for (int i = start; i < total && i < length; i++) {
            setObjectField(oop, base + i * 4, values[i - start]);
        }
    }

    public static byte[] getByteArrayElement(long oop, int start, int end) {
        int base = useCompressedOop() ? 0x10 : 0x18;
        int length = getArrayLength(oop);
        if (start < 0 || start >= length) return new byte[0];
        if (end + start > length) end = length - start;
        return Memory.readBytes(oop + base + (long) start, end);
    }

    public static int[] getIntArrayElement(long oop, int start, int end) {
        int base = useCompressedOop() ? 0x10 : 0x18;
        int length = getArrayLength(oop);
        if (start < 0 || start >= length) return new int[0];
        if (end + start > length) end = length - start;
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate((long) end * 4);
            Memory.readMemory(s, oop + base + (long) start * 4, s.byteSize());
            int[] out = new int[end];
            MemorySegment.copy(s, ValueLayout.JAVA_INT, 0, out, 0, end);
            return out;
        }
    }

    public static long decodeClass(long klass) {
        if (vsKlassBaseAddress == null) {
            vsKlassBaseAddress = AutoOffsetParser.findVMStructEntry(
                    "CompressedKlassPointers", "_narrow_klass._base");
        }
        if (vsKlassShiftAddress == null) {
            vsKlassShiftAddress = AutoOffsetParser.findVMStructEntry(
                    "CompressedKlassPointers", "_narrow_klass._shift");
        }
        Long cached = klassesCompressed.get(klass);
        if (cached != null) return cached;

        long decoded = klass;
        if (useCompressedOop()) {
            long kBase = vsKlassBaseAddress != null ? Memory.readPointer(vsKlassBaseAddress.address) : 0L;
            int  kShift = vsKlassShiftAddress != null ? Memory.readInt(vsKlassShiftAddress.address) : 0;
            decoded <<= kShift;
            decoded += kBase;
        }
        klassesCompressed.put(klass, decoded);
        return decoded;
    }

    public static long getObjectClass(long oop) {
        boolean compressed = useCompressedOop();
        long klass;
        if (compressed) {
            klass = Integer.toUnsignedLong(Memory.readInt(oop + 0x8));
            klass = decodeClass(klass);
        } else {
            klass = Memory.readLong(oop + 0x8);
        }
        return klass;
    }

    private static boolean firstInstanceOf(long first, long second) {
        if (first == second) return true;
        long t = first;
        while (t != 0L) {
            if (t == second) return true;
            t = getSuperClassFromClass(t);
        }
        return false;
    }

    public static boolean instanceOfClass(long first, long second) {
        long key = pair(first, second);
        Boolean cached = instanceOfCache.get(key);
        if (cached != null) return cached;
        boolean res = firstInstanceOf(first, second);
        instanceOfCache.put(key, res);
        return res;
    }

    public static boolean instanceOf(long obj, long klass) {
        return instanceOfClass(getObjectClass(obj), klass);
    }

    private static long pair(long a, long b) {
        return a ^ (b << 1);
    }
}
