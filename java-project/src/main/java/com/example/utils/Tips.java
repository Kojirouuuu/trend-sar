package com.example.utils;

import com.example.network.Network;
import java.util.Arrays;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;


public class Tips {
    static int[] bfs(Network network, int s) {
        int n = network.N;
        int[] dist = new int[n];
        Arrays.fill(dist, -1);

        Queue<Integer> q = new ArrayDeque<>();
        dist[s] = 0;
        q.add(s);

        while (!q.isEmpty()) {
            int v = q.poll();
            for (int to : network.getNeighbors(v)) {
                if (dist[to] != -1) continue;
                dist[to] = dist[v] + 1;
                q.add(to);
            }
        }
        return dist;
    }

    public static int[] bfsInitialInfect(Network network, int initialInfectedNum) {
        int n = network.N;
        Set<Integer> initialInfectedNodes = new HashSet<>();

        Random random = new Random();

        Queue<Integer> q = new ArrayDeque<>();
        int s = random.nextInt(n);
        initialInfectedNodes.add(s);
        q.add(s);

        while (!q.isEmpty()) {
            int v = q.poll();
            for (int to : network.getNeighbors(v)) {
                if (initialInfectedNodes.contains(to)) continue;
                initialInfectedNodes.add(to);
                q.add(to);

                if (initialInfectedNodes.size() >= initialInfectedNum) {
                    break;
                }
            }
        }

        int[] initialInfectedNodesArray = new int[initialInfectedNum];
        int i = 0;
        for (int node : initialInfectedNodes) {
            initialInfectedNodesArray[i] = node;
            i++;
        }
        return initialInfectedNodesArray;
    }
}
