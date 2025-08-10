package com.example;

import com.example.simulation.SAR02;  
import com.example.utils.Array;
import com.example.utils.Writer;
import com.example.utils.Params;
import java.time.LocalDateTime;
import java.lang.Runtime;
import java.util.stream.IntStream;

public class App02 {
    public static void main( String[] args ) {
        // ======== シミュレーションパラメータ ========
        String networkType = "ER";
        int N = 4000;
        int k_ave = 10;
        double lambdaMin = 0.0;
        double lambdaMax = 1.0;
        double dlambda = 0.01;
        double gamma = 0.8;
        double rho0 = 0.003;
        double alphaMin = 0.0;
        double alphaMax = 1.0;
        double dalpha = 0.01;
        int transmissionThreshold = 1;
        int trendThreshold = 2;
        int tmax = 100;
        int batchNum = (int)(Runtime.getRuntime().availableProcessors() * 0.75);
        int itrPerBatch = 20;

        double[] lambdaList = Array.arange(lambdaMin, lambdaMax, dlambda);
        double[] alphaList = Array.arange(alphaMin, alphaMax, dalpha);

        int lambdaLength = lambdaList.length;
        int alphaLength = alphaList.length;
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
            .put("alphaMin", alphaMin)
            .put("alphaMax", alphaMax)
            .put("dalpha", dalpha)
            .put("transmissionThreshold", transmissionThreshold)
            .put("trendThreshold", trendThreshold)
            .put("tmax", tmax)
            .put("batchNum", batchNum)
            .put("itrPerBatch", itrPerBatch);
        
        // パラメータをCSVに保存
        Writer.writeParametersToCSV("output/sar02/parameters.csv", params);

        IntStream.range(0, batchNum).parallel().forEach(batchIdx -> {
            int[][][][][] results = new int[3][lambdaLength][alphaLength][itrPerBatch][tmax + 1];

            for (int lambdaIdx = 0; lambdaIdx < lambdaLength; lambdaIdx++) {
                if (lambdaIdx % 10 == 0) {
                    synchronized (System.out) {
                        System.out.println(String.format("  Batch %02d: lambda %02d/%02d ...", batchIdx, lambdaIdx, lambdaLength));
                    }
                }
                double lambda = lambdaList[lambdaIdx];
                for (int alphaIdx = 0; alphaIdx < alphaLength; alphaIdx++) {
                    double alpha = alphaList[alphaIdx];
                    for (int itrIdx = 0; itrIdx < itrPerBatch; itrIdx++) {
                        int[][] result = SAR02.simulateToTmax(networkType, N, k_ave, lambda, gamma, alpha, rho0, tmax, transmissionThreshold, trendThreshold);
                        // S, A, Rの3つの状態を保存
                        results[0][lambdaIdx][alphaIdx][itrIdx] = result[0]; // S
                        results[1][lambdaIdx][alphaIdx][itrIdx] = result[1]; // A
                        results[2][lambdaIdx][alphaIdx][itrIdx] = result[2]; // R
                    }
                }
            }

            Writer.writeResultsToCSV("output/sar02/results_" + batchIdx + ".csv", results, lambdaList, alphaList, itrPerBatch, tmax);
            System.out.println(String.format("Completed batch %02d", batchIdx));
        });

        LocalDateTime endTime = LocalDateTime.now();

        // メタデータをCSVに保存
        Writer.writeMetadataToCSV("output/sar02/metadata.csv", startTime, endTime);
    }
}
