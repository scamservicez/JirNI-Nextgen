package com.javaexternal;

import com.javaexternal.jvm.AutoOffsetParser;
import com.javaexternal.jvm.JavaLang;
import com.javaexternal.jvm.Jvm;
import com.javaexternal.jvm.Symbol;
import com.javaexternal.memory.MemUtil;
import com.javaexternal.memory.Memory;
import com.javaexternal.minecraft.MinecraftMappings;
import com.javaexternal.win32.Win32;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.UUID;

public class Main {

    void main(String[] args) throws IOException {
        System.out.println("pid:");
        Scanner stdin = new Scanner(System.in);
        int pid = stdin.nextInt();
        if (pid == 0) {
            System.err.println("Error on searching process");
            System.exit(1);
        }
        System.out.println("Start client write target pid -> " + pid);

        MemorySegment hProcess = Win32.openProcess(Win32.PROCESS_ALL_ACCESS, false, pid);
        long base = MemUtil.spoonGetJvmBase(hProcess);

        if (base == 0L) {
            System.err.println("Error on getting jvm.dll address");
            System.exit(1);
        }
        System.out.printf("Base address -> 0x%x%n", base);

        if (Memory.initAttachToClient(hProcess, base) != 0) {
            System.err.println("Error on boot of client");
            System.exit(1);
        }
        if (AutoOffsetParser.initOffsetParser(base) != 0) {
            System.err.println("Error on offset parsing");
            System.exit(1);
        }

        try (BufferedWriter out = Files.newBufferedWriter(Path.of(UUID.randomUUID().toString()+ ".txt"),
                StandardCharsets.UTF_8)) {
            for (long cl = Jvm.getSystemClassloader();
                 cl != 0L;
                 cl = Jvm.getNextClassLoaderFromClassLoader(cl)) {
                for (long klass = Jvm.getFirstClassFromClassLoader(cl);
                     klass != 0L;
                     klass = Jvm.getNextClassFromClass(klass)) {
                    Symbol n = Jvm.getClassName(klass);
                    out.write(n.asString());
                    out.newLine();
                }
            }
        }

        long minecraftLunar = MinecraftMappings.findMinecraftClass("java/lang/ClassLoader");
        long classLoader   = Jvm.findClass("java/lang/ClassLoader");
        System.out.printf("%x%n", classLoader);

        int classLoaderNameId = Jvm.findLocalField(classLoader, "name", "Ljava/lang/String;");
        long classLoaderObj = Jvm.getClassLoaderObjectFromClassLoader(
                Jvm.getClassLoaderFromClass(minecraftLunar));
        System.out.println(JavaLang.jString2String(
                Jvm.getObjectField(classLoaderObj, classLoaderNameId)));
    }
}
