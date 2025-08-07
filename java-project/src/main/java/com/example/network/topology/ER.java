package com.example.network.topology;

import com.example.network.Network;
import java.util.*;

public class ER {
    /**
     * ERモデル（Erdős–Rényi型ランダムグラフ）を生成
     * @param N ノード数
     * @param p エッジ生成確率（0.0〜1.0）
     * @param seed 乱数シード（省略可）
     * @return 生成されたGraphインスタンス
     */
    public static Network generateER(int N, double p, long seed) {
        if (N <= 0) throw new IllegalArgumentException("ノード数Nは正の整数である必要があります");
        if (p < 0.0 || p > 1.0) throw new IllegalArgumentException("確率pは0.0〜1.0の範囲で指定してください");

        Random random = new Random(seed);

        int[] deg = new int[N];
        List<Integer> edges = new ArrayList<>();

        // エッジ生成
        int numEdges = 0;
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                if (random.nextDouble() < p) {
                    edges.add(i);
                    edges.add(j);
                    deg[i]++;
                    deg[j]++;
                    numEdges++;
                }
            }
        }

        int[] edgeList = new int[2 * numEdges];
        int[] addressList = new int[N];
        int[] cursorList = new int[N];

        // addressListとcursorListの初期化
        int pos = 0;
        for (int i = 0; i < N; i++) {
            addressList[i] = pos;
            cursorList[i] = pos;
            pos += deg[i];
        }

        for (int curEdges = 0; curEdges < numEdges; curEdges++) {
            int start = edges.get(2 * curEdges);
            int end = edges.get(2 * curEdges + 1);

            // 無効なエッジチェック
            if (start < 0 || end < 0 || start >= N || end >= N) {
                throw new IllegalArgumentException("無効なエッジ: " + start + " - " + end);
            }
            edgeList[cursorList[start]] = end;
            edgeList[cursorList[end]] = start;
            cursorList[start]++;
            cursorList[end]++;
        }

        // グラフオブジェクトの設定
        Network graph = new Network();
        graph.N = N;
        graph.edgeList = edgeList;
        graph.addressList = addressList;
        graph.cursorList = cursorList;
        return graph;
    }

    /**
     * シード省略版
     */
    public static Network generateER(int N, double p) {
        return generateER(N, p, System.currentTimeMillis());
    }
}
