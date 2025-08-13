package com.example;

import com.example.simulation.SIS01;  
import com.example.utils.Array;
import com.example.utils.Writer;
import com.example.utils.Params;
import java.time.LocalDateTime;
import java.lang.Runtime;
import java.util.stream.IntStream;

public class SISApp {
    public static void main( String[] args ) {
        // ======== シミュレーションパラメータ ========
        String networkType = "ER";
        int N = 4000;
        int k_ave = 10;
        double lambdaMin = 0.0;
        double lambdaMax = 0.2;
        double dlambda = 0.005;
        double gamma = 1.0;
        double rho0 = (double)1 / N;
        double cMin = 0.0;
        double cMax = 1.0;
        double dc = 0.01;
        int tmax = 50;
        int batchNum = (int)(Runtime.getRuntime().availableProcessors() * 0.75);
        int itrPerBatch = 10;

        double[] lambdaList = Array.arange(lambdaMin, lambdaMax, dlambda);
        double[] cList = Array.arange(cMin, cMax, dc);

        int lambdaLength = lambdaList.length;
        int cLength = cList.length;
        // 5次元配列: [stateId][lambdaIdx][rho0Idx][itrIdx][timeIdx]
        // stateId: 0=S, 1=A, 2=R

        LocalDateTime startTime = LocalDateTime.now();

        // パラメータをParamsオブジェクトに設定
        Params params = new Params()
            .put("networkType", networkType)
            .put("N", N)
            .put("k_ave", k_ave)
            .put("lambdaMin", lambdaMin)
            .put("lambdaMax", lambdaMax)
            .put("dlambda", dlambda)
            .put("gamma", gamma)
            .put("rho0", rho0)
            .put("cMin", cMin)
            .put("cMax", cMax)
            .put("dc", dc)
            .put("tmax", tmax)
            .put("batchNum", batchNum)
            .put("itrPerBatch", itrPerBatch);

        // パラメータをCSVに保存
        String outputPath = "output/sis01/" + networkType + "/";
        Writer.writeParametersToCSV(outputPath + "parameters.csv", params);

        IntStream.range(0, batchNum).parallel().forEach(batchIdx -> {
            int[][][][] results = new int[lambdaLength][cLength][itrPerBatch][tmax + 1];
            for (int lambdaIdx = 0; lambdaIdx < lambdaLength; lambdaIdx++) {
                if (lambdaIdx % 10 == 0) {
                    synchronized (System.out) {
                        System.out.println(String.format("  Batch %02d: lambda %02d/%02d ...", batchIdx, lambdaIdx, lambdaLength));
                    }
                }
                double lambda = lambdaList[lambdaIdx];
                for (int cIdx = 0; cIdx < cLength; cIdx++) {
                    double c = cList[cIdx];
                    for (int itrIdx = 0; itrIdx < itrPerBatch; itrIdx++) {
                        int[][] result = SIS01.simulateToTmax(networkType, N, k_ave, lambda, gamma, rho0, tmax, c);
                        results[lambdaIdx][cIdx][itrIdx] = result[0]; // I
                    }
                }
            }

            Writer.writeOneStateResultsToCSV(outputPath + "results_" + batchIdx + ".csv", results, lambdaList, cList, itrPerBatch, tmax);
            System.out.println(String.format("Completed batch %02d", batchIdx));
        });

        LocalDateTime endTime = LocalDateTime.now();

        // メタデータをCSVに保存
        Writer.writeMetadataToCSV(outputPath + "metadata.csv", startTime, endTime);
    }
}
