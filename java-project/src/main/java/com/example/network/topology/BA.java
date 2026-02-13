package com.example.network.topology;

import com.example.network.Network;
import java.util.*;

public class BA {
    /**
     * BAモデル（Barabási–Albert型スケールフリーネットワーク）を生成
     * @param N ノード数
     * @param m0 初期完全グラフの頂点数
     * @param m 各新規ノードが接続するエッジ数
     * @param seed 乱数シード（省略可）
     * @return 生成されたGraphインスタンス
     */
    public static Network generateBA(int N, int m0, int m, long seed) {
        if (N <= 0) throw new IllegalArgumentException("ノード数Nは正の整数である必要があります");
        if (m0 <= 0 || m0 > N) throw new IllegalArgumentException("初期完全グラフの頂点数m0は1〜Nの範囲で指定してください");
        if (m < 0 || m > m0) throw new IllegalArgumentException("各新規ノードが接続するエッジ数mは0以上m0以下である必要があります");

        Random random = new Random(seed);

        // 総エッジ数（undirected）
        long totalEdgesLong = (long) m0 * (m0 - 1) / 2 + (long) (N - m0) * m;
        if (totalEdgesLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("エッジ数が大きすぎます: " + totalEdgesLong);
        }
        int totalEdges = (int) totalEdgesLong;

        // stubList は 2*総エッジ数（両端点）
        int[] stubList = new int[2 * totalEdges];
        int[] s = new int[totalEdges];
        int[] d = new int[totalEdges];

        int e = 0;       // 現在のエッジ本数
        int stubLen = 0; // 現在のスタブ数 (=2*e)

        // 初期完全グラフ
        for (int i = 0; i < m0; i++) {
            for (int j = i + 1; j < m0; j++) {
                s[e] = i;
                d[e] = j;
                stubList[stubLen++] = i;
                stubList[stubLen++] = j;
                e++;
            }
        }

        // 新規ノード追加
        for (int i = m0; i < N; i++) {
            if (m == 0) continue;

            if (stubLen == 0) {
                throw new IllegalStateException("優先的選択のためのスタブが存在しません（初期辺が0本です）");
            }

            Set<Integer> connected = new HashSet<>(Math.max(16, m * 2));
            while (connected.size() < m) {
                int target = stubList[random.nextInt(stubLen)]; // 有効範囲のみ
                if (target != i) { // 念のため（通常 i は stubList にいない）
                    connected.add(target);
                }
            }

            for (int target : connected) {
                s[e] = i;
                d[e] = target;
                stubList[stubLen++] = i;
                stubList[stubLen++] = target;
                e++;
            }
        }

        // Network オブジェクトの構築
        int[] deg = new int[N];
        for (int i = 0; i < e; i++) {
            deg[s[i]]++;
            deg[d[i]]++;
        }

        int[] edgeListFinal = new int[2 * e];
        int[] addressList = new int[N];
        int[] cursorList = new int[N];

        // addressListとcursorListの初期化
        int pos = 0;
        for (int i = 0; i < N; i++) {
            addressList[i] = pos;
            cursorList[i] = pos;
            pos += deg[i];
        }

        for (int curEdges = 0; curEdges < e; curEdges++) {
            int start = s[curEdges];
            int end = d[curEdges];

            // 無効なエッジチェック
            if (start < 0 || end < 0 || start >= N || end >= N) {
                throw new IllegalArgumentException("無効なエッジ: " + start + " - " + end);
            }
            edgeListFinal[cursorList[start]] = end;
            edgeListFinal[cursorList[end]] = start;
            cursorList[start]++;
            cursorList[end]++;
        }

        // グラフオブジェクトの設定
        Network graph = new Network();
        graph.N = N;
        graph.networkType = "BA";
        graph.edgeList = edgeListFinal;
        graph.addressList = addressList;
        graph.cursorList = cursorList;
        return graph;
    }

    /**
     * シード省略版
     */
    public static Network generateBA(int N, int m0, int m) {
        return generateBA(N, m0, m, System.currentTimeMillis());
    }
}
