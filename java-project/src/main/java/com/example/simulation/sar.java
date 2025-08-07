package com.example.simulation;

import com.example.network.Network;
import java.util.Random;
import com.example.utils.Array;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

public class sar {

    public static int[][] simulateToTmax(Network network, double lambda, double gamma, double rho0, int tmax, int[] thresholdList) {
        // 返り値
        int[] S = new int[tmax + 1];
        int[] I = new int[tmax + 1];
        int[] R = new int[tmax + 1];

        Random random = new Random();
        int[] state = new int[network.N];
        int initialInfectedNum = (int)(Math.max(network.N * rho0, 1));

        // 0からnetwork.N-1までのインデックスを生成
        int[] nodeIndices = new int[network.N];
        for (int i = 0; i < network.N; i++) {
            nodeIndices[i] = i;
        }
        int[] shuffledNodeList = Array.shuffle(nodeIndices);
        
        int curAdoptedNum = initialInfectedNum;
        for (int i = 0; i < initialInfectedNum; i++) {
            state[shuffledNodeList[i]] = 1;
        }

        List<Set<Integer>> informedNeighbors = new ArrayList<>();
        for (int i = 0; i < network.N; i++) {
            informedNeighbors.add(new HashSet<>());
        }

        S[0] = network.N - initialInfectedNum;
        I[0] = initialInfectedNum;
        R[0] = 0;
        
        // デバッグ用: 初期状態を出力
        // System.out.println("Initial state - S: " + S[0] + ", I: " + I[0] + ", R: " + R[0]);
        // System.out.println("Parameters - lambda: " + lambda + ", gamma: " + gamma + ", rho0: " + rho0);

        int timeStep;
        for (timeStep = 0; timeStep < tmax; timeStep++) {
            if (curAdoptedNum == 0) {
                break;
            }

            Set<Integer> newAdopters = new HashSet<>();
            Set<Integer> newRecovered = new HashSet<>();

            for (int i = 0; i < network.N; i++) {
                if (state[i] == 1) {
                    if (random.nextDouble() < gamma) {
                        newRecovered.add(i);
                    }
                    int[] neighbors = network.getNeighbors(i);
                    for (int neighbor : neighbors) {
                        if (state[neighbor] == 0 && random.nextDouble() < lambda) {
                            informedNeighbors.get(neighbor).add(i);
                            if (informedNeighbors.get(neighbor).size() >= thresholdList[neighbor]) {
                                newAdopters.add(neighbor);
                            }
                        }
                    }
                }
            }
            
            // 回復したノードの情報を削除
            for (int recoveredNode : newRecovered) {
                for (Set<Integer> neighborSet : informedNeighbors) {
                    neighborSet.remove(recoveredNode);
                }
            }
            for (int node : newAdopters) {
                state[node] = 1;
                curAdoptedNum++;
            }
            for (int node : newRecovered) {
                state[node] = 2;
                curAdoptedNum--;
            }

            S[timeStep + 1] = S[timeStep] - newAdopters.size();
            I[timeStep + 1] = I[timeStep] + newAdopters.size() - newRecovered.size();
            R[timeStep + 1] = R[timeStep] + newRecovered.size();
            
            // デバッグ用: 最初の数ステップを出力
            // if (timeStep < 5) {
            //     System.out.println("Step " + (timeStep + 1) + " - S: " + S[timeStep + 1] + ", I: " + I[timeStep + 1] + ", R: " + R[timeStep + 1]);
            //     System.out.println("New adopters: " + newAdopters.size() + ", New recovered: " + newRecovered.size());
            // }
        }

        if (timeStep < tmax) {
            for (int afterStep = timeStep + 1; afterStep <= tmax; afterStep++) {
                S[afterStep] = S[afterStep - 1];
                I[afterStep] = I[afterStep - 1];
                R[afterStep] = R[afterStep - 1];
            }
        }

        return new int[][] {S, I, R};

    }
    
}
