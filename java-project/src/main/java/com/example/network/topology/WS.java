package com.example.network.topology;

import com.example.network.Network;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Watts–Strogatz（WS）小世界ネットワークの生成器。
 *
 * 実装方針:
 * - N 個のノードを環状格子に配置し、各ノードを両側 k/2 個ずつと接続（k は偶数）
 * - 各「前向き」エッジ (i -> i+j, j=1..k/2) を確率 p でランダムなノードへ付け替え
 * - 自己ループと多重辺は生成しない
 * - 生成後は Network の CSR 風配列（addressList, cursorList, edgeList）へ詰め替え
 */
public class WS {

    /**
     * WSモデルを生成する。
     * @param N ノード数
     * @param p 付け替え確率（0.0〜1.0）
     * @param k 初期環状格子の次数（偶数、0 ≤ k < N）
     * @param seed 乱数シード
     */
    public static Network generateWS(int N, double p, int k, long seed) {
        if (N <= 0) throw new IllegalArgumentException("ノード数Nは正の整数である必要があります");
        if (p < 0.0 || p > 1.0) throw new IllegalArgumentException("確率pは0.0〜1.0の範囲で指定してください");
        if (k < 0 || k >= N) throw new IllegalArgumentException("次数kは0以上N未満である必要があります");
        if ((k & 1) == 1) throw new IllegalArgumentException("次数kは偶数である必要があります");

        final int half = k / 2;
        Random rnd = new Random(seed);

        // 隣接集合（重複排除・存在判定用）
        @SuppressWarnings("unchecked")
        Set<Integer>[] nbr = new Set[N];
        for (int i = 0; i < N; i++) nbr[i] = new HashSet<>();

        // 1) 環状格子を構築（各 i は +1..+half へ接続）
        for (int i = 0; i < N; i++) {
            for (int j = 1; j <= half; j++) {
                int v = (i + j) % N;
                // 無向（重複防止は Set 側で）
                nbr[i].add(v);
                nbr[v].add(i);
            }
        }

        // 2) 各「前向き」エッジ (i, i+j) を確率 p で付け替え
        if (p > 0.0 && k > 0) {
            for (int i = 0; i < N; i++) {
                for (int j = 1; j <= half; j++) {
                    int v = (i + j) % N; // 現在の接続先
                    // i から「前向き」に見たエッジのみ対象（各無向辺をちょうど一度）
                    if (rnd.nextDouble() < p) {
                        // いったん現在の辺を外す
                        nbr[i].remove(v);
                        nbr[v].remove(i);

                        // 新しい接続先を選ぶ（自己/既存を避ける）
                        int newV = -1;
                        final int MAX_TRIES = 1000;
                        for (int t = 0; t < MAX_TRIES; t++) {
                            int cand = rnd.nextInt(N);
                            if (cand != i && !nbr[i].contains(cand)) { newV = cand; break; }
                        }
                        if (newV == -1) {
                            // うまく見つからない場合は線形探索で候補を探す
                            for (int cand = 0; cand < N; cand++) {
                                if (cand == i) continue;
                                if (!nbr[i].contains(cand)) { newV = cand; break; }
                            }
                        }

                        // 候補が全く無ければ（k = N-1 等）元に戻す
                        if (newV == -1) {
                            nbr[i].add(v);
                            nbr[v].add(i);
                        } else {
                            nbr[i].add(newV);
                            nbr[newV].add(i);
                        }
                    }
                }
            }
        }

        // 3) CSR 風配列に詰め替え
        int[] deg = new int[N];
        int totalHalfEdges = 0;
        for (int i = 0; i < N; i++) { deg[i] = nbr[i].size(); totalHalfEdges += deg[i]; }

        int[] addressList = new int[N];
        int[] cursorList  = new int[N];
        int[] edgeList    = new int[totalHalfEdges];

        int pos = 0;
        for (int i = 0; i < N; i++) { addressList[i] = pos; cursorList[i] = pos; pos += deg[i]; }

        for (int i = 0; i < N; i++) {
            int cur = cursorList[i];
            for (int v : nbr[i]) edgeList[cur++] = v;
            cursorList[i] = cur;
        }

        Network g = new Network();
        g.N = N;
        g.networkType = "WS";
        g.addressList = addressList;
        g.cursorList = cursorList;
        g.edgeList = edgeList;
        return g;
    }

    /** シード省略版 */
    public static Network generateWS(int N, double p, int k) {
        return generateWS(N, p, k, System.currentTimeMillis());
    }
}
