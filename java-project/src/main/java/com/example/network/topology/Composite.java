package com.example.network.topology;

import com.example.network.Network;

import java.util.HashSet;
import java.util.Set;

/**
 * 複数の Network を合成するユーティリティ。
 * - overlay: 同一ノード集合上でエッジを和集合（重複排除）
 * - disjointUnion: ノード集合を連結せずに直和（ブロック対角）
 */
public final class Composite {

    private Composite() {}

    /**
     * 同一ノード集合上に複数ネットワークを重ね合わせた合成ネットワークを返す。
     * すべての引数ネットワークは同じ N を持つ必要がある。
     *
     * @param networks 合成対象ネットワーク（N が一致していること）
     * @return エッジ集合の和（重複除去）を持つ合成ネットワーク
     * @throws IllegalArgumentException N が一致しない場合や引数が不正な場合
     */
    public static Network overlay(Network... networks) {
        if (networks == null || networks.length == 0) {
            throw new IllegalArgumentException("少なくとも1つのNetworkを指定してください");
        }

        int N = networks[0].N;
        for (Network g : networks) {
            if (g == null) throw new IllegalArgumentException("Networkにnullが含まれています");
            if (g.N != N) throw new IllegalArgumentException("overlay対象のNが一致しません");
        }

        @SuppressWarnings("unchecked")
        Set<Integer>[] nbr = new Set[N];
        for (int i = 0; i < N; i++) nbr[i] = new HashSet<>();

        // 各ネットワークの隣接を統合（重複は Set が除去）
        for (Network g : networks) {
            for (int i = 0; i < N; i++) {
                for (int p = g.addressList[i]; p < g.cursorList[i]; p++) {
                    int j = g.edgeList[p];
                    if (i != j) { // 念のため自己ループ除外
                        nbr[i].add(j);
                        nbr[j].add(i); // 無向に保つ
                    }
                }
            }
        }

        return fromNeighborSets(nbr);
    }

    /**
     * ネットワークの直和（disjoint union）を返す。異なる N 同士も結合可能。
     * 各ネットワーク間にエッジは追加しない（ブロック対角）。
     *
     * @param networks 合成対象ネットワーク
     * @return 直和ネットワーク
     * @throws IllegalArgumentException 引数が不正な場合
     */
    public static Network disjointUnion(Network... networks) {
        if (networks == null || networks.length == 0) {
            throw new IllegalArgumentException("少なくとも1つのNetworkを指定してください");
        }

        int totalN = 0;
        for (Network g : networks) {
            if (g == null) throw new IllegalArgumentException("Networkにnullが含まれています");
            totalN += g.N;
        }

        @SuppressWarnings("unchecked")
        Set<Integer>[] nbr = new Set[totalN];
        for (int i = 0; i < totalN; i++) nbr[i] = new HashSet<>();

        int offset = 0;
        for (Network g : networks) {
            for (int i = 0; i < g.N; i++) {
                int oi = offset + i;
                for (int p = g.addressList[i]; p < g.cursorList[i]; p++) {
                    int j = g.edgeList[p];
                    int oj = offset + j;
                    if (oi != oj) {
                        nbr[oi].add(oj);
                        nbr[oj].add(oi);
                    }
                }
            }
            offset += g.N;
        }

        return fromNeighborSets(nbr);
    }

    // 隣接集合から Network 構造へ詰め替え（CSR 風）
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

        // 各ノードの隣接を詰める
        for (int i = 0; i < N; i++) {
            for (int j : nbr[i]) {
                edgeList[cursorList[i]++] = j;
            }
        }

        Network g = new Network();
        g.N = N;
        g.addressList = addressList;
        g.cursorList = cursorList;
        g.edgeList = edgeList;
        return g;
    }
}

