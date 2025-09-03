package com.example.network.topology;

import com.example.network.Network;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * M個のモジュール（各モジュールはRRグラフ）を直和し、
 * 異なるモジュール間に確率pでランダム辺を加える合成器。
 */
public final class ModularRR {

    private ModularRR() {}

    /**
     * モジュール数M、各モジュールのノード数N、RRの次数k、モジュール間確率pのネットワークを生成。
     * 各モジュールは独立にRRを生成（seedに基づく）。モジュール間は全ペア(u in A, v in B)に対し
     * 確率pで無向辺を追加（重複は自動で除去）。
     */
    public static Network generate(int modules, int N, int k, double p, long seed) {
        if (modules <= 0) throw new IllegalArgumentException("modules は正の整数である必要があります");
        if (N <= 0) throw new IllegalArgumentException("N は正の整数である必要があります");
        if (k < 0 || k >= N) throw new IllegalArgumentException("k は 0 以上 N-1 以下");
        if (((long)N * k) % 2L != 0L) throw new IllegalArgumentException("N*k は偶数である必要があります (RR)");
        if (p < 0.0 || p > 1.0) throw new IllegalArgumentException("p は 0.0〜1.0 の範囲");

        final int totalN = modules * N;
        @SuppressWarnings("unchecked")
        Set<Integer>[] nbr = new Set[totalN];
        for (int i = 0; i < totalN; i++) nbr[i] = new HashSet<>();

        // 1) 各モジュール内のRRエッジを追加（直和）
        long base = seed == 0L ? System.currentTimeMillis() : seed;
        for (int m = 0; m < modules; m++) {
            long s = base + 1_000_003L * m;
            Network g = RR.generateRR(N, k, s);
            int offset = m * N;
            for (int u = 0; u < N; u++) {
                int ou = offset + u;
                for (int pidx = g.addressList[u]; pidx < g.cursorList[u]; pidx++) {
                    int v = g.edgeList[pidx];
                    int ov = offset + v;
                    if (ou != ov) { nbr[ou].add(ov); nbr[ov].add(ou); }
                }
            }
        }

        // 2) モジュール間のER型ランダム連結（全対ノード間で確率p）
        Random rnd = new Random(base ^ 0x9E3779B97F4A7C15L);
        for (int a = 0; a < modules; a++) {
            int ao = a * N;
            for (int b = a + 1; b < modules; b++) {
                int bo = b * N;
                for (int u = 0; u < N; u++) {
                    int ou = ao + u;
                    for (int v = 0; v < N; v++) {
                        int ov = bo + v;
                        if (rnd.nextDouble() < p) {
                            nbr[ou].add(ov);
                            nbr[ov].add(ou);
                        }
                    }
                }
            }
        }

        return fromNeighborSets(nbr);
    }

    public static Network generate(int modules, int N, int k, double p) {
        return generate(modules, N, k, p, System.currentTimeMillis());
    }

    private static Network fromNeighborSets(Set<Integer>[] nbr) {
        int N = nbr.length;
        int[] deg = new int[N];
        int totalHalfEdges = 0;
        for (int i = 0; i < N; i++) {
            deg[i] = nbr[i].size();
            totalHalfEdges += deg[i];
        }

        int[] addressList = new int[N];
        int[] cursorList = new int[N];
        int[] edgeList = new int[totalHalfEdges];

        int pos = 0;
        for (int i = 0; i < N; i++) {
            addressList[i] = pos;
            cursorList[i] = pos;
            pos += deg[i];
        }

        for (int i = 0; i < N; i++) {
            for (int j : nbr[i]) edgeList[cursorList[i]++] = j;
        }

        Network g = new Network();
        g.N = N;
        g.addressList = addressList;
        g.cursorList = cursorList;
        g.edgeList = edgeList;
        return g;
    }
}

