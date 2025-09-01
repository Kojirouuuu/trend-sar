package com.example.simulation;

import com.example.network.Network;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Event-driven SIS simulator (single run).
 * Returns event times and the infected count at each event time.
 *
 * Reference: new-sis.py (optimized continuous-time simulation with active edges and Fenwick tree).
 */
public class SIS {

    public static final class RunResult {
        private final int[] infectedSeries;   // I(t) at each recorded event time
        private final double[] times;         // event times (starting with 0.0)

        public RunResult(int[] infectedSeries, double[] times) {
            this.infectedSeries = infectedSeries;
            this.times = times;
        }

        public int[] infectedSeries() { return infectedSeries; }
        public double[] times() { return times; }
    }

    /** Fenwick (BIT) for weighted recovery sampling. 0-indexed external API, 1-indexed internal. */
    private static final class Fenwick {
        private final int n;
        private final double[] tree; // size n+1

        Fenwick(int n) {
            this.n = n;
            this.tree = new double[n + 1];
        }

        void build(double[] arr) {
            // arr length must be n
            for (int i = 0; i <= n; i++) tree[i] = 0.0;
            for (int i = 1; i <= n; i++) {
                tree[i] += arr[i - 1];
                int j = i + (i & -i);
                if (j <= n) tree[j] += tree[i];
            }
        }

        void add(int idx, double delta) {
            int i = idx + 1;
            while (i <= n) {
                tree[i] += delta;
                i += i & -i;
            }
        }

        double prefixSum(int idx) {
            int i = idx + 1;
            double s = 0.0;
            while (i > 0) {
                s += tree[i];
                i -= i & -i;
            }
            return s;
        }

        double total() {
            return prefixSum(n - 1);
        }

        int findByCumsum(double target) {
            // Smallest idx such that prefixSum(idx) >= target, assuming 0 <= target < total()
            int i = 0;
            int bit = Integer.highestOneBit(n);
            while (bit != 0) {
                int j = i + bit;
                if (j <= n && target >= tree[j]) {
                    target -= tree[j];
                    i = j;
                }
                bit >>= 1;
            }
            int idx = Math.min(i, n - 1);
            return idx;
        }
    }

    /**
     * Single continuous-time SIS simulation returning event times and I counts.
     * - Infection rate for each active I->S edge: lambda
     * - Recovery rate for infected node u: gamma / (1 + c * k_inf[u])
     */
    public static RunResult simulateOnce(
            Network network,
            double lambda,
            double gamma,
            double rho0,
            double tmax,
            double c,
            long seed
    ) {
        final int N = network.N;

        // Build CSR-like directed edge arrays and reverse-edge indices
        int[] rowptr = new int[N + 1];
        int M = 0;
        for (int u = 0; u < N; u++) {
            int deg = network.getNeighbors(u).length;
            rowptr[u] = M;
            M += deg;
        }
        rowptr[N] = M;

        int[] edges = new int[M];
        int[] src = new int[M];
        // Map (u<<32)|v -> index
        Map<Long, Integer> uv2idx = new HashMap<>(M * 2);
        int cursor = 0;
        for (int u = 0; u < N; u++) {
            int start = cursor;
            int[] nbrs = network.getNeighbors(u);
            for (int off = 0; off < nbrs.length; off++) {
                int v = nbrs[off];
                edges[cursor] = v;
                src[cursor] = u;
                long key = (((long) u) << 32) ^ (v & 0xffffffffL);
                uv2idx.put(key, cursor);
                cursor++;
            }
            // rowptr[u] was set to start; rowptr[u+1] should be cursor
            rowptr[u + 1] = cursor;
        }
        int[] rev = new int[M];
        for (int idx = 0; idx < M; idx++) {
            int u = src[idx];
            int v = edges[idx];
            long key = (((long) v) << 32) ^ (u & 0xffffffffL);
            Integer ridx = uv2idx.get(key);
            if (ridx == null) {
                throw new IllegalStateException("Reverse edge not found; graph must be simple undirected.");
            }
            rev[idx] = ridx;
        }

        // State and counters
        byte[] state = new byte[N];          // 0 = S, 1 = I
        int[] kInf = new int[N];             // number of infected neighbors for each node
        double[] recoverRate = new double[N];// gamma/(1 + c*kInf) when infected, else 0

        // Active I->S edges management
        boolean[] activeMask = new boolean[M];
        int[] activePos = new int[M];
        for (int i = 0; i < M; i++) activePos[i] = -1;
        IntDynArray active = new IntDynArray(Math.max(8, M / 8 + 1));

        // helpers
        java.util.function.IntConsumer activate = (int eidx) -> {
            if (!activeMask[eidx]) {
                activeMask[eidx] = true;
                activePos[eidx] = active.size();
                active.add(eidx);
            }
        };
        java.util.function.IntConsumer deactivate = (int eidx) -> {
            if (!activeMask[eidx]) return;
            int pos = activePos[eidx];
            int lastIdx = active.last();
            active.set(pos, lastIdx);
            activePos[lastIdx] = pos;
            active.pop();
            activePos[eidx] = -1;
            activeMask[eidx] = false;
        };

        // Fenwick for recovery weights
        Fenwick bit = new Fenwick(N);

        // Initial infection
        int initI = (int) Math.round(N * rho0);
        if (rho0 == 0.0) initI = 0;
        else if (initI == 0 && rho0 > 0.0) initI = 1;

        int[] indices = new int[N];
        for (int i = 0; i < N; i++) indices[i] = i;
        int[] shuffled = com.example.utils.Array.shuffle(indices);
        for (int i = 0; i < initI; i++) state[shuffled[i]] = 1;

        // Initialize kInf
        for (int u = 0; u < N; u++) if (state[u] == 1) {
            int s = rowptr[u];
            int t = rowptr[u + 1];
            for (int eidx = s; eidx < t; eidx++) {
                int v = edges[eidx];
                kInf[v] += 1;
            }
        }
        // Activate I->S edges
        for (int u = 0; u < N; u++) if (state[u] == 1) {
            int s = rowptr[u];
            int t = rowptr[u + 1];
            for (int eidx = s; eidx < t; eidx++) {
                int v = edges[eidx];
                if (state[v] == 0) activate.accept(eidx);
            }
        }
        // Initialize recovery rates
        for (int u = 0; u < N; u++) if (state[u] == 1) {
            recoverRate[u] = gamma / (1.0 + c * kInf[u]);
        }
        bit.build(recoverRate);

        // Recording
        ArrayList<Integer> Iseries = new ArrayList<>();
        ArrayList<Double> Tseries = new ArrayList<>();
        Iseries.add(initI);
        Tseries.add(0.0);

        // Random
        Random rng = new Random(seed);

        double currentTime = 0.0;
        int currentInfected = initI;

        // Main loop
        while (currentTime < tmax && (currentInfected > 0 || active.size() > 0)) {
            int K = active.size();
            double totalInfect = lambda * K;
            double totalRecover = bit.total();
            double totalRate = totalInfect + totalRecover;
            if (!(totalRate > 0.0) || Double.isInfinite(totalRate) || Double.isNaN(totalRate)) break;

            // Next event time
            double u = rng.nextDouble();
            if (u <= 0.0) u = Double.MIN_VALUE; // guard
            double dt = -Math.log(u) / totalRate;
            currentTime += dt;

            double r = rng.nextDouble() * totalRate;

            if (r < totalInfect && K > 0) {
                // Infection event: pick an active edge uniformly
                int pick = rng.nextInt(K);
                int eidx = active.get(pick);
                int i = src[eidx];
                int j = edges[eidx]; // i -> j (j should be S)

                // 1) deactivate the used I->S edge
                deactivate.accept(eidx);

                // 2) infect j
                state[j] = 1;
                currentInfected += 1;

                // 3) update j's own recovery rate
                double newRj = gamma / (1.0 + c * kInf[j]);
                if (recoverRate[j] != newRj) {
                    bit.add(j, newRj - recoverRate[j]);
                    recoverRate[j] = newRj;
                }

                // 4) neighbors update
                int s = rowptr[j];
                int t = rowptr[j + 1];
                for (int ejn = s; ejn < t; ejn++) {
                    int n = edges[ejn];
                    kInf[n] += 1;
                    if (state[n] == 0) {
                        // j->n becomes I->S
                        activate.accept(ejn);
                    } else {
                        // n is I: n->j becomes I->I -> deactivate
                        deactivate.accept(rev[ejn]);
                        // recovery rate of n decreases
                        double old = recoverRate[n];
                        double neu = gamma / (1.0 + c * kInf[n]);
                        if (old != neu) {
                            bit.add(n, neu - old);
                            recoverRate[n] = neu;
                        }
                    }
                }

                Iseries.add(currentInfected);
                Tseries.add(currentTime);
            } else {
                // Recovery event: sample node by weight
                double r2 = r - totalInfect;
                if (r2 < 0) r2 = 0; // guard
                int i = bit.findByCumsum(r2);

                // neighbors update before flipping state
                int s = rowptr[i];
                int t = rowptr[i + 1];
                for (int eidx = s; eidx < t; eidx++) {
                    int n = edges[eidx];
                    kInf[n] -= 1;
                    if (state[n] == 0) {
                        // i->n was I->S, now i becomes S -> deactivate
                        deactivate.accept(eidx);
                    } else {
                        // n is I: n->i becomes I->S -> activate
                        activate.accept(rev[eidx]);
                        // recovery rate of n increases
                        double old = recoverRate[n];
                        double neu = gamma / (1.0 + c * kInf[n]);
                        if (old != neu) {
                            bit.add(n, neu - old);
                            recoverRate[n] = neu;
                        }
                    }
                }

                // flip i to S
                double old = recoverRate[i];
                if (old != 0.0) bit.add(i, -old);
                recoverRate[i] = 0.0;
                state[i] = 0;
                currentInfected -= 1;

                Iseries.add(currentInfected);
                Tseries.add(currentTime);
            }
        }

        // Convert lists to arrays
        int L = Iseries.size();
        int[] Iarr = new int[L];
        double[] Tarr = new double[L];
        for (int k = 0; k < L; k++) {
            Iarr[k] = Iseries.get(k);
            Tarr[k] = Tseries.get(k);
        }
        return new RunResult(Iarr, Tarr);
    }

    // Simple dynamic array for primitive int with swap-pop operations
    private static final class IntDynArray {
        private int[] a;
        private int n;
        IntDynArray(int cap) { this.a = new int[Math.max(1, cap)]; this.n = 0; }
        int size() { return n; }
        int get(int i) { return a[i]; }
        int last() { return a[n - 1]; }
        void set(int i, int v) { a[i] = v; }
        void add(int v) { if (n == a.length) grow(); a[n++] = v; }
        void pop() { if (n > 0) n--; }
        private void grow() {
            int[] b = new int[a.length * 2];
            System.arraycopy(a, 0, b, 0, a.length);
            a = b;
        }
    }
}
