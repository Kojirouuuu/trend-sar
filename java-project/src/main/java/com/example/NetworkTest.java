package com.example;

import com.example.network.Network;
import java.nio.file.Path;
import java.io.IOException;

public class NetworkTest {
    public static void main(String[] args) {
        int N = 400;
        int k_ave = 6;
        int edgeNum = 4;
        Network net = Network.generateNetwork("TwoRR", N, k_ave, N, k_ave, edgeNum, 0L);
        Path outputPath = Path.of("output/network/TwoRR/N=" + N + "_k=" + k_ave + "_edgeNum=" + edgeNum + ".gexf");
        try {
            net.exportToGexf(outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("ネットワーク情報");
        net.printGraphInfo();
        System.out.println("頂点0次数: " + net.degree(0));
        int[] neighbors = net.getNeighbors(0);
        for (int neighbor : neighbors) {
            System.out.println("頂点0隣接点: " + neighbor);
        }
    }
}
