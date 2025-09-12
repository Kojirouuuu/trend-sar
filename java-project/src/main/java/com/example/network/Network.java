package com.example.network;

import com.example.network.topology.BA;
import com.example.network.topology.ER;
import com.example.network.topology.RR;
import com.example.network.topology.FB;


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
            int totalEdges = 0;
            int degreeI = 0;
            int maxDegree = 0;
            int minDegree = Integer.MAX_VALUE;
            
            for (int i = 0; i < N; i++) {
                degreeI = 0; // Reset degreeI for the current node
                for (int j = 0; j < cursorList[i] - addressList[i]; j++) {
                    totalEdges += 1;
                    degreeI += 1;
                }
                maxDegree = Math.max(maxDegree, degreeI);
                minDegree = Math.min(minDegree, degreeI);
            }
            
            System.out.println("総エッジ数: " + ((int)totalEdges / 2));
            System.out.println("最大次数: " + maxDegree);
            System.out.println("最小次数: " + minDegree);
            System.out.println("平均次数: " + (double)totalEdges / N);
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

}
