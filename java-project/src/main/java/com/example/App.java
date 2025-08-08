package com.example;

import com.example.network.Network;
import com.example.network.topology.BA;
import com.example.network.topology.ER;
import com.example.network.topology.RR;
import com.example.simulation.SAR;
import com.example.utils.Array;
import com.example.utils.Writer;
import java.time.LocalDateTime;

public class App {
    public static void main( String[] args ) {
        // ======== シミュレーションパラメータ ========
        String networkType = "ER";
        int N = 10000;
        int k_ave = 10;
        double lambdaMin = 0.0;  // 最小感染率を上げる
        double lambdaMax = 1.0;
        double dlambda = 0.025;
        double gamma = 1.0;  // 回復率を下げる
        double rho0Min = 0.0;  // 最小初期感染率を上げる
        double rho0Max = 1.0;
        double drho0 = 0.025;
        int T = 3;
        int tmax = 50;
        int batchNum = 10;
        int itrPerBatch = 20;

        Network network = ER.generateER(N, k_ave / (N - 1));

        int[] thresholdList = new int[network.N];
        for (int i = 0; i < network.N; i++) {
            thresholdList[i] = T;
        }

        double[] lambdaList = Array.arange(lambdaMin, lambdaMax, dlambda);
        double[] rho0List = Array.arange(rho0Min, rho0Max, drho0);

        int lambdaLength = lambdaList.length;
        int rho0Length = rho0List.length;
        // 4次元配列: [stateId][lambdaIdx][rho0Idx][time]
        // stateId: 0=S, 1=I, 2=R

        LocalDateTime startTime = LocalDateTime.now();

        for (int batchIdx = 0; batchIdx < batchNum; batchIdx++) {
            int[][][][][] results = new int[3][lambdaLength][rho0Length][itrPerBatch][tmax + 1];

            for (int itrIdx = 0; itrIdx < itrPerBatch; itrIdx++) {
                for (int lambdaIdx = 0; lambdaIdx < lambdaLength; lambdaIdx++) {
                    double lambda = lambdaList[lambdaIdx];
                    for (int rho0Idx = 0; rho0Idx < rho0Length; rho0Idx++) {
                        double rho0 = rho0List[rho0Idx];
                        int[][] result = SAR.simulateToTmax(network, lambda, gamma, rho0, tmax, thresholdList);
                        // S, I, Rの3つの状態を保存
                        results[0][lambdaIdx][rho0Idx][itrIdx] = result[0]; // S
                        results[1][lambdaIdx][rho0Idx][itrIdx] = result[1]; // I
                        results[2][lambdaIdx][rho0Idx][itrIdx] = result[2]; // R
                    }
                }
            }

            Writer.writeResultsToCSV("output/results_" + batchIdx + ".csv", results, lambdaList, rho0List, itrPerBatch, tmax, "lambda", "rho0");
        }

        LocalDateTime endTime = LocalDateTime.now();
        Writer.writeParametersToCSV("output/parameters.csv", networkType,N, k_ave, lambdaMin, lambdaMax, dlambda, gamma, rho0Min, rho0Max, drho0, T, tmax, batchNum, itrPerBatch);
        Writer.writeMetadataToCSV("output/metadata.csv", startTime, endTime);
    }
}
