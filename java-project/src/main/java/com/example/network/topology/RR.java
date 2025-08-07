package com.example.network.topology;

import com.example.network.Network;
import java.util.*;

public class RR {
    /**
     * ランダムレギュラーグラフを生成（staticメソッド）
     * @param N ノード数
     * @param k 各ノードの次数
     * @param seed 乱数シード
     * @return 生成されたGraphインスタンス
     */
    public static Network generateRR(int N, int k, long seed) {
        if (k >= N) {
            throw new IllegalArgumentException("次数kはノード数Nより小さい必要があります");
        }
        if ((N * k) % 2 != 0) {
            throw new IllegalArgumentException("N*kは偶数である必要があります");
        }
        if (k < 0) {
            throw new IllegalArgumentException("次数kは非負数である必要があります");
        }
        Random random = new Random(seed);
        Network graph = new Network();
        graph.N = N;
        graph.edgeList = new int[N * k];
        graph.addressList = new int[N];
        graph.cursorList = new int[N];
        // addressList, cursorList初期化
        int pos = 0;
        for (int i = 0; i < N; i++) {
            graph.addressList[i] = pos;
            graph.cursorList[i] = pos;
            pos += k;
        }
        // スタブリスト
        List<Integer> stubs = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < k; j++) {
                stubs.add(i);
            }
        }
        int maxItr = 1000;
        boolean success = false;
        for (int itr = 0; itr < maxItr; itr++) {
            Collections.shuffle(stubs, random);
            boolean hasSelfLoop = false;
            boolean hasMultiEdge = false;
            // 一時的なカーソル
            int[] tempCursor = new int[N];
            Arrays.fill(tempCursor, 0);
            Set<String> edgeSet = new HashSet<>();
            for (int i = 0; i < stubs.size(); i += 2) {
                int u = stubs.get(i);
                int v = stubs.get(i + 1);
                if (u == v) {
                    hasSelfLoop = true;
                    break;
                }
                String edge = Math.min(u, v) + "," + Math.max(u, v);
                if (edgeSet.contains(edge)) {
                    hasMultiEdge = true;
                    break;
                }
                edgeSet.add(edge);
                // エッジ追加
                graph.edgeList[graph.addressList[u] + tempCursor[u]] = v;
                tempCursor[u]++;
                graph.edgeList[graph.addressList[v] + tempCursor[v]] = u;
                tempCursor[v]++;
            }
            if (!hasSelfLoop && !hasMultiEdge) {
                // カーソルを反映
                for (int i = 0; i < N; i++) {
                    graph.cursorList[i] = graph.addressList[i] + tempCursor[i];
                }
                success = true;
                break;
            }
        }
        if (!success) {
            throw new RuntimeException("ランダムレギュラーグラフの生成に失敗しました");
        }
        return graph;
    }

    /**
     * シード省略版
     */
    public static Network generateRR(int N, int k) {
        return generateRR(N, k, System.currentTimeMillis());
    }
}
