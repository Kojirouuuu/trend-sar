package com.example;

import com.example.simulation.SIS01;
import com.example.utils.Array;
import com.example.utils.Writer;
import com.example.utils.Params;
import java.time.LocalDateTime;
import java.util.stream.IntStream;

public class SISApp {
    public static void main( String[] args ) {
        // ======== シミュレーションパラメータ ========
        String networkType = "ER";
        int N = 10000;
        int k_ave = 10;
        double lambdaMin = 0.07;
        double lambdaMax = 0.10;
        double dlambda = 0.001;
        double gamma = 1.0;
        double rho0Min = 0.0;
        double rho0Max = 0.3;
        double drho0 = 0.01;
        double c0 = 0.0;
        double c1 = 0.05;
        double c2 = 0.6;
        double[] cList = {c0, c1, c2};
        int tmax = 200;
        int batchNum = 20;
        int itrPerBatch = 10;

        double[] lambdaList = Array.arange(lambdaMin, lambdaMax, dlambda);
        double[] rho0List = Array.arange(rho0Min, rho0Max, drho0);

        int lambdaLength = lambdaList.length;
        int rho0Length = rho0List.length;
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
            .put("rho0Min", rho0Min)
            .put("rho0Max", rho0Max)
            .put("drho0", drho0)
            .put("c0", c0)
            .put("c1", c1)
            .put("c2", c2)
            .put("tmax", tmax)
            .put("batchNum", batchNum)
            .put("itrPerBatch", itrPerBatch);

        // パラメータをCSVに保存
        String outputPath = "output/sis01/" + networkType + "/";
        Writer.writeParametersToCSV(outputPath + "parameters.csv", params);

        IntStream.range(0, batchNum).parallel().forEach(batchIdx -> {
            int[][][][][] results = new int[cLength][lambdaLength][rho0Length][itrPerBatch][tmax + 1];
            for (int cIdx = 0; cIdx < cLength; cIdx++) {
                LocalDateTime currentTime = LocalDateTime.now();
                System.out.println(String.format("[%s] Batch %02d: c %02d/%02d ...", 
                    currentTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 
                    batchIdx, cIdx, cLength));
                double c = cList[cIdx];
                for (int lambdaIdx = 0; lambdaIdx < lambdaLength; lambdaIdx++) {
                    boolean isLambdaPrint = lambdaIdx % 10 == 0;
                    if (isLambdaPrint) {
                        synchronized (System.out) {
                            LocalDateTime lambdaTime = LocalDateTime.now();
                            System.out.println(String.format("[%s]   - Batch %02d: lamb %02d/%02d ...", 
                                lambdaTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 
                                batchIdx, lambdaIdx, lambdaLength));
                        }
                    }
                    double lambda = lambdaList[lambdaIdx];

                    for (int rho0Idx = 0; rho0Idx < rho0Length; rho0Idx++) {
                        boolean isRho0Print = rho0Idx % 10 == 0;
                        if (isRho0Print) {
                            synchronized (System.out) {
                                LocalDateTime rho0Time = LocalDateTime.now();
                                System.out.println(String.format("[%s]     - Batch %02d: rho0 %02d/%02d ...", 
                                    rho0Time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 
                                    batchIdx, rho0Idx, rho0Length));
                            }
                        }
                        double rho0 = rho0List[rho0Idx];
                        for (int itrIdx = 0; itrIdx < itrPerBatch; itrIdx++) {
                            results[cIdx][lambdaIdx][rho0Idx][itrIdx] = SIS01.simulateToTmax(networkType, N, k_ave, lambda, gamma, rho0, tmax, c);  
                        }
                    }
                }
            }

            Writer.write3ArgsOneStateResultsToCSV(outputPath + "results_" + batchIdx + ".csv", results, cList, lambdaList, rho0List, itrPerBatch, tmax);
            LocalDateTime completionTime = LocalDateTime.now();
            System.out.println(String.format("[%s] Completed batch %02d", 
                completionTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 
                batchIdx));
        });

        LocalDateTime endTime = LocalDateTime.now();

        // メタデータをCSVに保存
        Writer.writeMetadataToCSV(outputPath + "metadata.csv", startTime, endTime);
    }
}
