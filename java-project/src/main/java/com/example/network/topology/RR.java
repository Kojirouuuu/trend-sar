package com.example.network.topology;

import com.example.network.Network;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ランダムレギュラーグラフ（k-正則単純無向）の実用生成器。
 * 構成モデルで一度ペアリング→2-スイッチで不正辺を修復。
 */
public class RR {

    public static Network generateRR(int N, int k, long seed) {
        if (k < 0) throw new IllegalArgumentException("次数kは非負である必要があります");
        if (k >= N) throw new IllegalArgumentException("次数kはNより小さい必要があります");
        if (((long) N * k) % 2L != 0L) throw new IllegalArgumentException("N*kは偶数である必要があります");

        final int M2 = N * k;   // スタブ総数
        final int M  = M2 / 2;  // 辺数

        // ==== 1) スタブを並べて1回だけペアリング（多重/自己ループあり得る） ====
        int[] stubs = new int[M2];
        for (int i = 0, p = 0; i < N; i++) for (int j = 0; j < k; j++) stubs[p++] = i;

        Random rnd = new Random(seed);
        // Fisher–Yates shuffle
        for (int i = M2 - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = stubs[i]; stubs[i] = stubs[j]; stubs[j] = tmp;
        }

        // 辺配列（u[i], v[i]）
        int[] U = new int[M];
        int[] V = new int[M];
        for (int e = 0, p = 0; e < M; e++, p += 2) {
            U[e] = stubs[p];
            V[e] = stubs[p + 1];
        }

        // ==== 2) 近傍集合（重複なし判定用）と不正辺集合の構築 ====
        // 近傍は HashSet<Integer> の配列。N=1e4, k=10 程度なら十分軽い。
        @SuppressWarnings("unchecked")
        HashSet<Integer>[] nbr = new HashSet[N];
        for (int i = 0; i < N; i++) nbr[i] = new HashSet<>(k * 2);

        // 多重辺チェック用：各無向辺は (min,max) を long key に
        HashMap<Long, Integer> multiplicity = new HashMap<>(M * 2);
        Deque<Integer> badEdges = new ArrayDeque<>(); // 不正辺のインデックス（自己ループ or 多重の余剰）

        for (int e = 0; e < M; e++) {
            int a = U[e], b = V[e];
            if (a == b) { // 自己ループ
                badEdges.add(e);
                continue;
            }
            int min = Math.min(a, b), max = a ^ b ^ min;
            long key = ( (long)min << 32 ) | (max & 0xffffffffL);
            Integer cnt = multiplicity.get(key);
            if (cnt == null) {
                multiplicity.put(key, 1);
                nbr[a].add(b); nbr[b].add(a);
            } else {
                multiplicity.put(key, cnt + 1);
                badEdges.add(e); // 2本目以降は不正として後で修復
            }
        }

        // ==== 3) 2-スイッチで不正辺を解消 ====
        // 試行上限：経験的に O(M log M) 程度で十分
        final long MAX_TRIES = Math.max(10L * M, 100_000L);
        long tries = 0;
        ThreadLocalRandom tlr = ThreadLocalRandom.current();

        while (!badEdges.isEmpty() && tries < MAX_TRIES) {
            tries++;

            int e1 = badEdges.pollFirst(); // 修復対象の不正辺
            int u = U[e1], v = V[e1];

            // 置換に使う別の辺 e2 をランダムに選ぶ
            int e2 = tlr.nextInt(M);
            if (e2 == e1) continue;
            int x = U[e2], y = V[e2];

            // 端点が被っていると扱いづらいので入れ替え
            // (u,v) と (x,y) から (u,y),(x,v) を作りたい
            // 端点が衝突しないよう整形
            if (u == x || u == y || v == x || v == y) continue;

            // 新エッジ候補
            int a1 = u, b1 = y;
            int a2 = x, b2 = v;

            // 自己ループ禁止
            if (a1 == b1 || a2 == b2) continue;

            // 既存に無いか（多重禁止）
            if (nbr[a1].contains(b1) || nbr[a2].contains(b2)) continue;

            // ===== 適用 =====
            // 旧エッジ (u,v) と (x,y) を近傍から外す（(u,v) は元々多重か loop なので注意）
            // 近傍に含まれている場合のみ remove
            nbr[u].remove(v); nbr[v].remove(u);
            nbr[x].remove(y); nbr[y].remove(x);

            // 新エッジを追加
            nbr[a1].add(b1); nbr[b1].add(a1);
            nbr[a2].add(b2); nbr[b2].add(a2);

            // 辺配列を書き換え
            U[e1] = a1; V[e1] = b1;
            U[e2] = a2; V[e2] = b2;

            // e2 は正規辺 → 以降も正規のまま
            // e1 は修復済みなので何もしない（badEdges から外れている）
        }

        if (!badEdges.isEmpty()) {
            throw new RuntimeException("ランダムレギュラーグラフ生成: スイッチングで単純化できませんでした（試行上限到達）");
        }

        // ==== 4) Network へ詰め替え（CSR 風） ====
        Network g = new Network();
        g.N = N;
        g.edgeList    = new int[N * k];
        g.addressList = new int[N];
        g.cursorList  = new int[N];

        int[] deg = new int[N];
        for (int e = 0; e < M; e++) { deg[U[e]]++; deg[V[e]]++; }
        for (int d : deg) if (d != k) throw new IllegalStateException("次数がkでないノードがあります");

        int pos = 0;
        for (int i = 0; i < N; i++) { g.addressList[i] = pos; pos += k; }

        // 一時カーソル
        int[] cur = new int[N];
        for (int e = 0; e < M; e++) {
            int a = U[e], b = V[e];
            int pa = g.addressList[a] + cur[a]++;
            int pb = g.addressList[b] + cur[b]++;
            g.edgeList[pa] = b;
            g.edgeList[pb] = a;
        }
        for (int i = 0; i < N; i++) g.cursorList[i] = g.addressList[i] + cur[i];

        return g;
    }

    public static Network generateRR(int N, int k) {
        return generateRR(N, k, System.currentTimeMillis());
    }
}
