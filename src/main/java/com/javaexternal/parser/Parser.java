package com.javaexternal.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Parser {

    public enum Launcher { VANILLA, FABRIC, LUNAR }
    public static List<String> split(String s, char delimiter) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == delimiter) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    public static List<String> split(String s) {
        return split(s, '\t');
    }

    public static void parse(String path, List<String> linesOut) throws IOException {
        for (String line : Files.readAllLines(Path.of(path), StandardCharsets.UTF_8)) {
            linesOut.add(line);
        }
    }
    public static void parseBytes(byte[] data, int size, List<String> linesOut) {
        String buffer = new String(data, 0, Math.min(size, data.length), StandardCharsets.UTF_8);
        int len = buffer.length();
        int start = 0;
        for (int i = 0; i < len; i++) {
            char c = buffer.charAt(i);
            if (c == '\n') {
                int end = i;
                if (end > start && buffer.charAt(end - 1) == '\r') end--;
                linesOut.add(buffer.substring(start, end));
                start = i + 1;
            }
        }
        if (start < len) {
            int end = len;
            if (buffer.charAt(end - 1) == '\r') end--;
            linesOut.add(buffer.substring(start, end));
        }
    }

    public static void parseClasses(List<String> lines, Map<String, String> klassesMap) {
        for (String raw : lines) {
            if (raw.startsWith("c\t")) {
                String local = raw.substring(2);
                List<String> values = split(local, '\t');
                if (values.size() >= 2) {
                    klassesMap.put(values.get(0), values.get(1));
                }
            }
        }
    }

    public static void parseFields(List<String> lines, Map<String, String> fieldsMap) {
        for (String raw : lines) {
            if (raw.startsWith("f\t")) {
                String local = raw.substring(2);
                List<String> values = split(local, '\t');
                if (values.size() >= 2) {
                    fieldsMap.put(values.get(0), values.get(1));
                }
            }
        }
    }
}
