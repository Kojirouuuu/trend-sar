package com.example.simulation;

import com.example.network.Network;
import java.util.Random;
import com.example.utils.Array;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

/**
 * 流行効果付きSARモデルのシミュレーションを行うクラス
 * 各タイムステップで、採用者は、gamma / (1 + c * 採用者の数) の確率で回復する。
 */


public class SAR04 {
    
    public static int[] simulateToFinalTime(String networkType, int N, int k_ave, double lambda, double gamma, double c, double rho0, int T) {

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
        
        // 初期感染者を設定（0人の場合は何もしない
        HashSet<Integer> AdoptedNodes = new HashSet<>();
        for (int i = 0; i < initialInfectedNum; i++) {
            state[shuffledNodeList[i]] = 1;
            AdoptedNodes.add(shuffledNodeList[i]);
        }

        List<Set<Integer>> informedNeighbors = new ArrayList<>();
        for (int i = 0; i < network.N; i++) {
            informedNeighbors.add(new HashSet<>());
        }

        int[] thresholdList = new int[network.N];
        for (int i = 0; i < network.N; i++) {
            thresholdList[i] = T;
        }

        int curSusceptibleNum = network.N - initialInfectedNum;
        int curAdoptedNum = initialInfectedNum;
        int timeStep = 0;
        while (curAdoptedNum > 0 && curSusceptibleNum > 0 && timeStep < 10000) {
            Set<Integer> newAdopters = new HashSet<>();
            Set<Integer> newRecovered = new HashSet<>();

            for (int i : AdoptedNodes) {
                if (state[i] == 1) {
                    int[] neighbors = network.getNeighbors(i);
                    int adoptedNeighbors = 0;
                    for (int neighbor : neighbors) {
                        if (state[neighbor] == 1) {
                            adoptedNeighbors++;
                        }
                        else if (state[neighbor] == 0) {
                            if (random.nextDouble() < lambda) {
                                informedNeighbors.get(neighbor).add(i);
                                if (informedNeighbors.get(neighbor).size() >= thresholdList[neighbor]) {
                                    newAdopters.add(neighbor);
                                }
                            }
                        }
                    }
                    if (random.nextDouble() < gamma / (1 + c * adoptedNeighbors)) {
                        newRecovered.add(i);
                    }
                }
            }
            
            // 状態を更新
            for (int node : newAdopters) {
                state[node] = 1;
                AdoptedNodes.add(Integer.valueOf(node));
            }
            for (int node : newRecovered) {
                state[node] = 2;
                AdoptedNodes.remove(Integer.valueOf(node));
            }

            // 次の時間ステップの値を計算
            curSusceptibleNum -= newAdopters.size();
            curAdoptedNum += newAdopters.size() - newRecovered.size();
            timeStep++;
        }

        int A = 0;
        int R = 0;
        for (int i = 0; i < network.N; i++) {
            if (state[i] == 1) {
                A++;
            }
            else if (state[i] == 2) {
                R++;
            }
        }

        return new int[] {A, R};

    }
    
}
