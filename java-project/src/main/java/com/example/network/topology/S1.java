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
 * Loader for a comma-separated edge list (s1.csv) into a Network.
 * Lines are of the form "u,v"; header or comment lines starting with '#' are ignored.
 */
public final class S1 {

    private S1() {}

    /**
     * Load network from the given CSV edge-list file.
     * Each non-empty, non-comment line should contain two integer node IDs separated by a comma.
     */
    public static Network loadFromCsv(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new RuntimeException("Edge list CSV not found: " + filePath);
        }

        try {
            // Read unique undirected edges and determine N and degrees.
            int maxId = -1;
            java.util.HashSet<Long> edgeSet = new java.util.HashSet<>();
            try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int sp = findComma(line);
                    if (sp <= 0 || sp >= line.length() - 1) continue;
                    int u = parseInt(line, 0, sp);
                    int v = parseInt(line, sp + 1, line.length());
                    if (u == v) continue;
                    if (u < 0 || v < 0) throw new RuntimeException("Negative node id in line: " + line);
                    int a = Math.min(u, v);
                    int b = Math.max(u, v);
                    long key = (((long)a) << 32) | (b & 0xffffffffL);
                    edgeSet.add(key);
                    if (a > maxId) maxId = a;
                    if (b > maxId) maxId = b;
                }
            }

            if (edgeSet.isEmpty()) throw new RuntimeException("No edges found in: " + filePath);
            int N = maxId + 1;

            int[] deg = new int[N];
            for (long key : edgeSet) {
                int a = (int)(key >> 32);
                int b = (int)(key & 0xffffffffL);
                deg[a]++; deg[b]++;
            }

            int[] addressList = new int[N];
            int[] cursorList = new int[N];
            int totalAdj = 0;
            for (int i = 0; i < N; i++) {
                addressList[i] = totalAdj;
                cursorList[i] = totalAdj;
                totalAdj += deg[i];
            }
            int[] edgeList = new int[totalAdj];

            for (long key : edgeSet) {
                int a = (int)(key >> 32);
                int b = (int)(key & 0xffffffffL);
                edgeList[cursorList[a]] = b; cursorList[a]++;
                edgeList[cursorList[b]] = a; cursorList[b]++;
            }

            Network g = new Network();
            g.N = N;
            g.networkType = "S1";
            g.edgeList = edgeList;
            g.addressList = addressList;
            g.cursorList = cursorList;
            return g;

        } catch (IOException e) {
            throw new RuntimeException("Failed to read CSV edge list: " + filePath, e);
        }
    }

    /**
     * Convenience loader for the repository's s1.csv.
     * Tries absolute and repo-relative paths.
     */
    public static Network loadDefault() {
        String[] candidates = new String[] {
            "/Users/black/trend-sar/networks/s1.csv",
            "../networks/s1.csv",
            "networks/s1.csv"
        };
        for (String p : candidates) {
            Path path = Paths.get(p);
            if (Files.exists(path)) {
                return loadFromCsv(p);
            }
        }
        throw new RuntimeException("s1.csv not found in expected locations.");
    }

    // Helpers
    private static int findComma(String s) {
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == ',') return i;
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
