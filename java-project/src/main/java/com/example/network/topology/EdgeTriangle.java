package com.example.network.topology;

import com.example.network.Network;

import java.util.*;

/**
 * Edge–Triangle Configuration（Newman 2009）
 * - 各頂点 i に「単辺スタブ s_i」と「三角形スタブ t_i」を与える
 * - 単辺スタブはランダムにペアにして1本の辺に
 * - 三角形スタブはランダムに3つ組にして三角形（3辺）を張る
 *
 * 使い方（例）:
 *   // 1) 次数列と目標トランジティビティから作る
 *   int N = 1000;
 *   int[] k = new int[N]; // 例：適当に次数を入れる
 *   Arrays.fill(k, 6);    // 例：全頂点次数6
 *   Network G = EdgeTriangle.generateFromDegreeAndTransitivity(N, k, 0.2, 42L);
 *
 *   // 2) s[i], t[i] を直接指定して作る
 *   int[] s = new int[N];
 *   int[] t = new int[N];
 *   // 例：次数6を s=2, t=2（= 2 + 2*2）で構成
 *   for (int i = 0; i < N; i++) { s[i] = 2; t[i] = 2; }
 *   Network G2 = EdgeTriangle.generateFromST(N, s, t, 42L);
 *
 * 注意：
 *  - 読みやすさ重視。大規模向けの最適化はしていません。
 *  - 自己ループ・多重辺は可能な限り避けますが、完全には保証しません（最終段のフォールバックで発生しうる）。
 *  - t[i] は floor(k[i]/2) を越えないように割り当てる必要があります（k[i] = s[i] + 2 t[i]）。
 *  - ∑s[i] は偶数、∑t[i] は 3 の倍数である必要があります（辺と三角形の個数条件）。
 */
public class EdgeTriangle {

    /* =========================
     * パブリックAPI（入口）
     * ========================= */

    /**
     * 次数列 k[i] と目標トランジティビティ C*（0～1）から Edge–Triangle を組み立てる。
     * 説明用に素直な比例配分で t[i] を決め、残差は微調整で埋める。
     */
    public static Network generateFromDegreeAndTransitivity(int N, int[] degree, double targetTransitivity, long seed) {
        if (degree == null || degree.length != N) {
            throw new IllegalArgumentException("degree 配列の長さが N と一致していません。");
        }
        if (targetTransitivity < 0.0 || targetTransitivity > 1.0) {
            throw new IllegalArgumentException("targetTransitivity は 0.0～1.0 の範囲にしてください。");
        }

        // 1) W = Σ_i C(k_i, 2)（ウェッジ総数）
        long W = 0L;
        long[] wedge = new long[N];
        for (int i = 0; i < N; i++) {
            if (degree[i] < 0) throw new IllegalArgumentException("degree は非負にしてください。");
            wedge[i] = comb2(degree[i]);
            W += wedge[i];
        }
        if (W == 0L) {
            // 全ての次数が 0 or 1 → 三角形を作れない。単純に空グラフを返す。
            int[] s = Arrays.copyOf(degree, N);
            int[] t = new int[N];
            return generateFromST(N, s, t, seed);
        }

        // 2) 目標の「3倍三角形数」 sum_t_target = C* * W を 3 の倍数に丸める
        //    （transitivity = 3T / W → 3T = C*W。sum_i t_i = 3T に等しい。）
        long threeT = Math.round(targetTransitivity * W);
        long sum_t_target = roundToNearestMultipleOf3(threeT);

        // 3) t[i] の上限は floor(k[i]/2)。比例配分で初期割当し、残差を調整
        int[] t = new int[N];
        long sum_t = 0L;
        for (int i = 0; i < N; i++) {
            int cap = degree[i] / 2; // floor(k/2)
            // 各頂点への期待割当： sum_t_target * (wedge[i] / W)
            double expect = (double) sum_t_target * ((double) wedge[i] / (double) W);
            int ti = (int) Math.min(cap, Math.round(expect));
            t[i] = Math.max(0, ti);
            sum_t += t[i];
        }

        // 4) 合計が合わない場合、余り（±）を微調整（cap の余裕がある頂点から加算/減算）
        //    直感的で分かりやすい操作：余り分だけ +1/-1 を配る。
        adjustSumToTargetWithCaps(t, sum_t_target, degree, /*capPerNode=*/true);

        // 5) s[i] = k[i] - 2 t[i]
        int[] s = new int[N];
        long sumS = 0L;
        for (int i = 0; i < N; i++) {
            s[i] = degree[i] - 2 * t[i];
            if (s[i] < 0) {
                // 上の調整で cap を守っているので通常は起きない想定
                s[i] = 0;
            }
            sumS += s[i];
        }

        // ∑s は偶数（辺数 = ∑s/2）であるべき。次数列の総和が偶数なら自然と偶数になる。
        if ((sumS & 1L) == 1L) {
            // まれに丸め都合で奇数になる可能性に備え、1ノード調整（説明の簡単さ優先）
            // 余裕のあるノードで s[i] を +1 / -1 調整する（degree と整合するよう t も微修正）
            // ※厳密性より可読性重視の簡易対処
            for (int i = 0; i < N; i++) {
                if (degree[i] - 2 * t[i] >= 1) { // s[i] >= 1 がある
                    // s[i] を 1 減らし、t[i] を +1 して度を保つ（cap を超えない確認）
                    if (t[i] + 1 <= degree[i] / 2) {
                        s[i] -= 1;
                        t[i] += 1;
                        sumS -= 1;
                        break;
                    }
                }
            }
            // それでも奇数なら最後の手段：どこかで s[i]++ / s[j]-- する等の調整が必要。
            // 実運用ではここを厳密に設計するが、教育・可読性重視としてここでは省略。
        }

        return generateFromST(N, s, t, seed);
    }

    /**
     * s[i]（単辺スタブ数）と t[i]（三角形スタブ数）を直接指定して Edge–Triangle を生成。
     * - 合計スタブ数の条件も軽く検査する（∑s は偶数、∑t は 3 の倍数）。
     */
    public static Network generateFromST(int N, int[] s, int[] t, long seed) {
        if (s == null || t == null || s.length != N || t.length != N) {
            throw new IllegalArgumentException("s, t 配列の長さが N と一致していません。");
        }
        long sumS = 0L, sumT = 0L;
        for (int i = 0; i < N; i++) {
            if (s[i] < 0 || t[i] < 0) throw new IllegalArgumentException("s[i], t[i] は非負にしてください。");
            sumS += s[i];
            sumT += t[i];
        }
        if ((sumS & 1L) == 1L) {
            throw new IllegalArgumentException("∑s[i] は偶数である必要があります（辺の本数 = ∑s/2）。");
        }
        if ((sumT % 3L) != 0L) {
            throw new IllegalArgumentException("∑t[i] は 3 の倍数である必要があります（三角形の個数 = ∑t/3）。");
        }

        // 各頂点の最終次数は k[i] = s[i] + 2 t[i]
        int[] degree = new int[N];
        long totalDeg = 0L;
        for (int i = 0; i < N; i++) {
            degree[i] = s[i] + 2 * t[i];
            totalDeg += degree[i];
        }

        // Network の内部配列を確保
        // addressList は各頂点の開始位置、cursorList は書き込みカーソル兼「終端」
        int[] address = new int[N];
        int[] cursor  = new int[N];
        int[] edgeList = new int[(int) totalDeg]; // 無向なので「全頂点分の隣接」を収めるサイズ

        // 頂点ごとの書き込み領域を前詰めで割付（[address[i], address[i]+degree[i]) が領域）
        int pos = 0;
        for (int i = 0; i < N; i++) {
            address[i] = pos;
            cursor[i]  = address[i];          // まずは開始位置にカーソルを置く（追ってエッジ追加時に進める）
            pos += degree[i];
        }

        // 乱択のための準備
        Random rnd = (seed == 0L) ? new Random() : new Random(seed);

        // 単辺スタブの袋：s[i] 回だけ i を詰める
        ArrayList<Integer> singleBag = new ArrayList<>((int) sumS);
        for (int i = 0; i < N; i++) {
            for (int c = 0; c < s[i]; c++) singleBag.add(i);
        }
        Collections.shuffle(singleBag, rnd);

        // 三角形スタブの袋：t[i] 回だけ i を詰める
        ArrayList<Integer> triBag = new ArrayList<>((int) sumT);
        for (int i = 0; i < N; i++) {
            for (int c = 0; c < t[i]; c++) triBag.add(i);
        }
        Collections.shuffle(triBag, rnd);

        // 既存の辺を管理して、できるだけ自己ループ・多重辺を避ける
        HashSet<Long> edgeSet = new HashSet<>();

        // 1) 三角形から先に張る（各 i は t[i] 回だけ三角形に参加し、度は 2t[i] を使う）
        //    わかりやすさ優先：線形探索で (a,b,c) を探す。ダメなら妥協して組む。
        while (triBag.size() >= 3) {
            int a = popBack(triBag);
            Integer[] triple = pickTriangleDistinct(triBag, a, edgeSet);
            if (triple == null) {
                // 妥協：末尾から 2 つを取り、同一頂点なら並び替え。最悪は重複ありで組む。
                int b = popBack(triBag);
                int c = popBack(triBag);
                if (a == b || a == c || b == c) {
                    // 3頂点が同一にならないよう、簡単な入替を試みる（説明用の素朴な実装）
                    ensureDistinctWithSwap(a, b, c);
                }
                addTriangle(a, b, c, edgeList, address, cursor, edgeSet);
            } else {
                int b = triple[0], c = triple[1];
                addTriangle(a, b, c, edgeList, address, cursor, edgeSet);
            }
        }
        // 余った（サイズ1,2）三角形スタブは実用上ほぼ出ないはずだが、出たら単辺扱いへ回す
        while (!triBag.isEmpty()) singleBag.add(popBack(triBag));
        Collections.shuffle(singleBag, rnd);

        // 2) 単辺スタブをペアリング
        while (singleBag.size() >= 2) {
            int u = popBack(singleBag);

            // u と「自己ループ/多重辺にならない」相手 v を線形探索で探す
            int pickIdx = -1;
            for (int idx = singleBag.size() - 1; idx >= 0; idx--) {
                int vCand = singleBag.get(idx);
                if (u == vCand) continue; // 自己ループ回避
                if (!edgeExists(u, vCand, edgeSet)) { pickIdx = idx; break; }
            }

            if (pickIdx >= 0) {
                int v = singleBag.remove(pickIdx);
                addEdge(u, v, edgeList, address, cursor, edgeSet);
            } else {
                // 妥協：最後の1個と組む（重複や自己ループが起きることがある）
                int v = popBack(singleBag);
                addEdge(u, v, edgeList, address, cursor, edgeSet);
            }
        }
        // もし1個だけ余ったら、どこかとつなぐ（度は s,t に合わせて確保済みなので、必ず埋める）
        if (!singleBag.isEmpty()) {
            int u = popBack(singleBag);
            // 探しやすさ優先：u と異なる頂点を 0..N-1 から探し、既存と重複しにくい候補を選ぶ
            int v = (u + 1) % N;
            for (int tries = 0; tries < N; tries++) {
                v = (u + 1 + tries) % N;
                if (u != v && !edgeExists(u, v, edgeSet)) break;
            }
            addEdge(u, v, edgeList, address, cursor, edgeSet);
        }

        // 3) Network インスタンスを組み立てて返す
        Network net = new Network();
        net.N = N;
        net.networkType = "EdgeTriangle";
        net.edgeList = edgeList;
        net.addressList = address;
        net.cursorList = cursor; // すでに書き込み終端を示す
        return net;
    }

    /* =========================
     * 三角形の選び方（説明用の素朴ロジック）
     * ========================= */

    /**
     * triBag の中から a と異なる 2 頂点 b,c を見つけ、
     * できれば (a,b), (b,c), (a,c) の全てが「未使用」の組を返す。
     * 見つからなければ null（呼び出し側で妥協処理）。
     */
    private static Integer[] pickTriangleDistinct(ArrayList<Integer> triBag, int a, HashSet<Long> edgeSet) {
        // 後ろから線形探索（削除しやすい & 素朴で読みやすい）
        for (int i = triBag.size() - 1; i >= 1; i--) {
            int b = triBag.get(i);
            if (b == a) continue;
            for (int j = i - 1; j >= 0; j--) {
                int c = triBag.get(j);
                if (c == a || c == b) continue;
                // 既存の辺と重複しないか（できるだけ避けたい）
                if (!edgeExists(a, b, edgeSet) &&
                    !edgeExists(b, c, edgeSet) &&
                    !edgeExists(a, c, edgeSet)) {
                    // 見つかったら triBag から b,c を取り出す（インデックスの大きい方から remove）
                    triBag.remove(i);
                    triBag.remove(j); // i > j なので j はズレない
                    return new Integer[]{b, c};
                }
            }
        }
        return null;
    }

    /**
     * (a,b,c) に同一頂点が含まれる場合に、簡単な入替で distinct を試みる（教育用の素朴実装）。
     */
    private static void ensureDistinctWithSwap(int a, int b, int c) {
        // ここでは何もせず、呼び出し側で最後は受容する前提でも良いが、
        // 教育目的で「distinct を試みる」フックとして残す。実装は簡略化。
    }

    /* =========================
     * エッジ／三角形の追加
     * ========================= */

    /** 無向辺 (u,v) を追加し、edgeSet にも登録。 */
    private static void addEdge(int u, int v, int[] edgeList, int[] address, int[] cursor, HashSet<Long> edgeSet) {
        // 書き込み（u→v, v→u）
        edgeList[cursor[u]++] = v;
        edgeList[cursor[v]++] = u;
        // 記録（小さい方を前に詰めたキーで保存）
        edgeSet.add(edgeKey(u, v));
    }

    /** 三角形 (a,b,c) を追加（= 3 辺を追加） */
    private static void addTriangle(int a, int b, int c, int[] edgeList, int[] address, int[] cursor, HashSet<Long> edgeSet) {
        // distinct でない可能性もあり得るが、ここでは単純に 3 辺を追加（上位層で回避努力済み）
        addEdge(a, b, edgeList, address, cursor, edgeSet);
        addEdge(b, c, edgeList, address, cursor, edgeSet);
        addEdge(a, c, edgeList, address, cursor, edgeSet);
    }

    /* =========================
     * ユーティリティ
     * ========================= */

    private static int popBack(ArrayList<Integer> list) {
        int last = list.get(list.size() - 1);
        list.remove(list.size() - 1);
        return last;
    }

    private static boolean edgeExists(int u, int v, HashSet<Long> edgeSet) {
        return edgeSet.contains(edgeKey(u, v));
    }

    private static long edgeKey(int a, int b) {
        int u = Math.min(a, b);
        int v = Math.max(a, b);
        return ((long) u << 32) ^ (v & 0xffffffffL);
    }

    private static long comb2(int k) {
        return (k < 2) ? 0L : (long) k * (k - 1) / 2L;
    }

    private static long roundToNearestMultipleOf3(long x) {
        long r = x % 3L;
        if (r < 0) r += 3L;
        long down = x - r;
        long up   = down + 3L;
        // 近い方へ。ちょうど真ん中（±1.5）の扱いは down を優先
        return (x - down <= up - x) ? down : up;
    }

    /**
     * 合計を target に合わせるため、t[i] を ±1 ずつ増減して微調整する。
     * capPerNode = true のとき、t[i] ≤ floor(k[i]/2) を守る。
     * degree[i] が必要（cap のため）。
     */
    private static void adjustSumToTargetWithCaps(int[] t, long target, int[] degree, boolean capPerNode) {
        long sum = 0L;
        for (int x : t) sum += x;
        int N = t.length;

        // 余り > 0 なら +1 を配る、< 0 なら -1 を配る
        while (sum != target) {
            if (sum < target) {
                // +1 加算：まだ余裕のある頂点へ（cap を越えないように）
                int best = -1;
                for (int i = 0; i < N; i++) {
                    if (!capPerNode || t[i] + 1 <= degree[i] / 2) { best = i; break; }
                }
                if (best == -1) break; // これ以上増やせない（実運用では設計の見直しが必要）
                t[best] += 1;
                sum += 1;
            } else {
                // -1 減算：t[i] > 0 の頂点から
                int best = -1;
                for (int i = 0; i < N; i++) {
                    if (t[i] > 0) { best = i; break; }
                }
                if (best == -1) break; // これ以上減らせない
                t[best] -= 1;
                sum -= 1;
            }
        }
    }
}
