package com.example.network;

import com.example.network.topology.BA;
import com.example.network.topology.ER;
import com.example.network.topology.RR;
import com.example.network.topology.FB;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Random;

import com.example.utils.Tips;

/**
 * グラフ構造を表現するクラス
 * ネットワークのノードとエッジの情報を管理
 */
public class Network {
    public int N;              // ノード数
    public String networkType;  // ネットワークタイプ
    public int[] edgeList;   // 各ノードの隣接ノードリスト
    public int[] addressList;  // 各ノードのアドレス情報
    public int[] cursorList;   // 各ノードの現在の隣接ノード数

    public static Network generateNetwork(String networkType, int N, int k_ave) {
        if (networkType.equals("ER")) {
            return ER.generateER(N, ((double)k_ave / (N - 1)));
        } else if (networkType.equals("BA")) {
            return BA.generateBA(N, k_ave/2, k_ave/2);
        } else if (networkType.equals("RR")) {
            return RR.generateRR(N, k_ave);
        } else if (networkType.equals("FB")) {
            return FB.loadDefault();
        } else {
            throw new IllegalArgumentException("無効なネットワークタイプ: " + networkType);
        }
    }


    /**
     * グラフの基本情報を表示
     */
    public void printGraphInfo() {
        System.out.println("--------------------------------");
        System.out.println("ノード数: " + N);

        if (N > 0 && cursorList != null) {
            long sumDegrees = 0L;
            int maxDegree = 0;
            int minDegree = Integer.MAX_VALUE;

            for (int i = 0; i < N; i++) {
                int d = degree(i);
                sumDegrees += d;
                if (d > maxDegree) maxDegree = d;
                if (d < minDegree) minDegree = d;
            }

            long undirectedEdges = sumDegrees / 2L;
            double avgDeg = (N > 0) ? (double) sumDegrees / (double) N : 0.0;

            System.out.println("総エッジ数: " + undirectedEdges);
            System.out.println("最大次数: " + maxDegree);
            System.out.println("最小次数: " + minDegree);
            System.out.println("平均次数: " + avgDeg);
            System.out.println("");
        }
    }

    /**
     * 指定されたノードの隣接ノードを表示
     * @param nodeId ノードID
     */
    public void printNodeNeighbors(int nodeId) {
        if (nodeId < 0 || nodeId >= N) {
            System.out.println("無効なノードID: " + nodeId);
            return;
        }
        
        System.out.print("ノード " + nodeId + " の隣接ノード: ");
        for (int i = addressList[nodeId]; i < cursorList[nodeId]; i++) {
            System.out.print(edgeList[i] + " ");
        }
        System.out.println();
    }

    public int[] getNeighbors(int nodeId) {
        int[] neighbors = new int[cursorList[nodeId] - addressList[nodeId]];
        for (int i = 0; i < cursorList[nodeId] - addressList[nodeId]; i++) {
            neighbors[i] = edgeList[addressList[nodeId] + i];
        }
        return neighbors;
    }

    /**
     * ノード u の局所クラスタ係数 C_u を返す。
     * C_u = 2 * T_u / (d_u * (d_u - 1))  （T_u は u を含む三角形の数）
     */
    public double localClusteringCoefficient(int u) {
        int du = degree(u);
        if (du < 2) return 0.0;

        // u の隣接ノードをマーキング
        if (_mark == null || _mark.length < N) _mark = new int[N];
        int markId = ++_markClock;
        for (int i = addressList[u]; i < cursorList[u]; i++) {
            _mark[edgeList[i]] = markId;
        }

        long trianglesAtU = 0;

        // 隣接 v ごとに v の隣接 w を見て、u の隣接集合と交差する数を数える
        // 重複を避けるため w > v のときのみカウント
        for (int iv = addressList[u]; iv < cursorList[u]; iv++) {
            int v = edgeList[iv];
            // v の隣接を走査
            for (int jw = addressList[v]; jw < cursorList[v]; jw++) {
                int w = edgeList[jw];
                if (w == u) continue;                 // u 自身は除外
                if (_mark[w] == markId && w > v) {    // u の隣接で、重複排除
                    trianglesAtU++;
                }
            }
        }

        // 組数は d_u * (d_u - 1) / 2。式に合わせて 2倍して割る。
        return (2.0 * trianglesAtU) / (double) (du * (du - 1));
    }

    /**
     * 全ノードの局所クラスタ係数の平均（平均クラスタ係数）を返す。
     */
    public double averageClusteringCoefficient() {
        if (N <= 0) return 0.0;
        double sum = 0.0;
        for (int u = 0; u < N; u++) {
            sum += localClusteringCoefficient(u);
        }
        return sum / N;
    }

    /**
     * グローバル・クラスタ係数（Transitivity）を返す。
     * 3 * (# 三角形) / (# 開いた/閉じた 2-長パス = ウェッジ)
     * ここでは「頂点ごとの三角形数」と「ウェッジ数（d_u choose 2）」を合算して計算。
     */
    public double transitivity() {
        long threeTimesTriangles = 0L; // 3 * (#triangles)
        long wedges = 0L;

        // 三角形数は各 u で T_u を求め、合計をそのまま使うと各三角形は3回数えられる
        // → 3 * (#triangles) = 3 * (sum T_u / 3) = sum T_u
        long sumTu = 0L;

        for (int u = 0; u < N; u++) {
            int du = degree(u);
            if (du >= 2) wedges += (long) du * (du - 1) / 2;

            // T_u を数える（localClusteringCoefficient と同様だが値を再利用のため分離）
            if (_mark == null || _mark.length < N) _mark = new int[N];
            int markId = ++_markClock;
            for (int i = addressList[u]; i < cursorList[u]; i++) {
                _mark[edgeList[i]] = markId;
            }
            long Tu = 0L;
            for (int iv = addressList[u]; iv < cursorList[u]; iv++) {
                int v = edgeList[iv];
                for (int jw = addressList[v]; jw < cursorList[v]; jw++) {
                    int w = edgeList[jw];
                    if (w == u) continue;
                    if (_mark[w] == markId && w > v) {
                        Tu++;
                    }
                }
            }
            sumTu += Tu;
        }

        threeTimesTriangles = sumTu; // 説明の通り sum_u T_u は 3 * (#triangles)

        if (wedges == 0L) return 0.0;
        return (double) threeTimesTriangles / (double) wedges;
    }

    /** ノード u の次数を返すユーティリティ */
    public int degree(int u) {
        return cursorList[u] - addressList[u];
    }

    /* ===== 内部ワーク配列（マーキング用） =====
    リセットコストを避けるため「世代カウンタ方式」を採用 */
    private transient int[] _mark;
    private transient int _markClock = 1;

    /**
     * Gephi 用のエッジ CSV を書き出す。
     * フォーマットは "Source,Target" のヘッダー付き。
     * 無向グラフの重複エッジは除外（{min(u,v), max(u,v)} を一意キーとして扱う）。
     *
     * 例: exportToGephiEdgesCSV("java-project/output/graph_edges.csv");
     * @param edgesCsvPath 出力する CSV ファイルパス
     */
    public void exportToGephiEdgesCSV(String edgesCsvPath) {
        Path out = Paths.get(edgesCsvPath);
        ensureParentDir(out);

        // 重複排除用セット（無向辺[u,v]の一意キー）
        // キーの作り方: (min << 32) | max
        Set<Long> seen = new HashSet<>();

        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("Source,Target");
            w.newLine();

            for (int u = 0; u < N; u++) {
                for (int i = addressList[u]; i < cursorList[u]; i++) {
                    int v = edgeList[i];
                    if (v == u) continue; // 自己ループは除外
                    int a = Math.min(u, v);
                    int b = Math.max(u, v);
                    long key = (((long) a) << 32) | (b & 0xffffffffL);
                    if (seen.add(key)) {
                        w.write(Integer.toString(a));
                        w.write(',');
                        w.write(Integer.toString(b));
                        w.newLine();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Gephi edges CSV: " + edgesCsvPath, e);
        }
    }

    /**
     * Gephi 用のノード CSV を書き出す。
     * フォーマットは "Id,Label,Degree" のヘッダー付き。
     * ラベルは Id と同じ値を書き出す。
     *
     * 例: exportToGephiNodesCSV("java-project/output/graph_nodes.csv");
     * @param nodesCsvPath 出力する CSV ファイルパス
     */
    public void exportToGephiNodesCSV(String nodesCsvPath) {
        Path out = Paths.get(nodesCsvPath);
        ensureParentDir(out);
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("Id,Label,Degree");
            w.newLine();
            for (int u = 0; u < N; u++) {
                w.write(Integer.toString(u));
                w.write(',');
                w.write(Integer.toString(u)); // Label として Id を使用
                w.write(',');
                w.write(Integer.toString(degree(u)));
                w.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Gephi nodes CSV: " + nodesCsvPath, e);
        }
    }

    /** 親ディレクトリが無ければ作成 */
    private static void ensureParentDir(Path p) {
        Path parent = p.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create directory: " + parent, e);
            }
        }
    }

    /* ===================== Gephi ノード色分け出力 ===================== */

    /**
     * 局所的（BFS で広がる）初期感染者のみ色を変えてノード CSV を出力。
     * CSV ヘッダー: "Id,Label,Degree,r,g,b"
     * - 初期感染集合: 赤 (255,0,0)
     * - それ以外: グレー (200,200,200)
     * @param nodesCsvPath 出力 CSV パス
     * @param initialInfectedNum 初期感染者数
     */
    public void exportGephiNodesWithLocalizedColors(String nodesCsvPath, int initialInfectedNum) {
        int[] seeds = Tips.bfsInitialInfect(this, initialInfectedNum);
        boolean[] isSeed = new boolean[N];
        for (int v : seeds) {
            if (0 <= v && v < N) isSeed[v] = true;
        }

        Path out = Paths.get(nodesCsvPath);
        ensureParentDir(out);
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("Id,Label,Degree,r,g,b");
            w.newLine();
            for (int u = 0; u < N; u++) {
                w.write(Integer.toString(u));
                w.write(',');
                w.write(Integer.toString(u));
                w.write(',');
                w.write(Integer.toString(degree(u)));
                w.write(',');
                if (isSeed[u]) {
                    w.write("255,0,0"); // 赤
                } else {
                    w.write("200,200,200"); // グレー
                }
                w.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Gephi nodes CSV: " + nodesCsvPath, e);
        }
    }

    /**
     * ランダムに rho0 割合の頂点を選び、色を変えてノード CSV を出力。
     * CSV ヘッダー: "Id,Label,Degree,r,g,b"
     * - 選択集合: 青 (0,120,255)
     * - それ以外: グレー (200,200,200)
     * @param nodesCsvPath 出力 CSV パス
     * @param rho0 選択割合 (0.0～1.0)
     */
    public void exportGephiNodesWithRandomColors(String nodesCsvPath, double rho0) {
        exportGephiNodesWithRandomColors(nodesCsvPath, rho0, System.nanoTime());
    }

    /** シード指定版 */
    public void exportGephiNodesWithRandomColors(String nodesCsvPath, double rho0, long seed) {
        if (rho0 < 0.0 || rho0 > 1.0) {
            throw new IllegalArgumentException("rho0 must be in [0,1]: " + rho0);
        }
        int pick = (int) Math.round(rho0 * N);
        if (pick < 0) pick = 0;
        if (pick > N) pick = N;

        boolean[] chosen = new boolean[N];
        Random rnd = new Random(seed);
        // フィッシャー–イェーツ（部分選択）
        int[] idx = new int[N];
        for (int i = 0; i < N; i++) idx[i] = i;
        for (int i = 0; i < pick; i++) {
            int j = i + rnd.nextInt(N - i);
            int tmp = idx[i]; idx[i] = idx[j]; idx[j] = tmp;
            chosen[idx[i]] = true;
        }

        Path out = Paths.get(nodesCsvPath);
        ensureParentDir(out);
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("Id,Label,Degree,r,g,b");
            w.newLine();
            for (int u = 0; u < N; u++) {
                w.write(Integer.toString(u));
                w.write(',');
                w.write(Integer.toString(u));
                w.write(',');
                w.write(Integer.toString(degree(u)));
                w.write(',');
                if (chosen[u]) {
                    w.write("0,120,255"); // 青
                } else {
                    w.write("200,200,200"); // グレー
                }
                w.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Gephi nodes CSV: " + nodesCsvPath, e);
        }
    }

    /**
     * 次数分布を保ったまま、無向エッジの一部をランダムにリワイヤリング（ダブルエッジスワップ）する。
     * - 2本の無向エッジ (a,b), (c,d) を選び、(a,d), (c,b) あるいは (a,c), (b,d) に置き換える。
     * - 自己ループや多重辺は作らないようにチェック。
     * - 各ノードの次数は不変。
     *
     * 目標は「全無向エッジ数 M のうち割合 p を置換」なので、
     * 1回のスワップで 2 辺が置換されることを踏まえて、試行回数は round(p*M/2) 回を目安にする。
     *
     * @param p 置換するエッジ割合 [0,1]
     * @return 実際に置換できたスワップ回数（置換エッジ数は 2×戻り値）
     */
    public int rewirePreservingDegree(double p) {
        return rewirePreservingDegree(p, System.nanoTime());
    }

    /** シード指定版 */
    public int rewirePreservingDegree(double p, long seed) {
        if (p <= 0.0) return 0;
        if (p > 1.0) p = 1.0;

        // 無向エッジ集合（重複除去）を構築
        List<int[]> edges = new ArrayList<>();
        Set<Long> edgeSet = new HashSet<>();
        for (int u = 0; u < N; u++) {
            for (int i = addressList[u]; i < cursorList[u]; i++) {
                int v = edgeList[i];
                if (v == u) continue; // 自己ループ回避
                int a = Math.min(u, v);
                int b = Math.max(u, v);
                long key = edgeKey(a, b);
                if (edgeSet.add(key)) {
                    edges.add(new int[]{a, b});
                }
            }
        }

        final int M = edges.size();
        if (M < 2) return 0;
        int targetSwaps = (int) Math.round((p * M) / 2.0);
        if (targetSwaps <= 0) return 0;

        Random rnd = new Random(seed);
        int success = 0;
        int attempts = 0;
        int maxAttempts = Math.max(10 * targetSwaps, 50); // 失敗を見越して適度にリトライ

        while (success < targetSwaps && attempts < maxAttempts) {
            attempts++;
            int i1 = rnd.nextInt(M);
            int i2 = rnd.nextInt(M - 1);
            if (i2 >= i1) i2++;

            int a = edges.get(i1)[0];
            int b = edges.get(i1)[1];
            int c = edges.get(i2)[0];
            int d = edges.get(i2)[1];

            // 4頂点が全て異なる必要がある
            if (a == c || a == d || b == c || b == d) continue;

            boolean pattern = rnd.nextBoolean();
            int x1, y1, x2, y2; // 置換後の 2 辺
            if (pattern) {
                // (a,b), (c,d) -> (a,d), (c,b)
                x1 = a; y1 = d; x2 = c; y2 = b;
            } else {
                // (a,b), (c,d) -> (a,c), (b,d)
                x1 = a; y1 = c; x2 = b; y2 = d;
            }

            // 自己ループ回避
            if (x1 == y1 || x2 == y2) continue;

            int m1 = Math.min(x1, y1), M1 = Math.max(x1, y1);
            int m2 = Math.min(x2, y2), M2 = Math.max(x2, y2);
            long k1 = edgeKey(m1, M1);
            long k2 = edgeKey(m2, M2);

            // 既存辺（元の2辺を除く）との重複回避
            boolean existed1 = edgeSet.contains(k1);
            boolean existed2 = edgeSet.contains(k2);
            // k1, k2 が元の (a,b), (c,d) と一致する場合は除外（置換にならない）
            if (existed1 && !(m1 == a && M1 == b) && !(m1 == c && M1 == d)) continue;
            if (existed2 && !(m2 == a && M2 == b) && !(m2 == c && M2 == d)) continue;

            // 実際の隣接リストを書き換え（無向なので双方を置換）。
            // 先に古い2辺の除去→新2辺の追加 ではなく、配列位置を置換する形で実装。
            boolean ok = true;
            ok &= replaceNeighbor(a, b, pattern ? d : c);
            ok &= replaceNeighbor(b, a, pattern ? c : d);
            ok &= replaceNeighbor(c, d, pattern ? b : a);
            ok &= replaceNeighbor(d, c, pattern ? a : b);
            if (!ok) {
                // 一貫性が崩れている場合はロールバック不能なのでスキップ（極力発生しない前提）
                continue;
            }

            // エッジ集合・エッジ配列を更新
            edgeSet.remove(edgeKey(a, b));
            edgeSet.remove(edgeKey(c, d));
            edgeSet.add(k1);
            edgeSet.add(k2);
            edges.set(i1, new int[]{m1, M1});
            edges.set(i2, new int[]{m2, M2});

            success++;
        }

        return success;
    }

    /** undirected edge のキー化: (min<<32) | max */
    private static long edgeKey(int a, int b) {
        return (((long) a) << 32) | (b & 0xffffffffL);
    }

    /** 隣接配列で u の隣接 oldN を newN に置き換える（1 箇所のみ）。*/
    private boolean replaceNeighbor(int u, int oldN, int newN) {
        for (int i = addressList[u]; i < cursorList[u]; i++) {
            if (edgeList[i] == oldN) {
                edgeList[i] = newN;
                return true;
            }
        }
        return false;
    }
}
