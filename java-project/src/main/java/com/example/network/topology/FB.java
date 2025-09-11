package com.example.network.topology;

import com.example.network.Network;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Edge-list loader for the Facebook combined network.
 * Builds a Network (CSR-like) from a whitespace-separated edge list file.
 */
public final class FB {

    private FB() {}

    /**
     * Load network from a given edge-list file.
     * Lines should contain two integer node IDs separated by whitespace.
     * Self-loops are ignored; duplicate edges are included once per input line.
     */
    public static Network loadFromFile(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new RuntimeException("Edge list file not found: " + filePath);
        }

        try {
            // First pass: determine N (max node id + 1), edge count M, and degrees per node.
            int maxId = -1;
            long m = 0L;
            Map<Integer, Integer> degMap = new HashMap<>();

            try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int sp = findSplit(line);
                    if (sp <= 0 || sp >= line.length() - 1) continue; // skip malformed
                    int u = parseInt(line, 0, sp);
                    int v = parseInt(line, sp + 1, line.length());
                    if (u == v) continue; // ignore self-loop
                    if (u < 0 || v < 0) throw new RuntimeException("Negative node id in line: " + line);
                    maxId = Math.max(maxId, Math.max(u, v));
                    m++;
                    degMap.put(u, degMap.getOrDefault(u, 0) + 1);
                    degMap.put(v, degMap.getOrDefault(v, 0) + 1);
                }
            }

            if (m == 0L) throw new RuntimeException("No edges found in: " + filePath);
            int N = maxId + 1;

            int[] deg = new int[N];
            for (Map.Entry<Integer, Integer> e : degMap.entrySet()) {
                int id = e.getKey();
                int d = e.getValue();
                if (id >= 0 && id < N) deg[id] = d;
            }

            // Build CSR-like arrays
            int[] addressList = new int[N];
            int[] cursorList = new int[N];
            int totalAdj = 0;
            for (int i = 0; i < N; i++) {
                addressList[i] = totalAdj;
                cursorList[i] = totalAdj;
                totalAdj += deg[i];
            }
            int[] edgeList = new int[totalAdj];

            // Second pass: fill adjacency lists
            try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int sp = findSplit(line);
                    if (sp <= 0 || sp >= line.length() - 1) continue;
                    int u = parseInt(line, 0, sp);
                    int v = parseInt(line, sp + 1, line.length());
                    if (u == v) continue; // ignore self-loop
                    if (u < 0 || v < 0 || u >= N || v >= N) continue; // skip malformed

                    edgeList[cursorList[u]] = v; cursorList[u]++;
                    edgeList[cursorList[v]] = u; cursorList[v]++;
                }
            }

            Network g = new Network();
            g.N = N;
            g.networkType = "FB";
            g.edgeList = edgeList;
            g.addressList = addressList;
            g.cursorList = cursorList;
            return g;

        } catch (IOException e) {
            throw new RuntimeException("Failed to read edge list: " + filePath, e);
        }
    }

    /**
     * Convenience loader that tries common repo-relative locations.
     * Tries: ../networks/facebook_combined.txt then networks/facebook_combined.txt
     */
    public static Network loadDefault() {
        String[] candidates = new String[] {
            "../networks/facebook_combined.txt",
            "networks/facebook_combined.txt",
            // Fallback to absolute path if repository layout differs
            "/Users/black/trend-sar/networks/facebook_combined.txt"
        };
        for (String p : candidates) {
            Path path = Paths.get(p);
            if (Files.exists(path)) {
                return loadFromFile(p);
            }
        }
        throw new RuntimeException("facebook_combined.txt not found in expected locations.");
    }

    // Parse helpers to avoid per-line String#split allocations.
    private static int findSplit(String s) {
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t') return i;
        }
        return -1;
    }

    private static int parseInt(String s, int from, int to) {
        int i = from;
        // skip leading spaces
        while (i < to) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t') break;
            i++;
        }
        int sign = 1;
        if (i < to && s.charAt(i) == '-') { sign = -1; i++; }
        int val = 0;
        for (; i < to; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') break;
            val = val * 10 + (c - '0');
        }
        return sign * val;
    }
}
