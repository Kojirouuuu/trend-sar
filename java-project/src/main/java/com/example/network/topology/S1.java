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
 * network-data/S1.csv/edges.csv からネットワークを読み込み、
 * Network インスタンスとして返す。
 * エッジ形式: CSV "source,target"（1行目は # source, target などのヘッダ）。
 */
public final class S1 {

    private S1() {}

    /** デフォルトパス: network-data/S1.csv/edges.csv（プロジェクトルート基準） */
    private static final String DEFAULT_EDGES_PATH = "network-data/S1.csv/edges.csv";

    /**
     * 指定した edges.csv パスから Network を構築する。
     * 行形式: source,target （# 始まりはスキップ）。自ループは無視。
     */
    public static Network loadFromFile(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new RuntimeException("Edge list file not found: " + filePath);
        }

        try {
            int maxId = -1;
            long m = 0L;
            Map<Integer, Integer> degMap = new HashMap<>();

            try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int comma = line.indexOf(',');
                    if (comma <= 0 || comma >= line.length() - 1) continue;
                    int u = parseInt(line.substring(0, comma).trim());
                    int v = parseInt(line.substring(comma + 1).trim());
                    if (u == v) continue;
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
                if (id >= 0 && id < N) deg[id] = e.getValue();
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

            try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int comma = line.indexOf(',');
                    if (comma <= 0 || comma >= line.length() - 1) continue;
                    int u = parseInt(line.substring(0, comma).trim());
                    int v = parseInt(line.substring(comma + 1).trim());
                    if (u == v) continue;
                    if (u < 0 || v < 0 || u >= N || v >= N) continue;

                    edgeList[cursorList[u]] = v; cursorList[u]++;
                    edgeList[cursorList[v]] = u; cursorList[v]++;
                }
            }

            Network g = new Network();
            g.N = N;
            g.networkType = "S1";
            g.edgeList = edgeList;
            g.addressList = addressList;
            g.cursorList = cursorList;
            return g;

        } catch (IOException e) {
            throw new RuntimeException("Failed to read edge list: " + filePath, e);
        }
    }

    /**
     * network-data/S1.csv/edges.csv から読み込む。
     * カレントディレクトリがプロジェクトルートでない場合は loadFromFile(絶対パス) を使用すること。
     */
    public static Network loadDefault() {
        String[] candidates = new String[] {
            DEFAULT_EDGES_PATH,
            "../network-data/S1.csv/edges.csv",
        };
        for (String p : candidates) {
            Path path = Paths.get(p);
            if (Files.exists(path)) {
                return loadFromFile(p);
            }
        }
        throw new RuntimeException("S1 edges not found. Tried: " + DEFAULT_EDGES_PATH + " and ../network-data/S1.csv/edges.csv");
    }

    private static int parseInt(String s) {
        if (s == null || s.isEmpty()) throw new RuntimeException("Empty number");
        s = s.trim();
        int i = 0;
        int sign = 1;
        if (s.charAt(0) == '-') { sign = -1; i = 1; }
        int val = 0;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') throw new RuntimeException("Invalid number: " + s);
            val = val * 10 + (c - '0');
        }
        return sign * val;
    }

    /**
     * 動作確認用: loadDefault() または第1引数で指定パスを読み込み、グラフ情報を表示。
     * 例: mvn exec:java -Dexec.mainClass="com.example.network.topology.S1"
     *     mvn exec:java -Dexec.mainClass="com.example.network.topology.S1" -Dexec.args="network-data/S1.csv/edges.csv"
     */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : null;
        Network g = path != null ? loadFromFile(path) : loadDefault();
        g.printGraphInfo();
    }
}
