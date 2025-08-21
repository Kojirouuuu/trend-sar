package com.example.simulation;

import com.example.network.Network;
import java.util.ArrayList;
import java.util.Random;

public class ContinuousSIS {

    /**
     * 連続時間シミュレーション（イベント記録付き）
     * @return ContinuousRunResult (I(t), dt, Event[])
     */
    public static ContinuousRunResult simulateContinuousWithEvents(
            Network network, double lambda, double gamma, double rho0,
            int tmax, double dt, double c) {

        final int numTimeSteps = (int)(tmax / dt) + 1; // ★必ずこれ
        int[] I = new int[numTimeSteps];

        Random random = new Random();
        int[] state = new int[network.N];

        ArrayList<SISEvent> events = new ArrayList<>();

        // 初期感染者の設定
        int initialInfectedNum = (int)(network.N * rho0);
        if (rho0 == 0.0) {
            initialInfectedNum = 0;
        } else if (initialInfectedNum == 0 && rho0 > 0) {
            initialInfectedNum = 1;
        }

        int[] nodeIndices = new int[network.N];
        for (int i = 0; i < network.N; i++) nodeIndices[i] = i;
        int[] shuffled = com.example.utils.Array.shuffle(nodeIndices);

        for (int i = 0; i < initialInfectedNum; i++) {
            state[shuffled[i]] = 1;
            events.add(new SISEvent(0.0, SISEventType.INITIAL_INFECTION, shuffled[i]));
        }

        I[0] = initialInfectedNum;

        double currentTime = 0.0;
        int currentInfected = initialInfectedNum;
        int timeStepIndex = 1;

        while (currentTime < tmax && currentInfected > 0) {
            double totalRate = calculateTotalRate(network, state, lambda, gamma, c);
            if (totalRate <= 0) break;

            double timeToNextEvent = -Math.log(random.nextDouble()) / totalRate;
            currentTime += timeToNextEvent;

            while (timeStepIndex < numTimeSteps && (timeStepIndex * dt) <= currentTime) {
                I[timeStepIndex] = currentInfected;
                timeStepIndex++;
            }

            double r = random.nextDouble() * totalRate;
            double cum = 0.0;

            // 感染イベントの候補列挙
            boolean fired = false;
            for (int i = 0; i < network.N; i++) {
                if (state[i] == 0) {
                    int kInf = countInfectedNeighbors(network, state, i);
                    if (kInf > 0) {
                        cum += lambda * kInf;
                        if (r <= cum) {
                            state[i] = 1;
                            currentInfected++;
                            events.add(new SISEvent(currentTime, SISEventType.INFECTION, i));
                            fired = true;
                            break;
                        }
                    }
                }
            }

            // 回復イベント
            if (!fired) {
                for (int i = 0; i < network.N; i++) {
                    if (state[i] == 1) {
                        int kInf = countInfectedNeighbors(network, state, i);
                        cum += gamma / (c * kInf + 1);
                        if (r <= cum) {
                            state[i] = 0;
                            currentInfected--;
                            events.add(new SISEvent(currentTime, SISEventType.RECOVERY, i));
                            break;
                        }
                    }
                }
            }
        }

        while (timeStepIndex < numTimeSteps) {
            I[timeStepIndex] = currentInfected;
            timeStepIndex++;
        }

        SISEvent[] eventArray = events.toArray(new SISEvent[0]);
        return new ContinuousRunResult(I, dt, eventArray);
    }

    private static int countInfectedNeighbors(Network network, int[] state, int nodeIndex) {
        int cnt = 0;
        int[] nbrs = network.getNeighbors(nodeIndex);
        for (int v : nbrs) if (state[v] == 1) cnt++;
        return cnt;
    }

    private static double calculateTotalRate(Network network, int[] state, double lambda, double gamma, double c) {
        double total = 0.0;
        for (int i = 0; i < network.N; i++) {
            if (state[i] == 0) {
                int kInf = countInfectedNeighbors(network, state, i);
                if (kInf > 0) total += lambda * kInf;
            } else {
                int kInf = countInfectedNeighbors(network, state, i);
                total += gamma / (c * kInf + 1);
            }
        }
        return total;
    }
}
