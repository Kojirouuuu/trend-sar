package com.example;

import com.example.simulation.ContinuousSIS;
import com.example.simulation.ContinuousRunResult;
import com.example.simulation.SISEvent;
import com.example.network.Network;
import com.example.utils.Array;
import com.example.utils.ContinuoutWriter;
import com.example.utils.Params;
import java.time.LocalDateTime;
import java.util.stream.IntStream;

public class ContinuousSISApp {
    public static void main(String[] args) {
        // ======== シミュレーションパラメータ ========
        String networkType = "RR";
        int N = 10000;
        int k_ave = 6;
        double lambdaMin = 0.0, lambdaMax = 2.0, dlambda = 0.01;
        double gamma = 1.0;
        double rho01 = (double)1 / N;
        double c0 = 0.0, c1 = 0.3, c2 = 0.8, c3 = 1.4;
        // double c0 = 0.0, c1 = 1.0;
        int tmax = 40;
        int batchNum = 20;
        int itrPerBatch = 5;
        double dt = 1;

        double[] lambdaList = Array.arange(lambdaMin, lambdaMax, dlambda);
        double[] rho0List = {rho01};
        double[] cList = {c0, c1, c2, c3};
        // double[] cList = {c0, c1};

        int lambdaLength = lambdaList.length;
        int rho0Length = rho0List.length;
        int cLength = cList.length;

        final int numTimeSteps = (int)(tmax / dt) + 1; // ★ここが重要

        LocalDateTime startTime = LocalDateTime.now();

        Params params = new Params()
            .put("networkType", networkType)
            .put("N", N)
            .put("k_ave", k_ave)
            .put("lambdaMin", lambdaMin)
            .put("lambdaMax", lambdaMax)
            .put("dlambda", dlambda)
            .put("gamma", gamma)
            .put("rho01", rho01)
            .put("c0", c0).put("c1", c1).put("c2", c2).put("c3", c3)
            // .put("c0", c0).put("c1", c1)
            .put("tmax", tmax)
            .put("dt", dt) // ★ 追加：結果の解像度
            .put("batchNum", batchNum)
            .put("itrPerBatch", itrPerBatch);

        String outputPath = "output/sis01/" + networkType + "/" + "single_rho0/";
        ContinuoutWriter.writeParametersToCSV(outputPath + "parameters.csv", params);

        Network network = Network.generateNetwork(networkType, N, k_ave);

        IntStream.range(0, batchNum).parallel().forEach(batchIdx -> {
            // I(t) を入れる5次元配列
            int[][][][][] results = new int[cLength][lambdaLength][rho0Length][itrPerBatch][numTimeSteps];
            // イベント履歴（最後の次元はイベント配列）
            SISEvent[][][][][] eventHistories = new SISEvent[cLength][lambdaLength][rho0Length][itrPerBatch][];

            for (int cIdx = 0; cIdx < cLength; cIdx++) {
                logf("Batch %02d: c %02d/%02d ...", batchIdx + 1, cIdx + 1, cLength);
                double c = cList[cIdx];

                for (int lambdaIdx = 0; lambdaIdx < lambdaLength; lambdaIdx++) {
                    boolean isPrintlmabda = lambdaIdx % 10 == 0;
                    double lambda = lambdaList[lambdaIdx];

                    for (int rho0Idx = 0; rho0Idx < rho0Length; rho0Idx++) {
                        boolean isPrintRho0 = rho0Idx % 10 == 0;
                        if (isPrintRho0 && isPrintlmabda) logf("  - Batch %02d: c %02d/%02d, lamb %02d/%02d, rho0 %02d/%02d ...", batchIdx + 1, cIdx + 1, cLength, lambdaIdx + 1, lambdaLength, rho0Idx + 1, rho0Length);
                        double rho0 = rho0List[rho0Idx];

                        for (int itrIdx = 0; itrIdx < itrPerBatch; itrIdx++) {
                            ContinuousRunResult sim = ContinuousSIS.simulateContinuousWithEvents(
                                network, lambda, gamma, rho0, tmax, dt, c);

                            results[cIdx][lambdaIdx][rho0Idx][itrIdx] = sim.infectedTimeSeries();
                            eventHistories[cIdx][lambdaIdx][rho0Idx][itrIdx] = sim.events();
                        }
                    }
                }
            }

            ContinuoutWriter.write3ArgsOneStateResultsToCSV(
                outputPath + "results_" + batchIdx + ".csv",
                results, cList, lambdaList, rho0List, itrPerBatch, tmax, dt
            );

            ContinuoutWriter.writeEventHistoriesToCSV(
                outputPath + "events_" + batchIdx + ".csv",
                eventHistories, cList, lambdaList, rho0List, itrPerBatch
            );

            logf("Completed batch %02d", batchIdx);
        });

        LocalDateTime endTime = LocalDateTime.now();
        ContinuoutWriter.writeMetadataToCSV(outputPath + "metadata.csv", startTime, endTime);
    }

    private static void logf(String fmt, Object... args) {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        synchronized (System.out) {
            System.out.println("[%s] %s".formatted(ts, String.format(fmt, args)));
        }
    }
}
