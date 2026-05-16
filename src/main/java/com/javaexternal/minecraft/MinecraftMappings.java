package com.javaexternal.minecraft;

import com.javaexternal.jvm.Jvm;
import com.javaexternal.parser.Parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MinecraftMappings {

    private static final Map<String, String> parsedClasses = new HashMap<>();
    private static final Map<String, String> parsedFields  = new HashMap<>();
    private static final Map<String, Long>    klasses      = new HashMap<>();
    private static final Map<String, Integer> fields       = new HashMap<>();
    public static String mappingsPath = "mappings/mappingsExt.txt";

    public static int initMappings() {
        List<String> lines = new ArrayList<>();

        long mcLunar  = findMinecraftClass("net/minecraft/client/Minecraft");
        long mcFabric = findMinecraftClass("net/minecraft/client/Minecraft");

        if (mcLunar == 0L && mcFabric != 0L) {
            byte[] data = loadMappingBytes();
            Parser.parseBytes(data, data.length, lines);
        }

        Parser.parseClasses(lines, parsedClasses);
        Parser.parseFields(lines, parsedFields);
        return 0;
    }

    private static byte[] loadMappingBytes() {
        try (InputStream in = MinecraftMappings.class
                .getClassLoader()
                .getResourceAsStream("mappings/mappingsExt.txt")) {
            if (in != null) return in.readAllBytes();
        } catch (IOException ignored) {}
        try {
            Path p = Path.of(mappingsPath);
            if (Files.exists(p)) return Files.readAllBytes(p);
        } catch (IOException ignored) {}
        try {
            Path p = Path.of("..", "mappings", "mappingsExt.txt");
            if (Files.exists(p)) return Files.readAllBytes(p);
        } catch (IOException ignored) {}

        return new byte[0];
    }

    public static String getClassFromKey(String key) {
        return parsedClasses.get(key);
    }

    public static String getFieldFromKey(String key) {
        String v = parsedFields.get(key);
        return v == null ? "" : v;
    }

    public static long findMinecraftClass(String name) {
        Long cached = klasses.get(name);
        if (cached != null && cached != 0L) return cached;

        String mapped = getClassFromKey(name);
        if (mapped == null) mapped = name;

        long klass = Jvm.findClass(mapped);
        klasses.put(name, klass);
        return klass;
    }

    public static int findMinecraftField(long klass, String ownerClass,
                                         String fieldName, String fieldDesc) {
        String key = ownerClass + "." + fieldName + fieldDesc;
        String value = getFieldFromKey(key);

        Integer cached = fields.get(value);
        if (cached != null && cached != 0) return cached;

        int result;
        if (value.isEmpty()) {
            result = Jvm.findField(klass, fieldName, fieldDesc);
        } else {
            List<String> parts = Parser.split(value, '|');
            result = Jvm.findField(klass, parts.get(0), parts.get(1));
        }
        fields.put(value, result);
        return result;
    }
}
