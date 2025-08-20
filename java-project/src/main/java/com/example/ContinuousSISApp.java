package com.example;

import com.example.simulation.SIS01;
import com.example.network.Network;
import com.example.utils.Array;
import com.example.utils.Writer;
import com.example.utils.Params;
import java.time.LocalDateTime;
import java.util.stream.IntStream;

public class ContinuousSISApp {
    public static void main( String[] args ) {
        // ======== シミュレーションパラメータ ========
        String networkType = "RR";
        int N = 10000;
        int k_ave = 10;
        double lambdaMin = 0.0;
        double lambdaMax = 1.0;
        double dlambda = 0.01;
        double gamma = 1.0;
        double rho01 = (double) 1 / N;
        double c0 = 0.0;
        double c1 = 0.1;
        double c2 = 0.2;
        double c3 = 0.6;
        int tmax = 200;
        int batchNum = 40;
        int itrPerBatch = 5;
        double dt = 0.1;
        double[] lambdaList = Array.arange(lambdaMin, lambdaMax, dlambda);
        double[] rho0List = {rho01};
        double[] cList = {c0, c1, c2, c3};

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
            .put("rho01", rho01)
            .put("c0", c0)
            .put("c1", c1)
            .put("c2", c2)
            .put("c3", c3)
            .put("tmax", tmax)
            .put("batchNum", batchNum)
            .put("itrPerBatch", itrPerBatch);

        // パラメータをCSVに保存
        String outputPath = "output/sis01/" + networkType + "/" + "single_rho0/";
        Writer.writeParametersToCSV(outputPath + "parameters.csv", params);

        Network network = Network.generateNetwork(networkType, N, k_ave);

        IntStream.range(0, batchNum).parallel().forEach(batchIdx -> {
            // 5次元配列: [cIdx][lambdaIdx][rho0Idx][itrIdx][timeIdx] - 感染者数時系列
            int[][][][][] results = new int[cLength][lambdaLength][rho0Length][itrPerBatch][tmax + 1];
            // イベント履歴を保存する配列: [cIdx][lambdaIdx][rho0Idx][itrIdx][eventIdx]
            Object[][][][][] eventHistories = new Object[cLength][lambdaLength][rho0Length][itrPerBatch][];
            
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
                            // 連続時間シミュレーションを実行（イベント履歴付き）
                            Object[] simulationResult = SIS01.simulateContinuousWithEvents(network, lambda, gamma, rho0, tmax, dt, c);
                            
                            // 感染者数時系列を取得
                            int[] infectedTimeSeries = (int[]) simulationResult[0];
                            results[cIdx][lambdaIdx][rho0Idx][itrIdx] = infectedTimeSeries;
                            
                            // イベント履歴を保存
                            eventHistories[cIdx][lambdaIdx][rho0Idx][itrIdx] = simulationResult;
                        }
                    }
                }
            }

            // 感染者数時系列の結果をCSVに保存
            Writer.write3ArgsOneStateResultsToCSV(outputPath + "results_" + batchIdx + ".csv", results, cList, lambdaList, rho0List, itrPerBatch, tmax);
            
            // イベント履歴をCSVに保存
            Writer.writeEventHistoriesToCSV(outputPath + "events_" + batchIdx + ".csv", eventHistories, cList, lambdaList, rho0List, itrPerBatch);
            
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
