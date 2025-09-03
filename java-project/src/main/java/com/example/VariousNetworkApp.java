package com.example;

import com.example.network.Network;
import com.example.network.topology.ModularRR;
import com.example.simulation.SIS;
import com.example.utils.Array;
import com.example.utils.Params;
import com.example.utils.Writer;

import java.util.Locale;
import java.util.stream.IntStream;
import java.time.LocalDateTime;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * ModularRR ネットワークで、p × c × lambda × itr を
 * batchNum 回並列処理して実行するアプリ。
 */
public class VariousNetworkApp {
    public static void main(String[] args) {
        // ======== ネットワーク（モジュール構造） ========
        int modules = 50;         // NumberOfModular
        int N = 200;            // 各モジュールの頂点数（並列処理ならやや小さめ推奨）
        int k_ave = 6;           // 各モジュール内RRの次数

        // p を動かす
        double pMin = 1.0 / ((modules - 1) * N * N), pMax = 1.0 / ((modules - 1) * N), dp = 4.0 / ((modules - 1) * N * N);

        // c を動かす
        double cMin = 0.0, cMax = 2.0, dc = 0.5;

        // lambda を動かす
        double lambdaMin = 0.00, lambdaMax = 0.30, dlambda = 0.005;

        // ======== SIS パラメータ ========
        double gamma = 1.0;
        double rho0 = 1.0; // 初期感染率: 1個感染相当
        double tmax = 60.0;

        // スイープ範囲
        double[] cList = Array.arange(cMin, cMax + dc, dc);
        double[] lambdaList = Array.arange(lambdaMin, lambdaMax + dlambda, dlambda);
        double[] pList = Array.arange(pMin, pMax + dp, dp);
        int itr = 10;

        int batchNum = 100; // 並列バッチ数（IntStream.range(0, batchNum).parallel()）
        long seed0 = 12345L; // ベースシード

        // 出力先と params.csv
        String outDir = String.format("output/various/ModularRR/M=%d_N=%d_k=%d", modules, N, k_ave);
        Params params = new Params()
                .put("network", "ModularRR")
                .put("modules", modules)
                .put("N_per_module", N)
                .put("k_ave", k_ave)
                .put("pMin", pMin)
                .put("pMax", pMax)
                .put("dp", dp)
                .put("cMin", cMin)
                .put("cMax", cMax)
                .put("dc", dc)
                .put("lambdaMin", lambdaMin)
                .put("lambdaMax", lambdaMax)
                .put("dlambda", dlambda)
                .put("gamma", gamma)
                .put("rho0", rho0)
                .put("tmax", tmax)
                .put("itr", itr)
                .put("batchNum", batchNum)
                .put("seed0", seed0);

        StringBuilder cStr = new StringBuilder();
        for (int i = 0; i < cList.length; i++) { if (i > 0) cStr.append(":"); cStr.append(String.format(Locale.US, "%.6f", cList[i])); }
        StringBuilder lStr = new StringBuilder();
        for (int i = 0; i < lambdaList.length; i++) { if (i > 0) lStr.append(":"); lStr.append(String.format(Locale.US, "%.6f", lambdaList[i])); }
        StringBuilder pStr = new StringBuilder();
        for (int i = 0; i < pList.length; i++) { if (i > 0) pStr.append(":"); pStr.append(String.format(Locale.US, "%.8f", pList[i])); }
        params.put("cList", cStr.toString());
        params.put("lambdaList", lStr.toString());
        params.put("pList", pStr.toString());

        Writer.writeParametersToCSV(outDir + "/params.csv", params);

        LocalDateTime startTime = LocalDateTime.now();

        long runsPerBatch = (long) pList.length * cList.length * lambdaList.length * itr;
        long totalRuns = runsPerBatch * batchNum;
        System.out.printf(Locale.US,
                "Start VariousNetworkApp: batches=%d, runsPerBatch=%d, totalRuns=%d\n",
                batchNum, runsPerBatch, totalRuns);

        // 並列にバッチを回す。各バッチで infected_num_%02d.txt を出力（App.java と同様に最終値のみ1行ずつ）。
        IntStream.range(0, batchNum).parallel().forEach(batchIdx -> {
            String infectedFile = outDir + String.format("/infected_num_%02d.txt", batchIdx);
            VariousNetworkAppUtil.ensureParentDir(infectedFile);
            try (BufferedWriter iw = new BufferedWriter(new FileWriter(infectedFile, false))) {
                long runIndex = 0;
                for (int pi = 0; pi < pList.length; pi++) {
                    double p = pList[pi];

                    // p ごとにネットワークを生成（バッチごとに異なるシード）
                    long netSeed = seed0 + 1_000_003L * pi + 1_000_000_007L * batchIdx;
                    Network net = ModularRR.generate(modules, N, k_ave, p, netSeed);

                    for (int ci = 0; ci < cList.length; ci++) {
                        double c = cList[ci];
                        for (int li = 0; li < lambdaList.length; li++) {
                            double lambda = lambdaList[li];
                            for (int it = 0; it < itr; it++) {
                                long runSeed = seed0 + it + 10_007L * li + 1_000_003L * ci + 31L * pi + 97L * batchIdx;

                                SIS.RunResult res = SIS.simulateOnce(net, lambda, gamma, rho0, tmax, c, runSeed);
                                int lastI = res.infectedSeries()[res.infectedSeries().length - 1];

                                // 出力（最終感染者数のみ）
                                iw.write(Integer.toString(lastI));
                                iw.newLine();

                                runIndex++;
                                // synchronized (System.out) {
                                //     System.out.printf(Locale.US,
                                //             "[batch=%d %6d/%6d] p=%.6f c=%.3f lambda=%.3f itr=%d -> T_end=%.6f I_end=%d\n",
                                //             batchIdx, runIndex, runsPerBatch, p, c, lambda, it, lastT, lastI);
                                // }
                            }
                        }
                    }
                }
                synchronized (System.out) {
                    System.out.printf("Batch %d completed (%d runs).\n", batchIdx, runIndex);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        System.out.println("Completed VariousNetworkApp.");

        LocalDateTime endTime = LocalDateTime.now();
        Writer.writeMetadataToCSV(outDir + "/metadata.csv", startTime, endTime);
    }
}

// App.java に合わせた簡易ユーティリティ
class VariousNetworkAppUtil {
    static void ensureParentDir(String filename) {
        java.io.File f = new java.io.File(filename);
        java.io.File p = f.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
    }
}
