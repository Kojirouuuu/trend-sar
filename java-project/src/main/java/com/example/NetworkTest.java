package com.example;

import com.example.network.Network;
import com.example.network.topology.M1;

public class NetworkTest {
    public static void main(String[] args) {
        int N = 1429;
        int k_ave = 10;
        int edgeNum = 0;
        Network net = Network.generateNetwork("M1", N, k_ave, edgeNum);

        System.out.println("ネットワーク情報");
        net.printGraphInfo();
        System.out.println("頂点0次数: " + net.degree(0));
        int[] neighbors = net.getNeighbors(0);
        for (int neighbor : neighbors) {
            System.out.println("頂点0隣接点: " + neighbor);
        }
    }
}
