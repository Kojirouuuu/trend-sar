package com.example.network.topology;

import com.example.network.Network;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * 2つのランダムレギュラーグラフ（RR）を生成し、両者の間に指定本数の橋渡しエッジを追加した
 * 合成ネットワークを構築するユーティリティ。
 */
public final class TwoRR {

    private TwoRR() {}

    /**
     * RR(N1,k1) と RR(N2,k2) を生成し、モジュール間に edgeNum 本の無向エッジを追加して返す。
     * 橋渡しエッジは重複なし（単純グラフ）でランダムに選ぶ。
     */
    public static Network generate2RR(int N1, int k1, int N2, int k2, int edgeNum, long seed) {
        if (N1 <= 0 || N2 <= 0) throw new IllegalArgumentException("N1,N2は正である必要があります");
        if (k1 < 0 || k2 < 0) throw new IllegalArgumentException("k1,k2は非負である必要があります");
        if (k1 >= N1) throw new IllegalArgumentException("k1はN1より小さい必要があります");
        if (k2 >= N2) throw new IllegalArgumentException("k2はN2より小さい必要があります");
        if ((((long)N1) * k1) % 2L != 0L) throw new IllegalArgumentException("N1*k1は偶数である必要があります");
        if ((((long)N2) * k2) % 2L != 0L) throw new IllegalArgumentException("N2*k2は偶数である必要があります");
        if (edgeNum < 0) throw new IllegalArgumentException("edgeNumは0以上である必要があります");
        long maxCross = (long) N1 * (long) N2;
        if ((long) edgeNum > maxCross) throw new IllegalArgumentException("edgeNumが大きすぎます（N1*N2を超過）");

        // 2つのRRを生成
        long base = (seed == 0L ? System.currentTimeMillis() : seed);
        Network gA = RR.generateRR(N1, k1, base ^ 0x9E3779B97F4A7C15L);
        Network gB = RR.generateRR(N2, k2, base ^ 0xBF58476D1CE4E5B9L);

        final int N = N1 + N2;

        // 各ノードの初期次数（内部エッジのみ）
        int[] deg = new int[N];
        long sumDeg = 0L;
        for (int i = 0; i < N1; i++) { deg[i] = gA.degree(i); sumDeg += deg[i]; }
        for (int j = 0; j < N2; j++) { deg[N1 + j] = gB.degree(j); sumDeg += deg[N1 + j]; }

        // 交差エッジを選ぶ（重複なし）
        Set<Long> cross = new HashSet<>(Math.max(16, edgeNum * 2));
        final long MAX_TRIES = Math.max(10L * Math.max(1, edgeNum), 100_000L);
        long tries = 0L;
        Random rnd = new Random(base ^ 0x94D049BB133111EBL);
        while (cross.size() < edgeNum) {
            if (tries++ > MAX_TRIES) {
                throw new RuntimeException("交差エッジの選択が収束しません。edgeNumが大きすぎる可能性があります。");
            }
            int u = rnd.nextInt(N1);
            int v = N1 + rnd.nextInt(N2);
            long key = (((long)u) << 32) | (v & 0xffffffffL);
            if (cross.add(key)) {
                deg[u]++; deg[v]++;
                sumDeg += 2L;
            }
        }

        // CSR配列の確保
        int[] addressList = new int[N];
        int[] cursorList  = new int[N];
        int[] edgeList    = new int[(int) sumDeg];
        int pos = 0;
        for (int i = 0; i < N; i++) {
            addressList[i] = pos;
            cursorList[i] = pos;
            pos += deg[i];
        }

        // 一時カーソル
        int[] cur = new int[N];
        System.arraycopy(addressList, 0, cur, 0, N);

        // 内部エッジ（A）
        for (int i = 0; i < N1; i++) {
            int[] nb = gA.getNeighbors(i);
            for (int w : nb) edgeList[cur[i]++] = w;
        }

        // 内部エッジ（B）→ グローバルへ +N1 シフト
        for (int j = 0; j < N2; j++) {
            int gj = N1 + j;
            int[] nb = gB.getNeighbors(j);
            for (int w : nb) edgeList[cur[gj]++] = N1 + w;
        }

        // 交差エッジ（双方向）
        for (long key : cross) {
            int u = (int) (key >> 32);
            int v = (int) (key & 0xffffffffL);
            edgeList[cur[u]++] = v;
            edgeList[cur[v]++] = u;
        }

        // finalize
        for (int i = 0; i < N; i++) cursorList[i] = cur[i];

        Network g = new Network();
        g.N = N;
        g.networkType = "TwoRR";
        g.addressList = addressList;
        g.cursorList = cursorList;
        g.edgeList = edgeList;
        return g;
    }

    /** シード省略版 */
    public static Network generate2RR(int N1, int k1, int N2, int k2, int edgeNum) {
        return generate2RR(N1, k1, N2, k2, edgeNum, System.currentTimeMillis());
    }
}

