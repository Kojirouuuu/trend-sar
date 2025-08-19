package com.example;

import com.example.simulation.SAR04;  
import com.example.utils.Array;
import com.example.utils.Writer;
import com.example.utils.Params;
import java.time.LocalDateTime;
import java.lang.Runtime;
import java.util.stream.IntStream;

public class App04 {
    public static void main( String[] args ) {
        // ======== シミュレーションパラメータ ========
        String networkType = "ER";
        int N = 4000;
        int k_ave = 6;
        double lambdaMin = 0.0;
        double lambdaMax = 0.2;
        double dlambda = 0.005;
        double gamma = 1.0;
        double rho0 = (double)1 / N;
        double cMin = 0.0;
        double cMax = 20.0;
        double dc = 0.5;
        int T = 1;
        int batchNum = (int)(Runtime.getRuntime().availableProcessors());
        int itrPerBatch = 10;

        double[] lambdaList = Array.arange(lambdaMin, lambdaMax, dlambda);
        double[] cList = Array.arange(cMin, cMax, dc);

        int lambdaLength = lambdaList.length;
        int cLength = cList.length;
        // 4次元配列: [stateId][lambdaIdx][cIdx][itrIdx]
        // stateId: 0=Adopted, 1=Recovered

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
            .put("T", T)
            .put("batchNum", batchNum)
            .put("itrPerBatch", itrPerBatch);

        // パラメータをCSVに保存
        String outputPath = "output/sar04/" + networkType + "/";
        Writer.writeParametersToCSV(outputPath + "parameters.csv", params);

        IntStream.range(0, batchNum).parallel().forEach(batchIdx -> {
            int[][][][] results = new int[2][lambdaLength][cLength][itrPerBatch];

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
                        int[] result = SAR04.simulateToFinalTime(networkType, N, k_ave, lambda, gamma, c, rho0, T);
                        results[0][lambdaIdx][cIdx][itrIdx] = result[0]; // Adopted
                        results[1][lambdaIdx][cIdx][itrIdx] = result[1]; // Recovered
                    }
                }
            }

            Writer.writeFinalResultsToCSV(outputPath + "results_" + batchIdx + ".csv", results, lambdaList, cList, itrPerBatch);
            System.out.println(String.format("Completed batch %02d", batchIdx));
        });

        LocalDateTime endTime = LocalDateTime.now();

        // メタデータをCSVに保存
        Writer.writeMetadataToCSV(outputPath + "metadata.csv", startTime, endTime);
    }
}
