package com.example;

import com.example.network.Network;
import com.example.network.topology.TwoRR;

public class NetworkTest {
    public static void main(String[] args) {
        // デフォルト設定（引数が無い場合は3種類すべてを書き出す）
        int N = 100;
        int k = 6;

        if (args.length == 0) {
            exportBA(N, k);
            exportER(N, k);
            exportTwoRR(N / 2, k, N - (N / 2), k, Math.max(1, N / 10));
            return;
        }

        // 簡易引数モード
        // 例:
        //  BA N k
        //  ER N k
        //  TwoRR N1 k1 N2 k2 cross
        String type = args[0];
        switch (type) {
            case "BA": {
                int n = args.length > 1 ? Integer.parseInt(args[1]) : N;
                int kk = args.length > 2 ? Integer.parseInt(args[2]) : k;
                exportBA(n, kk);
                break;
            }
            case "ER": {
                int n = args.length > 1 ? Integer.parseInt(args[1]) : N;
                int kk = args.length > 2 ? Integer.parseInt(args[2]) : k;
                exportER(n, kk);
                break;
            }
            case "TwoRR": {
                int n1 = args.length > 1 ? Integer.parseInt(args[1]) : N / 2;
                int k1 = args.length > 2 ? Integer.parseInt(args[2]) : k;
                int n2 = args.length > 3 ? Integer.parseInt(args[3]) : N - (N / 2);
                int k2 = args.length > 4 ? Integer.parseInt(args[4]) : k;
                int cross = args.length > 5 ? Integer.parseInt(args[5]) : Math.max(1, N / 10);
                exportTwoRR(n1, k1, n2, k2, cross);
                break;
            }
            default:
                System.err.println("Unknown type: " + type + " (use BA, ER, or TwoRR)");
        }
    }

    private static void exportBA(int N, int k) {
        String type = "BA";
        Network net = Network.generateNetwork(type, N, k);
        System.out.printf("%s N=%d k=%d\n", type, N, k);
        net.printGraphInfo();
        String base = String.format("networks/%s/%d", type, N);
        net.exportToGephiEdgesCSV(base + "/graph_edges.csv");
        net.exportToGephiNodesCSV(base + "/graph_nodes.csv");
    }

    private static void exportER(int N, int k) {
        String type = "ER";
        Network net = Network.generateNetwork(type, N, k);
        System.out.printf("%s N=%d k≈%d\n", type, N, k);
        net.printGraphInfo();
        String base = String.format("networks/%s/%d", type, N);
        net.exportToGephiEdgesCSV(base + "/graph_edges.csv");
        net.exportToGephiNodesCSV(base + "/graph_nodes.csv");
    }

    private static void exportTwoRR(int N1, int k1, int N2, int k2, int cross) {
        String type = "TwoRR";
        Network net = TwoRR.generate2RR(N1, k1, N2, k2, cross);
        System.out.printf("%s N1=%d k1=%d N2=%d k2=%d cross=%d (N=%d)\n",
                type, N1, k1, N2, k2, cross, net.N);
        net.printGraphInfo();
        String base = String.format("networks/%s/%d", type, net.N);
        net.exportToGephiEdgesCSV(base + "/graph_edges.csv");
        net.exportToGephiNodesCSV(base + "/graph_nodes.csv");
    }

    
}
