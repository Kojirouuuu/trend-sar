package com.example.network.topology;

import com.example.network.Network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 2つのランダムレギュラーグラフ（RR）を生成し、内部辺を edgeNum 本選んで
 * モジュール間へリワイヤリングした合成ネットワークを構築するユーティリティ。
 * 総辺数は不変（内部辺 edgeNum 本を交差辺に置き換える）。edgeNum は偶数である必要あり。
 */
public final class TwoRR {

    private TwoRR() {}

    /**
     * RR(N1,k1) と RR(N2,k2) を生成し、内部辺から edgeNum/2 本ずつ選んで
     * モジュール間へリワイヤリングする。交差辺は重複なし（単純グラフ）。
     */
    public static Network generate2RR(int N1, int k1, int N2, int k2, int edgeNum, long seed) {
        if (N1 <= 0 || N2 <= 0) throw new IllegalArgumentException("N1,N2は正である必要があります");
        if (k1 < 0 || k2 < 0) throw new IllegalArgumentException("k1,k2は非負である必要があります");
        if (k1 >= N1) throw new IllegalArgumentException("k1はN1より小さい必要があります");
        if (k2 >= N2) throw new IllegalArgumentException("k2はN2より小さい必要があります");
        if ((((long)N1) * k1) % 2L != 0L) throw new IllegalArgumentException("N1*k1は偶数である必要があります");
        if ((((long)N2) * k2) % 2L != 0L) throw new IllegalArgumentException("N2*k2は偶数である必要があります");
        if (edgeNum < 0) throw new IllegalArgumentException("edgeNumは0以上である必要があります");
        if (edgeNum % 2 != 0) throw new IllegalArgumentException("リワイヤリングではedgeNumは偶数である必要があります");
        int M1 = N1 * k1 / 2, M2 = N2 * k2 / 2; // 各モジュールの辺数
        if (edgeNum / 2 > M1 || edgeNum / 2 > M2) throw new IllegalArgumentException("edgeNum/2がモジュールの辺数を超えています");

        long base = (seed == 0L ? System.currentTimeMillis() : seed);
        Random rnd = new Random(base ^ 0x94D049BB133111EBL);
        Network gA = RR.generateRR(N1, k1, base ^ 0x9E3779B97F4A7C15L);
        Network gB = RR.generateRR(N2, k2, base ^ 0xBF58476D1CE4E5B9L);

        final int N = N1 + N2;

        // 内部辺を列挙（無向なので u < v のときのみ）
        List<int[]> edgesA = enumerateEdges(gA);
        List<int[]> edgesB = enumerateEdges(gB);

        // リワイヤする辺を選ぶ: A から edgeNum/2 本、B から edgeNum/2 本
        Collections.shuffle(edgesA, rnd);
        Collections.shuffle(edgesB, rnd);
        List<int[]> removedA = new ArrayList<>(edgesA.subList(0, edgeNum / 2));
        List<int[]> removedB = new ArrayList<>(edgesB.subList(0, edgeNum / 2));
        List<int[]> remainingA = new ArrayList<>(edgesA.subList(edgeNum / 2, edgesA.size()));
        List<int[]> remainingB = new ArrayList<>(edgesB.subList(edgeNum / 2, edgesB.size()));

        // 削除する辺の端点をスタブとして収集（A 側 edgeNum 個、B 側 edgeNum 個）
        List<Integer> stubsA = new ArrayList<>(edgeNum);
        List<Integer> stubsB = new ArrayList<>(edgeNum);
        for (int[] e : removedA) { stubsA.add(e[0]); stubsA.add(e[1]); }
        for (int[] e : removedB) { stubsB.add(e[0]); stubsB.add(e[1]); }

        // スタブをランダムにペアにして交差辺を生成（重複辺なしでちょうど edgeNum 本）
        List<int[]> crossEdges = new ArrayList<>(edgeNum);
        final long MAX_TRIES = 50_000L;
        for (long tries = 0; tries < MAX_TRIES; tries++) {
            crossEdges.clear();
            Collections.shuffle(stubsB, rnd);
            Set<Long> seen = new HashSet<>(edgeNum);
            for (int i = 0; i < edgeNum; i++) {
                int a = stubsA.get(i), b = stubsB.get(i);
                long key = ((long) a << 32) | (b & 0xffffffffL);
                if (seen.add(key)) crossEdges.add(new int[] { a, b });
            }
            if (crossEdges.size() == edgeNum) break;
        }
        if (crossEdges.size() < edgeNum) {
            throw new RuntimeException("交差辺のリワイヤリングで重複なしの組み合わせが得られません。edgeNumを減らすかシードを変えてください。");
        }

        // 最終グラフ構築: remainingA + remainingB + crossEdges から CSR を組む
        int[] deg = new int[N];
        for (int[] e : remainingA) { deg[e[0]]++; deg[e[1]]++; }
        for (int[] e : remainingB) { deg[N1 + e[0]]++; deg[N1 + e[1]]++; }
        for (int[] e : crossEdges) { deg[e[0]]++; deg[N1 + e[1]]++; }

        int[] addressList = new int[N];
        int[] cursorList = new int[N];
        int totalDeg = 0;
        for (int i = 0; i < N; i++) { addressList[i] = totalDeg; totalDeg += deg[i]; }
        int[] edgeList = new int[totalDeg];
        int[] cur = new int[N];
        System.arraycopy(addressList, 0, cur, 0, N);

        for (int[] e : remainingA) {
            edgeList[cur[e[0]]++] = e[1];
            edgeList[cur[e[1]]++] = e[0];
        }
        for (int[] e : remainingB) {
            int u = N1 + e[0], v = N1 + e[1];
            edgeList[cur[u]++] = v;
            edgeList[cur[v]++] = u;
        }
        for (int[] e : crossEdges) {
            int u = e[0], v = N1 + e[1];
            edgeList[cur[u]++] = v;
            edgeList[cur[v]++] = u;
        }
        for (int i = 0; i < N; i++) cursorList[i] = cur[i];

        Network g = new Network();
        g.N = N;
        g.networkType = "TwoRR";
        g.addressList = addressList;
        g.cursorList = cursorList;
        g.edgeList = edgeList;
        return g;
    }

    /** 無向辺を (u, v) のリストで列挙。i < j のときだけ追加するので、(i,j)と(j,i)が両方選ばれることはない。 */
    private static List<int[]> enumerateEdges(Network g) {
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < g.N; i++) {
            for (int k = g.addressList[i]; k < g.cursorList[i]; k++) {
                int j = g.edgeList[k];
                if (i < j) edges.add(new int[] { i, j });
            }
        }
        return edges;
    }

    /** シード省略版 */
    public static Network generate2RR(int N1, int k1, int N2, int k2, int edgeNum) {
        return generate2RR(N1, k1, N2, k2, edgeNum, System.currentTimeMillis());
    }
}

