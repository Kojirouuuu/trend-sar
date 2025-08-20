package com.example.simulation;

import com.example.network.Network;
import java.util.Random;
import com.example.utils.Array;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

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

    public static int[] simulateContinuous(Network network, double lambda, double gamma, double rho0, int tmax, double dt, double c) {
        // 返り値：時間間隔dtでの感染者数の時系列
        int numTimeSteps = (int)(tmax / dt) + 1;
        int[] I = new int[numTimeSteps];

        Random random = new Random();

        int[] state = new int[network.N];
        
        // 初期感染者を設定
        int initialInfectedNum = (int)(network.N * rho0);
        
        if (rho0 == 0.0) {
            initialInfectedNum = 0;
        } else if (initialInfectedNum == 0 && rho0 > 0) {
            initialInfectedNum = 1;
        }

        // 0からnetwork.N-1までのインデックスを生成してシャッフル
        int[] nodeIndices = new int[network.N];
        for (int i = 0; i < network.N; i++) {
            nodeIndices[i] = i;
        }
        int[] shuffledNodeList = Array.shuffle(nodeIndices);
        
        // 初期感染者を設定
        for (int i = 0; i < initialInfectedNum; i++) {
            state[shuffledNodeList[i]] = 1;
        }

        I[0] = initialInfectedNum;

        // 連続時間シミュレーション（Gillespieアルゴリズム）
        double currentTime = 0.0;
        int currentInfected = initialInfectedNum;
        int timeStepIndex = 1;
        
        while (currentTime < tmax && currentInfected > 0) {
            // 現在の状態での全イベント率を計算
            double totalRate = calculateTotalRate(network, state, lambda, gamma, c);
            
            if (totalRate <= 0) {
                break; // イベントが発生しない場合
            }
            
            // 次のイベントまでの時間を指数分布で生成
            double timeToNextEvent = -Math.log(random.nextDouble()) / totalRate;
            currentTime += timeToNextEvent;
            
            // 時間ステップdtごとの値を記録
            while (timeStepIndex < numTimeSteps && (timeStepIndex * dt) <= currentTime) {
                I[timeStepIndex] = currentInfected;
                timeStepIndex++;
            }
            
            // イベントの種類を選択（感染 or 回復）
            double eventType = random.nextDouble() * totalRate;
            double cumulativeRate = 0.0;
            
            // 感染イベントを処理
            for (int i = 0; i < network.N; i++) {
                if (state[i] == 0) { // 未感染ノード
                    int numInfectedNeighbors = countInfectedNeighbors(network, state, i);
                    if (numInfectedNeighbors > 0) {
                        cumulativeRate += lambda * numInfectedNeighbors;
                        if (eventType <= cumulativeRate) {
                            state[i] = 1; // 感染
                            currentInfected++;
                            break;
                        }
                    }
                }
            }
            
            // 回復イベントを処理
            if (eventType > cumulativeRate) {
                for (int i = 0; i < network.N; i++) {
                    if (state[i] == 1) { // 感染ノード
                        int numInfectedNeighbors = countInfectedNeighbors(network, state, i);
                        cumulativeRate += gamma / (c * numInfectedNeighbors + 1);
                        if (eventType <= cumulativeRate) {
                            state[i] = 0; // 回復
                            currentInfected--;
                            break;
                        }
                    }
                }
            }
        }
        
        // 残りの時間ステップを現在の値で埋める
        while (timeStepIndex < numTimeSteps) {
            I[timeStepIndex] = currentInfected;
            timeStepIndex++;
        }

        return I;
    }
    
    /**
     * イベント発生時刻も記録する連続時間シミュレーション
     * @return Object[] {感染者数時系列, イベント時刻配列, イベント種類配列, イベント対象ノード配列}
     */
    public static Object[] simulateContinuousWithEvents(Network network, double lambda, double gamma, double rho0, int tmax, double dt, double c) {
        // 返り値：時間間隔dtでの感染者数の時系列
        int numTimeSteps = (int)(tmax / dt) + 1;
        int[] I = new int[numTimeSteps];

        Random random = new Random();

        int[] state = new int[network.N];
        
        // イベント履歴を保存するリスト
        ArrayList<Double> eventTimes = new ArrayList<>();
        ArrayList<String> eventTypes = new ArrayList<>();
        ArrayList<Integer> eventNodes = new ArrayList<>();
        
        // 初期感染者を設定
        int initialInfectedNum = (int)(network.N * rho0);
        
        if (rho0 == 0.0) {
            initialInfectedNum = 0;
        } else if (initialInfectedNum == 0 && rho0 > 0) {
            initialInfectedNum = 1;
        }

        // 0からnetwork.N-1までのインデックスを生成してシャッフル
        int[] nodeIndices = new int[network.N];
        for (int i = 0; i < network.N; i++) {
            nodeIndices[i] = i;
        }
        int[] shuffledNodeList = Array.shuffle(nodeIndices);
        
        // 初期感染者を設定
        for (int i = 0; i < initialInfectedNum; i++) {
            state[shuffledNodeList[i]] = 1;
            // 初期感染イベントを記録
            eventTimes.add(0.0);
            eventTypes.add("INITIAL_INFECTION");
            eventNodes.add(shuffledNodeList[i]);
        }

        I[0] = initialInfectedNum;

        // 連続時間シミュレーション（Gillespieアルゴリズム）
        double currentTime = 0.0;
        int currentInfected = initialInfectedNum;
        int timeStepIndex = 1;
        
        while (currentTime < tmax && currentInfected > 0) {
            // 現在の状態での全イベント率を計算
            double totalRate = calculateTotalRate(network, state, lambda, gamma, c);
            
            if (totalRate <= 0) {
                break; // イベントが発生しない場合
            }
            
            // 次のイベントまでの時間を指数分布で生成
            double timeToNextEvent = -Math.log(random.nextDouble()) / totalRate;
            currentTime += timeToNextEvent;
            
            // 時間ステップdtごとの値を記録
            while (timeStepIndex < numTimeSteps && (timeStepIndex * dt) <= currentTime) {
                I[timeStepIndex] = currentInfected;
                timeStepIndex++;
            }
            
            // イベントの種類を選択（感染 or 回復）
            double eventType = random.nextDouble() * totalRate;
            double cumulativeRate = 0.0;
            
            // 感染イベントを処理
            for (int i = 0; i < network.N; i++) {
                if (state[i] == 0) { // 未感染ノード
                    int numInfectedNeighbors = countInfectedNeighbors(network, state, i);
                    if (numInfectedNeighbors > 0) {
                        cumulativeRate += lambda * numInfectedNeighbors;
                        if (eventType <= cumulativeRate) {
                            state[i] = 1; // 感染
                            currentInfected++;
                            
                            // 感染イベントを記録
                            eventTimes.add(currentTime);
                            eventTypes.add("INFECTION");
                            eventNodes.add(i);
                            break;
                        }
                    }
                }
            }
            
            // 回復イベントを処理
            if (eventType > cumulativeRate) {
                for (int i = 0; i < network.N; i++) {
                    if (state[i] == 1) { // 感染ノード
                        int numInfectedNeighbors = countInfectedNeighbors(network, state, i);
                        cumulativeRate += gamma / (c * numInfectedNeighbors + 1);
                        if (eventType <= cumulativeRate) {
                            state[i] = 0; // 回復
                            currentInfected--;
                            
                            // 回復イベントを記録
                            eventTimes.add(currentTime);
                            eventTypes.add("RECOVERY");
                            eventNodes.add(i);
                            break;
                        }
                    }
                }
            }
        }
        
        // 残りの時間ステップを現在の値で埋める
        while (timeStepIndex < numTimeSteps) {
            I[timeStepIndex] = currentInfected;
            timeStepIndex++;
        }

        // イベント履歴を配列に変換
        double[] eventTimesArray = new double[eventTimes.size()];
        String[] eventTypesArray = new String[eventTypes.size()];
        int[] eventNodesArray = new int[eventNodes.size()];
        
        for (int i = 0; i < eventTimes.size(); i++) {
            eventTimesArray[i] = eventTimes.get(i);
            eventTypesArray[i] = eventTypes.get(i);
            eventNodesArray[i] = eventNodes.get(i);
        }

        return new Object[]{I, eventTimesArray, eventTypesArray, eventNodesArray};
    }
    
    /**
     * 指定されたノードの感染隣接ノード数をカウント
     */
    private static int countInfectedNeighbors(Network network, int[] state, int nodeIndex) {
        int count = 0;
        int[] neighbors = network.getNeighbors(nodeIndex);
        for (int neighbor : neighbors) {
            if (state[neighbor] == 1) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 全イベント率を計算
     */
    private static double calculateTotalRate(Network network, int[] state, double lambda, double gamma, double c) {
        double totalRate = 0.0;
        
        for (int i = 0; i < network.N; i++) {
            if (state[i] == 0) { // 未感染ノード
                int numInfectedNeighbors = countInfectedNeighbors(network, state, i);
                if (numInfectedNeighbors > 0) {
                    totalRate += lambda * numInfectedNeighbors;
                }
            } else if (state[i] == 1) { // 感染ノード
                int numInfectedNeighbors = countInfectedNeighbors(network, state, i);
                totalRate += gamma / (c * numInfectedNeighbors + 1);
            }
        }
        
        return totalRate;
    }
}
