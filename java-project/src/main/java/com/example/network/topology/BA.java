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

        int[] deg = new int[N];
        int[] edgeList = new int[(int)(m0 * (m0 - 1) + 2 * m * (N - m0))];

        // 初期完全グラフのエッジを設定
        int numEdges = 0;
        for (int i = 0; i < m0; i++) {
            for (int j = i + 1; j < m0; j++) {
                edgeList[2 * numEdges] = i;
                edgeList[2 * numEdges + 1] = j;
                deg[i]++;
                deg[j]++;
                numEdges++;
            }
        }

        // 新規ノードの追加
        for (int i = m0; i < N; i++) {
            // 既存のノードのリストを作成（重複を許可）
            List<Integer> existingNodes = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                for (int k = 0; k < deg[j]; k++) {
                    existingNodes.add(j);
                }
            }
            
            // m個のエッジを接続
            for (int j = 0; j < m; j++) {
                if (existingNodes.isEmpty()) {
                    break;
                }
                
                // 優先度付き選択（次数に比例）
                int r = random.nextInt(existingNodes.size());
                int target = existingNodes.get(r);
                
                // エッジを追加
                edgeList[2 * numEdges] = i;
                edgeList[2 * numEdges + 1] = target;
                deg[i]++;
                deg[target]++;
                numEdges++;
                
                // 選択されたノードをリストから削除（重複接続を避けるため）
                existingNodes.remove(r);
            }
        }

        int[] edgeListFinal = new int[2 * numEdges];
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
            int start = edgeList[2 * curEdges];
            int end = edgeList[2 * curEdges + 1];

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
