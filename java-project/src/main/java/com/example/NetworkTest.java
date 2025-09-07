package com.example;

import com.example.network.Network;
import com.example.network.topology.TwoRR;

public class NetworkTest {
    public static void main(String[] args) {
        int N = 1000;
        int k_ave = 10;
        int edgeNum = 0;
        Network net = TwoRR.generate2RR(N, k_ave, N, k_ave, edgeNum);

        System.out.println("ネットワーク情報");
        net.printGraphInfo();
        System.out.println("頂点0次数: " + net.degree(0));
        int[] neighbors = net.getNeighbors(0);
        for (int neighbor : neighbors) {
            System.out.println("頂点0隣接点: " + neighbor);
        }
        System.out.println("頂点N-1次数: " + net.degree(2*N-1));
        neighbors = net.getNeighbors(2*N-1);
        for (int neighbor : neighbors) {
            System.out.println("頂点N-1隣接点: " + neighbor);
        }
    }
}
