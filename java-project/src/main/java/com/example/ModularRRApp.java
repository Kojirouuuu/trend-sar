package com.example;

import com.example.network.Network;
import com.example.network.topology.ModularRR;
import com.example.simulation.SIS;
import com.example.utils.Array;

import java.util.Locale;

/**
 * ModularRR ネットワーク（M個のRRモジュール + モジュール間確率pの辺）上で
 * c × lambda × itr 回 SIS を実行して標準出力に要約を出す。
 */
public class ModularRRApp {
    public static void main(String[] args) {
        // ======== ネットワーク構成 ========
        int modules = 3;         // NumberOfModular
        int N = 3000;            // 各モジュールの頂点数
        int k_ave = 6;           // 各モジュール内RRの次数
        double p = 0.0002;       // モジュール間の辺生成確率
        long seed = 0L;          // 0 の場合は現在時刻ベース

        // ======== SIS パラメータ ========
        double gamma = 1.0;
        double rho0 = 1.0 / (modules * N); // 初期感染率: 1個感染
        double tmax = 40.0;

        // スイープ範囲
        double[] cList = Array.arange(0.0, 1.01, 0.25);
        double[] lambdaList = Array.arange(0.2, 1.01, 0.2);
        int itr = 3;

        System.out.printf(Locale.US,
                "Build ModularRR: M=%d, N=%d, k=%d, p=%.6f\n",
                modules, N, k_ave, p);
        Network net = seed == 0L ?
                ModularRR.generate(modules, N, k_ave, p) :
                ModularRR.generate(modules, N, k_ave, p, seed);

        int totalN = modules * N;
        System.out.printf(Locale.US, "Total nodes: %d\n", totalN);

        long totalRuns = (long) cList.length * lambdaList.length * itr;
        long runIndex = 0;
        for (int ci = 0; ci < cList.length; ci++) {
            double c = cList[ci];
            for (int li = 0; li < lambdaList.length; li++) {
                double lambda = lambdaList[li];
                for (int it = 0; it < itr; it++) {
                    long runSeed = (seed == 0L ? System.currentTimeMillis() : seed)
                            + it + 10_007L * li + 1_000_003L * ci;

                    SIS.RunResult res = SIS.simulateOnce(net, lambda, gamma, rho0, tmax, c, runSeed);

                    double lastT = res.times()[res.times().length - 1];
                    int lastI = res.infectedSeries()[res.infectedSeries().length - 1];
                    runIndex++;
                    System.out.printf(Locale.US,
                            "[%6d/%6d] c=%.3f lambda=%.3f itr=%d -> T_end=%.6f I_end=%d\n",
                            runIndex, totalRuns, c, lambda, it, lastT, lastI);
                }
            }
        }

        System.out.println("Completed ModularRRApp.");
    }
}

