package com.example.simulation;

import com.example.network.Network;
import java.util.Random;
import com.example.utils.Array;
import java.util.Set;
import java.util.HashSet;

/**
 * 一般的なSISモデルのシミュレーションを行うクラス
 * ノードの状態は0: 未感染, 1: 感染
 */

public class SIS01 {

    public static int[] simulateToTmax(String networkType, int N, int k_ave, double lambda, double gamma, double rho0, int tmax, double c) {
        // 返り値
        int[] I = new int[tmax + 1];

        Random random = new Random();

        Network network = Network.generateNetwork(networkType, N, k_ave);

        int[] state = new int[network.N];
        int initialInfectedNum = (int)(network.N * rho0);
        
        // rho0 = 0.0の場合は初期感染者0人
        if (rho0 == 0.0) {
            initialInfectedNum = 0;
        } else if (initialInfectedNum == 0) {
            // rho0 > 0だが計算結果が0の場合は最低1人
            initialInfectedNum = 1;
        }

        // 0からnetwork.N-1までのインデックスを生成
        int[] nodeIndices = new int[network.N];
        for (int i = 0; i < network.N; i++) {
            nodeIndices[i] = i;
        }
        int[] shuffledNodeList = Array.shuffle(nodeIndices);
        
        // 初期感染者を設定（0人の場合は何もしない）
        for (int i = 0; i < initialInfectedNum; i++) {
            state[shuffledNodeList[i]] = 1;
        }

        I[0] = initialInfectedNum;

        int timeStep = 0;
        while (timeStep < tmax) {
            int curAdoptedNum = I[timeStep];
            if (curAdoptedNum == 0) {
                break;
            }

            Set<Integer> newAdopters = new HashSet<>();
            Set<Integer> newSusceptible = new HashSet<>();

            for (int i = 0; i < network.N; i++) {
                int numInfectedNeighbors = 0;
                if (state[i] == 1) {
                    int[] neighbors = network.getNeighbors(i);
                    for (int neighbor : neighbors) {
                        if (state[neighbor] == 0) {
                            if (random.nextDouble() < lambda) {
                                newAdopters.add(neighbor);
                            }
                        }
                        else if (state[neighbor] == 1) {
                            numInfectedNeighbors++;
                        }
                    }
                    if (random.nextDouble() < (double)gamma / (c * numInfectedNeighbors + 1) ) {
                        newSusceptible.add(i);
                    }
                }
            }
            
            // 状態を更新
            for (int node : newAdopters) {
                state[node] = 1;
            }
            for (int node : newSusceptible) {
                state[node] = 0;
            }
            // 次の時間ステップの値を計算
            I[timeStep + 1] = I[timeStep] + newAdopters.size() - newSusceptible.size();
            
            timeStep++;
        }

        if (timeStep < tmax) {
            for (int afterStep = timeStep + 1; afterStep <= tmax; afterStep++) {
                I[afterStep] = I[afterStep - 1];
            }
        }

        return I;

    }
    
}
