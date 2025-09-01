package com.example;

import com.example.network.Network;
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

public class App {
    public static void main(String[] args) {
        // 単発の連続時間 SIS シミュレーションを実行し、イベント時刻と感染者数を表示
        String networkType = "RR"; // "ER", "BA", "RR" が利用可能
        int N = 10000;
        int k_ave = 10;
        double lambdaMin = 0.00;
        double lambdaMax = 0.40;
        double dlambda   = 0.005;
        double gamma = 1.0;
        double rho0 = 1.0; // 初期感染率
        double tmax = 50.0;
        // c の候補リスト
        double[] cList = new double[] {0.0, 0.4, 1.2};
        long seed = 0L;

        // itr 回繰り返し、各回のイベント列を1行CSVで書き出し
        int itr = 10; // 必要に応じて変更
        int batchNum = 100;

        Network net = Network.generateNetwork(networkType, N, k_ave);

        // パラメータを辞書っぽくCSVに保存
        Params params = new Params()
            .put("networkType", networkType)
            .put("N", N)
            .put("k_ave", k_ave)
            .put("lambdaMin", lambdaMin)
            .put("lambdaMax", lambdaMax)
            .put("dlambda", dlambda)
            .put("gamma", gamma)
            .put("rho0", rho0)
            .put("tmax", tmax)
            .put("cList", "0.0,0.4,1.2")
            .put("seed", seed)
            .put("itr", itr)
            .put("batchNum", batchNum);
        String paramPath = "output/sis/params.csv";
        Writer.writeParametersToCSV(paramPath, params);

        double[] lambdaList = Array.arange(lambdaMin, lambdaMax, dlambda);

        // 全体メタデータのための開始時刻
        LocalDateTime globalStart = LocalDateTime.now();
        long globalT0 = System.nanoTime();

        IntStream.range(0, batchNum).parallel().forEach(b -> {
            String outDir = String.format("output/sis/N=%d", N);
            String timeFile = outDir + String.format("/times_%02d.txt", b);
            String infectedFile = outDir + String.format("/infected_num_%02d.txt", b);

            ensureParentDir(timeFile);
            ensureParentDir(infectedFile);

            try (BufferedWriter tw = new BufferedWriter(new FileWriter(timeFile, false));
                 BufferedWriter iw = new BufferedWriter(new FileWriter(infectedFile, false))) {
                for (int cIdx = 0; cIdx < cList.length; cIdx++) {
                    double c = cList[cIdx];
                    for (int lIdx = 0; lIdx < lambdaList.length; lIdx++) {
                        double lambda = lambdaList[lIdx];
                        for (int it2 = 0; it2 < itr; it2++) {
                            long runSeed = seed
                                    + it2
                                    + (long) cIdx * 1_000_003L
                                    + (long) lIdx * 10_007L
                                    + (long) b * 1_000_000_007L;
                            SIS.RunResult res = SIS.simulateOnce(net, lambda, gamma, rho0, tmax, c, runSeed);
                            double[] T = res.times();
                            int[] I = res.infectedSeries();

                            // 時刻列: カンマ区切り
                            StringBuilder tsb = new StringBuilder();
                            for (int k = 0; k < T.length; k++) {
                                if (k > 0) tsb.append(',');
                                tsb.append(String.format(Locale.US, "%.10f", T[k]));
                            }
                            tw.write(tsb.toString());
                            tw.newLine();

                            // 感染者数列: カンマ区切り
                            StringBuilder isb = new StringBuilder();
                            for (int k = 0; k < I.length; k++) {
                                if (k > 0) isb.append(',');
                                isb.append(I[k]);
                            }
                            iw.write(isb.toString());
                            iw.newLine();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        });

        // 総合メタデータを書き出す（全処理完了後）
        LocalDateTime globalEnd = LocalDateTime.now();
        long elapsedNs = System.nanoTime() - globalT0;
        int lambdaCount = lambdaList.length;
        int runsPerBatch = cList.length * lambdaCount * itr;
        long totalRuns = (long) runsPerBatch * batchNum;
        long cpuCores = Runtime.getRuntime().availableProcessors();
        long totalMemMB = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long maxMemMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        String metaPath = "output/sis/metadata.csv";
        ensureParentDir(metaPath);
        try (BufferedWriter mw = new BufferedWriter(new FileWriter(metaPath, false))) {
            mw.write("key,value"); mw.newLine();
            mw.write("start_time," + globalStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); mw.newLine();
            mw.write("end_time," + globalEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); mw.newLine();
            mw.write("duration_seconds," + String.format(Locale.US, "%.3f", elapsedNs / 1e9)); mw.newLine();
            mw.write("network_type," + networkType); mw.newLine();
            mw.write("N," + N); mw.newLine();
            mw.write("k_ave," + k_ave); mw.newLine();
            mw.write("gamma," + gamma); mw.newLine();
            mw.write("rho0," + rho0); mw.newLine();
            mw.write("tmax," + tmax); mw.newLine();
            mw.write("itr," + itr); mw.newLine();
            mw.write("batch_num," + batchNum); mw.newLine();
            mw.write("c_list,0.0,0.4,1.2"); mw.newLine();
            mw.write("lambda_min," + lambdaMin); mw.newLine();
            mw.write("lambda_max," + lambdaMax); mw.newLine();
            mw.write("dlambda," + dlambda); mw.newLine();
            mw.write("lambda_count," + lambdaCount); mw.newLine();
            mw.write("runs_per_batch," + runsPerBatch); mw.newLine();
            mw.write("total_runs," + totalRuns); mw.newLine();
            mw.write("seed_base," + seed); mw.newLine();
            mw.write("os_name," + System.getProperty("os.name")); mw.newLine();
            mw.write("os_version," + System.getProperty("os.version")); mw.newLine();
            mw.write("java_version," + System.getProperty("java.version")); mw.newLine();
            mw.write("java_vendor," + System.getProperty("java.vendor")); mw.newLine();
            mw.write("cpu_cores," + cpuCores); mw.newLine();
            mw.write("total_memory_mb," + totalMemMB); mw.newLine();
            mw.write("max_memory_mb," + maxMemMB); mw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void ensureParentDir(String filename) {
        File f = new File(filename);
        File p = f.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
    }
}
