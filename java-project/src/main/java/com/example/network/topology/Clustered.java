package com.example.network.topology;

import com.example.network.Network;

import java.util.*;

/**
 * 三角形閉包（友達の友達）を用いてクラスタ係数を制御しつつ、
 * 任意の次数分布（次数列）に一致する単純無向グラフを生成する構成モデル。
 *
 * - 与えた次数列に厳密一致（全ノードで指定次数に）
 * - 確率 pTriangle で二段近傍から端点を選び、三角形を作りやすくする
 * - 多重辺/自己ループはその場で拒否し、成立するまでリトライ
 *
 * 使い方（k-正則）:
 *   Network g = Clustered.generate(N, k, 0.3, 1234L);
 * 使い方（任意次数列）:
 *   int[] deg = ...; // 長さN、sum(deg)は偶数
 *   Network g = Clustered.generate(deg, 0.3, 1234L);
 */
public class Clustered {

    private static final int MAX_PICK_TRIES = 10_000; // 極端な詰まり対策

    /**
     * 任意次数列版。
     * @param degree 各ノードの目標次数（長さN）
     * @param pTriangle 二段近傍から選ぶ確率（クラスタ係数制御）
     * @param seed 乱数シード
     */
    public static Network generate(int[] degree, double pTriangle, long seed) {
        if (degree == null) throw new IllegalArgumentException("degreeがnullです");
        final int N = degree.length;
        if (N <= 0) throw new IllegalArgumentException("Nは正である必要があります");
        if (pTriangle < 0 || pTriangle > 1) throw new IllegalArgumentException("pTriangleは[0,1]の範囲");

        long sum = 0L;
        for (int i = 0; i < N; i++) {
            int d = degree[i];
            if (d < 0) throw new IllegalArgumentException("次数は非負である必要があります: index=" + i);
            if (d >= N) throw new IllegalArgumentException("次数はN未満である必要があります: index=" + i);
            sum += d;
        }
        if ((sum & 1L) != 0L) throw new IllegalArgumentException("sum(degree)は偶数である必要があります");

        final int M = (int) (sum / 2L); // 辺数
        final Random rnd = new Random(seed);

        // 近傍集合（多重辺チェック用）
        @SuppressWarnings("unchecked")
        HashSet<Integer>[] nbr = new HashSet[N];
        for (int i = 0; i < N; i++) nbr[i] = new HashSet<>(Math.max(4, degree[i] * 2));

        // 残スタブ数（指定次数から開始）
        int[] rem = Arrays.copyOf(degree, N);
        long totalStubs = sum;

        // 出力用エッジ
        int[] U = new int[M];
        int[] V = new int[M];
        int e = 0;

        // ===== メインループ：辺を1本ずつ張る =====
        while (e < M) {
            // 1) u を残スタブ重みでサンプル
            int u = sampleNodeByRemaining(rem, totalStubs, rnd);

            // 2) v を選ぶ（pTriangleで"友達の友達"を優先）
            Integer v = null;
            boolean tryTriangle = rnd.nextDouble() < pTriangle;

            if (tryTriangle && !nbr[u].isEmpty()) {
                // Γ(Γ(u)) を候補に（u と既存隣接を除外、rem>0 に限定）
                ArrayList<Integer> cand = getTwoHopCandidates(u, nbr, rem);
                if (!cand.isEmpty()) {
                    v = sampleFromSetByRemaining(cand, rem, rnd);
                }
            }

            // フォールバック：完全ランダム（ただし単純グラフ制約）
            int tries = 0;
            while (v == null) {
                if (tries++ > MAX_PICK_TRIES) {
                    throw new RuntimeException("頂点選択が収束しません（詰まり）。次数列/パラメータを見直してください。");
                }
                int cand = sampleNodeByRemaining(rem, totalStubs - 1 /*u分を仮引き*/, rnd);
                if (cand == u) continue;                 // 自己ループ禁止
                if (nbr[u].contains(cand)) continue;     // 多重辺禁止
                if (rem[cand] <= 0) continue;            // スタブなし
                v = cand;
            }

            // 3) 辺(u,v)を採用
            nbr[u].add(v);
            nbr[v].add(u);
            U[e] = u;
            V[e] = v;
            e++;

            // 4) 残スタブを更新
            rem[u]--; rem[v]--;
            totalStubs -= 2L;

            // 念のため不変条件チェック（コスト低）
            if (rem[u] < 0 || rem[v] < 0) {
                throw new IllegalStateException("残スタブが負になりました");
            }
        }

        // ===== Networkへ詰め替え（CSR風） =====
        Network g = new Network();
        g.N = N;

        // 実現次数（検査用）
        int[] deg = new int[N];
        for (int i = 0; i < M; i++) {
            deg[U[i]]++; deg[V[i]]++;
        }
        for (int i = 0; i < N; i++) {
            if (deg[i] != degree[i]) {
                throw new IllegalStateException("次数が指定と一致しません: node=" + i + " expected=" + degree[i] + " actual=" + deg[i]);
            }
        }

        // CSR 配列確保
        int totalDeg = (int) sum;
        g.edgeList    = new int[totalDeg];
        g.addressList = new int[N];
        g.cursorList  = new int[N];

        int pos = 0;
        for (int i = 0; i < N; i++) { g.addressList[i] = pos; pos += degree[i]; }

        int[] cur = new int[N];
        for (int i = 0; i < M; i++) {
            int a = U[i], b = V[i];
            int pa = g.addressList[a] + cur[a]++;
            int pb = g.addressList[b] + cur[b]++;
            g.edgeList[pa] = b;
            g.edgeList[pb] = a;
        }
        for (int i = 0; i < N; i++) g.cursorList[i] = g.addressList[i] + cur[i];

        return g;
    }

    /** k-正則のショートカット（後方互換） */
    public static Network generate(int N, int k, double pTriangle, long seed) {
        if (k < 0) throw new IllegalArgumentException("次数kは非負である必要があります");
        if (k >= N) throw new IllegalArgumentException("次数kはNより小さい必要があります");
        if (((long) N * k) % 2L != 0L) throw new IllegalArgumentException("N*kは偶数である必要があります");
        int[] deg = new int[N];
        Arrays.fill(deg, k);
        return generate(deg, pTriangle, seed);
    }

    public static Network generate(int N, int k, double pTriangle) {
        return generate(N, k, pTriangle, System.currentTimeMillis());
    }

    /** 任意次数列版（シード省略） */
    public static Network generate(int[] degree, double pTriangle) {
        return generate(degree, pTriangle, System.currentTimeMillis());
    }

    // ---------------- ユーティリティ ----------------

    /**
     * 残スタブ配列 rem[] を重みとして 1 ノードをサンプル。
     * totalStubs は sum(rem) を期待（パフォーマンスのため引数で渡す）。
     * O(N) だが N=1e4, k=O(10) 程度なら実用。
     */
    private static int sampleNodeByRemaining(int[] rem, long totalStubs, Random rnd) {
        long r = (long) (rnd.nextDouble() * totalStubs); // 0 <= r < totalStubs
        long acc = 0;
        for (int i = 0; i < rem.length; i++) {
            int w = rem[i];
            if (w <= 0) continue;
            acc += w;
            if (acc > r) return i;
        }
        // 理論上到達しないが、数値誤差等の保険
        for (int i = 0; i < rem.length; i++) if (rem[i] > 0) return i;
        throw new IllegalStateException("残スタブが0です");
    }

    /**
     * u の二段近傍 Γ(Γ(u)) から候補を集める。
     * - u 自身と Γ(u) は除外
     * - 残スタブ > 0 のみ
     */
    private static ArrayList<Integer> getTwoHopCandidates(int u, HashSet<Integer>[] nbr, int[] rem) {
        HashSet<Integer> banned = nbr[u]; // 直接の隣接（除外）
        ArrayList<Integer> out = new ArrayList<>(banned.size() * 4 + 8);

        // 重複を避けるため訪問フラグ
        // N が大きくても二段近傍は多くて k^2 オーダー
        HashSet<Integer> seen = new HashSet<>(banned.size() * 4 + 8);
        seen.add(u);
        seen.addAll(banned);

        for (int w : banned) {
            for (int x : nbr[w]) {
                if (seen.contains(x)) continue;
                if (rem[x] <= 0) continue;
                seen.add(x);
                out.add(x);
            }
        }
        return out;
    }

    /**
     * 候補集合 cand から rem[] を重みとして 1 要素をサンプル。
     */
    private static int sampleFromSetByRemaining(List<Integer> cand, int[] rem, Random rnd) {
        long tot = 0;
        for (int v : cand) tot += Math.max(0, rem[v]);
        if (tot <= 0) throw new IllegalStateException("候補に残スタブがありません");
        long r = (long) (rnd.nextDouble() * tot);
        long acc = 0;
        for (int v : cand) {
            int w = Math.max(0, rem[v]);
            acc += w;
            if (acc > r) return v;
        }
        // 保険
        return cand.get(rnd.nextInt(cand.size()));
    }
}
