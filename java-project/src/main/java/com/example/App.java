package com.example;

import com.example.network.Network;
import com.example.network.topology.BA;
import com.example.network.topology.ER;
import com.example.network.topology.RR;
import com.example.simulation.SAR01;
import com.example.utils.Array;
import com.example.utils.Writer;
import java.time.LocalDateTime;
import java.lang.Runtime;
import java.util.stream.IntStream;

public class App {
    public static void main( String[] args ) {
        // ======== シミュレーションパラメータ ========
        String networkType = "ER";
        int N = 4000;
        int k_ave = 10;
        double lambdaMin = 0.0;  // 最小感染率を上げる
        double lambdaMax = 1.0;
        double dlambda = 0.01;
        double gamma = 1.0;  // 回復率を下げる
        double rho0Min = 0.0;  // 最小初期感染率を上げる
        double rho0Max = 1.0;
        double drho0 = 0.01;
        int T = 3;
        int tmax = 50;
        int batchNum = (int)(Runtime.getRuntime().availableProcessors() * 0.75);
        int itrPerBatch = 10;

        double[] lambdaList = Array.arange(lambdaMin, lambdaMax, dlambda);
        double[] rho0List = Array.arange(rho0Min, rho0Max, drho0);

        int lambdaLength = lambdaList.length;
        int rho0Length = rho0List.length;
        // 5次元配列: [stateId][lambdaIdx][rho0Idx][itrIdx][timeIdx]
        // stateId: 0=S, 1=A, 2=R

        LocalDateTime startTime = LocalDateTime.now();

        // パラメータをCSVに保存
        Writer.writeParametersToCSV("output/parameters.csv", networkType,N, k_ave, lambdaMin, lambdaMax, dlambda, gamma, rho0Min, rho0Max, drho0, T, tmax, batchNum, itrPerBatch);

        IntStream.range(0, batchNum).parallel().forEach(batchIdx -> {
            int[][][][][] results = new int[3][lambdaLength][rho0Length][itrPerBatch][tmax + 1];

            for (int lambdaIdx = 0; lambdaIdx < lambdaLength; lambdaIdx++) {
                if (lambdaIdx % 10 == 0) {
                    synchronized (System.out) {
                        System.out.println(String.format("  Batch %02d: lambda %02d/%02d ...", batchIdx, lambdaIdx, lambdaLength));
                    }
                }
                double lambda = lambdaList[lambdaIdx];
                for (int rho0Idx = 0; rho0Idx < rho0Length; rho0Idx++) {
                    double rho0 = rho0List[rho0Idx];
                    for (int itrIdx = 0; itrIdx < itrPerBatch; itrIdx++) {
                        int[][] result = SAR01.simulateToTmax(networkType, N, k_ave, lambda, gamma, rho0, tmax, T);
                        // S, A, Rの3つの状態を保存
                        results[0][lambdaIdx][rho0Idx][itrIdx] = result[0]; // S
                        results[1][lambdaIdx][rho0Idx][itrIdx] = result[1]; // A
                        results[2][lambdaIdx][rho0Idx][itrIdx] = result[2]; // R
                    }
                }
            }

            Writer.writeResultsToCSV("output/results_" + batchIdx + ".csv", results, lambdaList, rho0List, itrPerBatch, tmax);
            System.out.println(String.format("Completed batch %02d", batchIdx));
        });

        LocalDateTime endTime = LocalDateTime.now();

        // メタデータをCSVに保存
        Writer.writeMetadataToCSV("output/metadata.csv", startTime, endTime);
    }
}
