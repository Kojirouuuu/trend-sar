package com.example;

import com.example.network.Network;
import com.example.simulation.SIS;
import com.example.utils.Array;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * 2つのRRネットワークを結合したネットワークでのSISシミュレーション
 * 連続時間での感染ダイナミクスを解析する
 */
public class App {

    private static final int PROGRESS_BAR_LENGTH = 100; // 進捗バーの長さ
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 100; // 進捗更新間隔（ミリ秒）

    public static void main(String[] args) {
        // === シミュレーションパラメータの設定 ===
        String networkType = "TwoRR"; // "ER", "BA", "RR" が利用可能
        int N = 10000;
        int kAve = 6;
        // double lambdaMin = 0.00;
        // double lambdaMax = 0.30;
        // double dlambda = 0.006;
        double lambdaMin = 0.00;
        double lambdaMax = 0.10;
        double dlambda = 0.002;
        double[] lambdaList = Array.arange(lambdaMin, lambdaMax, dlambda);

        int N2 = N;
        int k2 = kAve;
        int edgeNum = 4;

        double cMin = 2.0;
        double cMax = 2.0;
        double dc = 0.01;
        // double[] cList = Array.arange(cMin, cMax, dc);
        double[] cList = new double[] {cMin};

        double rho0Min = 0.0;
        double rho0Max = 1.0;
        double drho0 = 0.1;
        // double[] rho0List = Array.arange(rho0Min, rho0Max, drho0);
        double[] rho0List = new double[] {1.0};

        double mu = 1.0;
        double tmax = 2000.0;
        
        long seed = 0L;

        // itr 回繰り返し、各回のイベント列を1行CSVで書き出し
        int itr = 20; // 必要に応じて変更
        int batchNum = 10;

        // === 出力ディレクトリの準備 ===
        String fileType = "final";
        String iniType = "nonbfs";
        String path = String.format("output/sis/%s/edgeNum=%d/z=%d/N=%dcMin=%.2f%s%s", networkType, edgeNum, kAve, N, cMin, fileType, iniType);
        ensureParentDir(path);

        long totalTasks = (long) batchNum * rho0List.length * cList.length * lambdaList.length * itr;
        AtomicLong done = new AtomicLong(0);
        AtomicBoolean running = new AtomicBoolean(true);

        Thread renderer = createTotalProgressRenderer(done, totalTasks, running);
        renderer.start();

        // === 並列シミュレーション実行 ===
        boolean includeTime = fileType.equals("final");
        try {
            IntStream.range(0, batchNum).parallel().forEach(b -> {
            String outDir = path;
            String csvFile = outDir + String.format("/results_%02d.csv", b);

            ensureParentDir(csvFile);

            try (BufferedWriter csv = new BufferedWriter(new FileWriter(csvFile, false))) {
                // ヘッダー: itr,lambda,mu,rho,c,(time,)S,I
                if (includeTime) {
                    csv.write("itr,lambda,mu,rho,c,time,S,I");
                } else {
                    csv.write("itr,lambda,mu,rho,c,S,I");
                }
                csv.newLine();

                for (int rho0Idx = 0; rho0Idx < rho0List.length; rho0Idx++) {
                    double rho0 = rho0List[rho0Idx];
                    // バッチ番号 b でネットワーク用シードを分離（並列でも再現可能）
                    long networkSeed = seed + (long) b * 1_000_000_007L;
                    Network net = Network.generateNetwork(networkType, N, kAve, N2, k2, edgeNum, networkSeed);

                    for (int cIdx = 0; cIdx < cList.length; cIdx++) {
                        double c = cList[cIdx];

                        for (int lIdx = lambdaList.length - 1; lIdx >= 0; lIdx--) {
                            double lambda = lambdaList[lIdx];

                            for (int it2 = 0; it2 < itr; it2++) {
                                // シード値の計算
                                long runSeed = seed
                                    + it2
                                    + (long) cIdx * 1_000_003L
                                    + (long) lIdx * 10_007L
                                    + (long) b * 1_000_000_007L;

                                // SISシミュレーション実行
                                SIS.RunResult res = SIS.simulateOnce(net, lambda, mu, rho0, tmax, c, runSeed, iniType);
                                double[] T = res.times();
                                int[] I = res.infectedSeries();

                                int itrVal = b * itr + it2;
                                double time = T[T.length - 1];
                                int iFinal = I[I.length - 1];
                                int totalNodes = net.N; // TwoRR では N1+N2、それ以外は N
                                int sFinal = totalNodes - iFinal;

                                if (includeTime) {
                                    csv.write(String.format("%d,%.6g,%.6g,%.6g,%.6g,%.6g,%d,%d%n",
                                            itrVal, lambda, mu, rho0, c, time, sFinal, iFinal));
                                } else {
                                    csv.write(String.format("%d,%.6g,%.6g,%.6g,%.6g,%d,%d%n",
                                            itrVal, lambda, mu, rho0, c, sFinal, iFinal));
                                }

                                done.incrementAndGet();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            });
        } finally {
            running.set(false);
            try {
                renderer.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        synchronized (System.out) {
            renderTotalProgressBar(totalTasks, totalTasks);
            System.out.println();
        }
        System.out.println("All tasks completed");
    }

    /**
     * 進捗表示スレッドを作成する。
     *
     * @param done       完了タスク数のカウンタ
     * @param totalTasks 総タスク数
     * @param running    実行中フラグ
     * @return 進捗表示スレッド
     */
    private static Thread createTotalProgressRenderer(AtomicLong done, long totalTasks, AtomicBoolean running) {
        return new Thread(() -> {
            long lastPrintedDone = 0;

            while (running.get()) {
                long d = done.get();
                if (d != lastPrintedDone) {
                    synchronized (System.out) {
                        renderTotalProgressBar(d, totalTasks);
                    }
                    lastPrintedDone = d;
                }

                if (d >= totalTasks) {
                    break;
                }

                try {
                    Thread.sleep(PROGRESS_UPDATE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    running.set(false);
                    break;
                }
            }

            synchronized (System.out) {
                renderTotalProgressBar(totalTasks, totalTasks);
                System.out.println();
            }
        }, "progress-renderer");
    }

    /**
     * 進捗バーを表示する。
     *
     * @param done  完了タスク数
     * @param total 総タスク数
     */
    private static void renderTotalProgressBar(long done, long total) {
        int filled = (total == 0) ? PROGRESS_BAR_LENGTH
                : (int) Math.min(PROGRESS_BAR_LENGTH, (done * PROGRESS_BAR_LENGTH) / total);

        int percent = (total == 0) ? 100 : (int) Math.min(100, (done * 100) / total);

        String bar = "#".repeat(filled) + "-".repeat(PROGRESS_BAR_LENGTH - filled);

        // 行をクリアしてから進捗を表示（\033[2K は行全体をクリア、\r は行頭に戻る）
        System.out.print("\033[2K\rProgress [%s] %3d%% (%d/%d)".formatted(bar, percent, done, total));
        System.out.flush();
    }

    /**
     * 指定されたファイルの親ディレクトリが存在しない場合は作成する
     * @param filename ファイルパス
     */
    private static void ensureParentDir(String filename) {
        File f = new File(filename);
        File p = f.getParentFile();
        if (p != null && !p.exists()) {
            p.mkdirs();
        }
    }
}
