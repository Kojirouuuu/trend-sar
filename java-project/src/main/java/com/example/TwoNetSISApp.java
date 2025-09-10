package com.example;

import com.example.network.Network;
import com.example.network.topology.TwoRR;
import com.example.simulation.TwoNetSIS;
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
public class TwoNetSISApp {
    
    public static void main(String[] args) {
        // === シミュレーションパラメータの設定 ===
        String networkType = "2RR"; // "ER", "BA", "RR" が利用可能
        int N = 5000;
        int k_ave = 6;
        double lambdaMin = 0.02;
        double lambdaMax = 0.08;
        double dlambda = 0.0005;
        double gamma = 1.0;
        double rho0 = 1.0 / 2.0; // 初期感染率
        double tmax = 400.0;
        
        // c の候補リスト
        double[] cList = new double[] {2.5, 5.0};
        int[] edgeNumList = new int[] {0, 1, 10, 20};
        long seed = 0L;

        // itr 回繰り返し、各回のイベント列を1行CSVで書き出し
        int itr = 10; // 必要に応じて変更
        int batchNum = 100;

        // === 出力ディレクトリの準備 ===
        String fileType = "final";
        String path = String.format("output/sis/%s/z=%d/N=%d%snew", networkType, k_ave, N, fileType);
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
        
        // cListを文字列に変換
        String cListStr = "";
        for (int i = 0; i < cList.length; i++) {
            if (i > 0) cListStr += ":";
            cListStr += String.format(Locale.US, "%.3f", cList[i]);
        }
        params.put("cList", cListStr);

        // edgeNumListを文字列に変換
        String edgeNumListStr = "";
        for (int i = 0; i < edgeNumList.length; i++) {
            if (i > 0) edgeNumListStr += ":";
            edgeNumListStr += String.format(Locale.US, "%d", edgeNumList[i]);
        }
        params.put("edgeNumList", edgeNumListStr);

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
            edgeNumList.length, cList.length, lambdaList.length, itr, edgeNumList.length * cList.length * lambdaList.length * itr));

        // === 並列シミュレーション実行 ===
        IntStream.range(0, batchNum).parallel().forEach(b -> {
            String outDir = path;
            String timeFile = outDir + String.format("/times_%02d.txt", b);
            String infectedFile = outDir + String.format("/infected_num_%02d.txt", b);
            String infectedAFile = outDir + String.format("/infected_num_A_%02d.txt", b);
            String infectedBFile = outDir + String.format("/infected_num_B_%02d.txt", b);

            ensureParentDir(timeFile);
            ensureParentDir(infectedFile);
            ensureParentDir(infectedAFile);
            ensureParentDir(infectedBFile);

            try (BufferedWriter tw = new BufferedWriter(new FileWriter(timeFile, false));
                 BufferedWriter iw = new BufferedWriter(new FileWriter(infectedFile, false));
                 BufferedWriter iwA = new BufferedWriter(new FileWriter(infectedAFile, false));
                 BufferedWriter iwB = new BufferedWriter(new FileWriter(infectedBFile, false))) {
                
                for (int edgeNumIdx = 0; edgeNumIdx < edgeNumList.length; edgeNumIdx++) {
                    int edgeNum = edgeNumList[edgeNumIdx];
                    Network net = TwoRR.generate2RR(N, k_ave, N, k_ave, edgeNum, seed);
                    
                    for (int cIdx = 0; cIdx < cList.length; cIdx++) {
                        double c = cList[cIdx];
                        
                        for (int lIdx = 0; lIdx < lambdaList.length; lIdx++) {
                            double lambda = lambdaList[lIdx];
                            
                            for (int it2 = 0; it2 < itr; it2++) {
                                // シード値の計算
                                long runSeed = seed
                                    + it2
                                    + (long) cIdx * 1_000_003L
                                    + (long) lIdx * 10_007L
                                    + (long) b * 1_000_000_007L;
                                
                                // SISシミュレーション実行
                                TwoNetSIS.RunResult res = TwoNetSIS.simulateOnce(net, lambda, gamma, rho0, tmax, c, runSeed);
                                double[] T = res.times();
                                int[] I = res.infectedSeries();
                                int[] IA = res.infectedSeriesA();
                                int[] IB = res.infectedSeriesB();

                                // 時刻列と感染者数列の出力
                                StringBuilder tsb = new StringBuilder();
                                StringBuilder isb = new StringBuilder();
                                StringBuilder isbA = new StringBuilder();
                                StringBuilder isbB = new StringBuilder();

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

                                    for (int k = 0; k < IA.length; k++) {
                                        if (k > 0) isbA.append(',');
                                        isbA.append(IA[k]);
                                    }
                                    iwA.write(isbA.toString());
                                    iwA.newLine();
                                    for (int k = 0; k < IB.length; k++) {
                                        if (k > 0) isbB.append(',');
                                        isbB.append(IB[k]);
                                    }
                                    iwB.write(isbB.toString());
                                    iwB.newLine();
                                } else {
                                    // 最終時刻と最終感染者数のみ出力
                                    tsb.append(T[T.length - 1]);
                                    tw.write(tsb.toString());
                                    tw.newLine();

                                    isb.append(I[I.length - 1]);
                                    iw.write(isb.toString());
                                    iw.newLine();

                                    isbA.append(IA[IA.length - 1]);
                                    iwA.write(isbA.toString());
                                    iwA.newLine();

                                    isbB.append(IB[IB.length - 1]);
                                    iwB.write(isbB.toString());
                                    iwB.newLine();
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
