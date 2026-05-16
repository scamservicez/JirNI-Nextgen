package com.javaexternal.jvm;

public  class FieldFlags {

    public static final int FF_INITIALIZED = 0;
    public static final int FF_INJECTED    = 1;
    public static final int FF_GENERIC     = 2;
    public static final int FF_STABLE      = 3;
    public static final int FF_CONTENDED   = 4;

    public int flags;

    public FieldFlags() {}
    public FieldFlags(int flags) { this.flags = flags; }

    public int asUint() { return flags; }

    private static int flagMask(int p) { return 1 << p; }

    private boolean testFlag(int pos) { return (flags & flagMask(pos)) != 0; }

    private static final int OPTIONAL_ITEM_BIT_MASK =
            flagMask(FF_INITIALIZED) | flagMask(FF_GENERIC) | flagMask(FF_CONTENDED);

    public boolean hasAnyOptionals() { return (flags & OPTIONAL_ITEM_BIT_MASK) != 0; }

    public boolean isInitialized() { return testFlag(FF_INITIALIZED); }
    public boolean isInjected()    { return testFlag(FF_INJECTED); }
    public boolean isGeneric()     { return testFlag(FF_GENERIC); }
    public boolean isStable()      { return testFlag(FF_STABLE); }
    public boolean isContended()   { return testFlag(FF_CONTENDED); }
}
