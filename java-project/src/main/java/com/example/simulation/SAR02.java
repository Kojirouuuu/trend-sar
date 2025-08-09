package com.example.simulation;

import com.example.network.Network;
import java.util.Random;
import com.example.utils.Array;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

/**
 * 一般的なSARモデルのシミュレーションを行うクラス
 * ノードの状態は0: 未感染, 1: 感染, 2: 回復
 * 全ての頂点は閾値Tを持ち、その頂点が口コミを受け取った数が閾値Tに達したとき、採用者となる
 */

public class SAR02 {

    public static int[][] simulateToTmax(String networkType, int N, int k_ave, double lambda, double gamma, double alpha, double rho0, int tmax, int transmissionThreshold, int trendThreshold) {
        // 返り値
        int[] S = new int[tmax + 1];
        int[] A = new int[tmax + 1];
        int[] R = new int[tmax + 1];

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
        
        int curAdoptedNum = 0;
        // 初期感染者を設定（0人の場合は何もしない）
        for (int i = 0; i < initialInfectedNum; i++) {
            state[shuffledNodeList[i]] = 1;
            curAdoptedNum++;
        }

        List<Set<Integer>> informedNeighbors = new ArrayList<>();
        for (int i = 0; i < network.N; i++) {
            informedNeighbors.add(new HashSet<>());
        }

        S[0] = network.N - initialInfectedNum;
        A[0] = initialInfectedNum;
        R[0] = 0;

        int[] transmissionThresholdList = new int[network.N];
        for (int i = 0; i < network.N; i++) {
            transmissionThresholdList[i] = transmissionThreshold;
        }

        int[] trendThresholdList = new int[network.N];
        for (int i = 0; i < network.N; i++) {
            trendThresholdList[i] = trendThreshold;
        }

        int[] numHadTrendEffect = new int[network.N];

        int timeStep = 0;
        while (timeStep < tmax) {
            if (curAdoptedNum == 0) {
                break;
            }

            Set<Integer> newAdopters = new HashSet<>();
            Set<Integer> newRecovered = new HashSet<>();

            for (int i = 0; i < network.N; i++) {
                if (state[i] == 1) {
                    int[] neighbors = network.getNeighbors(i);
                    for (int neighbor : neighbors) {
                        if (state[neighbor] == 0) {
                            if (random.nextDouble() < lambda) {
                                informedNeighbors.get(neighbor).add(i);
                                if (informedNeighbors.get(neighbor).size() >= transmissionThresholdList[i]) {
                                    newAdopters.add(neighbor);
                                }
                            }
                        }
                    }
                    if (random.nextDouble() < gamma) {
                        newRecovered.add(i);
                    }
                }
                else if (state[i] == 0) {
                    if (random.nextDouble() < alpha) {
                        numHadTrendEffect[i]++;
                        if (numHadTrendEffect[i] >= trendThresholdList[i]) {
                            state[i] = 1;
                            newAdopters.add(i);
                        }
                    }
                }
            }
            
            // 状態を更新
            for (int node : newAdopters) {
                state[node] = 1;
                curAdoptedNum++;
            }
            for (int node : newRecovered) {
                state[node] = 2;
                curAdoptedNum--;
            }

            // 次の時間ステップの値を計算
            S[timeStep + 1] = S[timeStep] - newAdopters.size();
            A[timeStep + 1] = A[timeStep] + newAdopters.size() - newRecovered.size();
            R[timeStep + 1] = R[timeStep] + newRecovered.size();
            
            timeStep++;
        }

        if (timeStep < tmax) {
            for (int afterStep = timeStep + 1; afterStep <= tmax; afterStep++) {
                S[afterStep] = S[afterStep - 1];
                A[afterStep] = A[afterStep - 1];
                R[afterStep] = R[afterStep - 1];
            }
        }

        return new int[][] {S, A, R};

    }
    
}
