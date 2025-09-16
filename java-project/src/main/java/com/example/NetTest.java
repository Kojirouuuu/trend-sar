package com.example;

import com.example.network.Network;
import com.example.network.topology.S1;

public class NetTest {
    // ネットワークを生成
    public static void main(String[] args) {
        Network network = S1.loadFromCsv("networks/s1.csv");
        // 元のクラスタ係数を確認
        double originalClustering = network.averageClusteringCoefficient();
        System.out.println("元のクラスタ係数: " + originalClustering);

        // エッジの50%をリワイヤリング
        int swaps = network.rewirePreservingDegree(0.5);
        System.out.println("実行されたスワップ数: " + swaps);

        // リワイヤリング後のクラスタ係数を確認
        double newClustering = network.averageClusteringCoefficient();
        System.out.println("リワイヤリング後のクラスタ係数: " + newClustering);

        // 次数分布は変わらないことを確認
        network.printGraphInfo();
    }
}
