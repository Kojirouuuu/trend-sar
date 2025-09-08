package com.example;

import com.example.network.Network;
import com.example.network.topology.EdgeTriangle;
import com.example.simulation.SIS;
import com.example.utils.Params;
import com.example.utils.Array;
import com.example.utils.Writer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import java.lang.Runtime;
import java.util.stream.IntStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 2つのRRネットワークを結合したネットワークでのSISシミュレーション
 * 連続時間での感染ダイナミクスを解析する
 */
public class TriangleSIS {
    
    public static void main(String[] args) {
        // === シミュレーションパラメータの設定 ===
        String networkType = "EdgeTriangle";
        int N = 1000;
        int k_ave = 6;
        double lambdaMin = 0.00;
        double lambdaMax = 0.30;
        double dlambda = 0.005;
        double gamma = 1.0;
        double rho0 = 1.0; // 初期感染率
        double tmax = 400.0;
        
        // beta の候補リスト
        double[] betaList = new double[] {0.0, 0.5, 1.0};
        double[] cList = new double[] {0.0, 0.05, 0.10, 0.15, 0.20};
        long seed = 0L;

        // itr 回繰り返し、各回のイベント列を1行CSVで書き出し
        int itr = 5; // 必要に応じて変更
        int batchNum = 32;

        // === 出力ディレクトリの準備 ===
        String fileType = "final";
        String path = String.format("output/sis/%s/z=%d/N=%d%s", networkType, k_ave, N, fileType);
        ensureParentDir(path);

        // === パラメータを辞書っぽくCSVに保存 ===
        Params params = new Params()
            .put("networkType", networkType)
            .put("N", N)
            .put("k_ave", k_ave)
            .put("gamma", gamma)
            .put("rho0", rho0)
            .put("tmax", tmax)
            .put("seed", seed)
            .put("itr", itr)
            .put("batchNum", batchNum);
        
        // betaListを文字列に変換
        String betaListStr = "";
        for (int i = 0; i < betaList.length; i++) {
            if (i > 0) betaListStr += ":";
            betaListStr += String.format(Locale.US, "%.3f", betaList[i]);
        }
        params.put("betaList", betaListStr);

        // cListを文字列に変換
        String cListStr = "";
        for (int i = 0; i < cList.length; i++) {
            if (i > 0) cListStr += ":";
            cListStr += String.format(Locale.US, "%.3f", cList[i]);
        }
        params.put("cList", cListStr);

        // === lambda値のリストを生成 ===
        double[] lambdaList = Array.arange(lambdaMin, lambdaMax, dlambda);
        String lambdaListStr = "";
        for (int i = 0; i < lambdaList.length; i++) {
            if (i > 0) lambdaListStr += ":";
            lambdaListStr += String.format(Locale.US, "%.8f", lambdaList[i]);
        }
        params.put("lambdaList", lambdaListStr);

        String paramPath = String.format("%s/params.csv", path);
        Writer.writeParametersToCSV(paramPath, params);

        // === 全体メタデータのための開始時刻 ===
        LocalDateTime globalStart = LocalDateTime.now();
        long globalT0 = System.nanoTime();

        System.out.println(String.format("[%s] Start simulation", 
            globalStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        System.out.println(String.format("Run: Edge * c * lambda * itr = %d * %d * %d * %d = %d", 
            cList.length, betaList.length, lambdaList.length, itr, cList.length * betaList.length * lambdaList.length * itr));

        // === 並列シミュレーション実行 ===
        IntStream.range(0, batchNum).parallel().forEach(b -> {
            String outDir = path;
            String timeFile = outDir + String.format("/times_%02d.txt", b);
            String infectedFile = outDir + String.format("/infected_num_%02d.txt", b);

            ensureParentDir(timeFile);
            ensureParentDir(infectedFile);

            try (BufferedWriter tw = new BufferedWriter(new FileWriter(timeFile, false));
                 BufferedWriter iw = new BufferedWriter(new FileWriter(infectedFile, false))) {
                
                for (int c_idx = 0; c_idx < cList.length; c_idx++) {
                    double c = cList[c_idx];
                    int[] degree = new int[N];
                    for (int i = 0; i < N; i++) degree[i] = k_ave;
                    if (((long) N * k_ave) % 2L != 0L) {
                        throw new IllegalArgumentException("N*k must be even for a simple undirected graph");
                    }

                    Network net = EdgeTriangle.generateFromDegreeAndTransitivity(N, degree, c, seed);
                    double trans = net.transitivity();
                    double avgC = net.averageClusteringCoefficient();
                    System.out.println(String.format("c: %.3f, trans: %.3f, avgC: %.3f", c, trans, avgC));
                    
                    for (int betaIdx = 0; betaIdx < betaList.length; betaIdx++) {
                        double beta = betaList[betaIdx];
                        
                        for (int lIdx = 0; lIdx < lambdaList.length; lIdx++) {
                            double lambda = lambdaList[lIdx];
                            
                            for (int it2 = 0; it2 < itr; it2++) {
                                // シード値の計算
                                long runSeed = seed
                                    + it2
                                    + (long) c_idx * 1_000_003L
                                    + (long) betaIdx * 1_000_003L
                                    + (long) lIdx * 10_007L
                                    + (long) b * 1_000_000_007L;
                                
                                // SISシミュレーション実行
                                SIS.RunResult res = SIS.simulateOnce(net, lambda, gamma, rho0, tmax, beta, runSeed);
                                double[] T = res.times();
                                int[] I = res.infectedSeries();

                                // 時刻列と感染者数列の出力
                                StringBuilder tsb = new StringBuilder();
                                StringBuilder isb = new StringBuilder();

                                if (fileType.equals("time")) {
                                    // 全時刻と感染者数を出力
                                    for (int k = 0; k < T.length; k++) {
                                        if (k > 0) tsb.append(',');
                                        tsb.append(T[k]);
                                    }
                                    tw.write(tsb.toString());
                                    tw.newLine();

                                    for (int k = 0; k < I.length; k++) {
                                        if (k > 0) isb.append(',');
                                        isb.append(I[k]);
                                    }
                                    iw.write(isb.toString());
                                    iw.newLine();
                                } else {
                                    // 最終時刻と最終感染者数のみ出力
                                    tsb.append(T[T.length - 1]);
                                    tw.write(tsb.toString());
                                    tw.newLine();

                                    isb.append(I[I.length - 1]);
                                    iw.write(isb.toString());
                                    iw.newLine();
                                }

                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println(String.format("[%s] Completed batch %02d", 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), b));
        });

        // === 総合メタデータを書き出す（全処理完了後） ===
        LocalDateTime globalEnd = LocalDateTime.now();
        long elapsedNs = System.nanoTime() - globalT0;
        int lambdaCount = lambdaList.length;
        int runsPerBatch = lambdaCount * cList.length * itr;
        long totalRuns = (long) runsPerBatch * batchNum;
        long cpuCores = Runtime.getRuntime().availableProcessors();
        long totalMemMB = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long maxMemMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        String metaPath = String.format("%s/metadata.csv", path);
        ensureParentDir(metaPath);
        
        try (BufferedWriter mw = new BufferedWriter(new FileWriter(metaPath, false))) {
            mw.write("key,value"); 
            mw.newLine();
            mw.write("start_time," + globalStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); 
            mw.newLine();
            mw.write("end_time," + globalEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); 
            mw.newLine();
            mw.write("duration_seconds," + String.format(Locale.US, "%.3f", elapsedNs / 1e9)); 
            mw.newLine();
            mw.write("network_type," + networkType); 
            mw.newLine();
            mw.write("runs_per_batch," + runsPerBatch); 
            mw.newLine();
            mw.write("total_runs," + totalRuns); 
            mw.newLine();
            mw.write("seed_base," + seed); 
            mw.newLine();
            mw.write("os_name," + System.getProperty("os.name")); 
            mw.newLine();
            mw.write("os_version," + System.getProperty("os.version")); 
            mw.newLine();
            mw.write("java_version," + System.getProperty("java.version")); 
            mw.newLine();
            mw.write("java_vendor," + System.getProperty("java.vendor")); 
            mw.newLine();
            mw.write("cpu_cores," + cpuCores); 
            mw.newLine();
            mw.write("total_memory_mb," + totalMemMB); 
            mw.newLine();
            mw.write("max_memory_mb," + maxMemMB); 
            mw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
