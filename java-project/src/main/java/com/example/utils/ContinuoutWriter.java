package com.example.utils;

import com.example.simulation.SISEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

public class ContinuoutWriter {

    private static final DecimalFormat DF = new DecimalFormat("0.############");

    private static void ensureDirectoryExists(String filename) {
        File file = new File(filename);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }

    /**
     * Paramsオブジェクトを使用してパラメータをCSVに書き出す
     * @param filename 出力ファイル名
     * @param params パラメータオブジェクト
     */
    public static void writeParametersToCSV(String filename, Params params) {
        ensureDirectoryExists(filename);
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, false))) { // true: 追記, false: 上書き
            // ヘッダー行を書き込み
            writer.println(params.toCsvHeader());
            // 値行を書き込み
            writer.println(params.toCsvValues());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeMetadataToCSV(String filename, LocalDateTime startTime, LocalDateTime endTime) {
        ensureDirectoryExists(filename);
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, false))) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            Duration duration = Duration.between(startTime, endTime);
            
            // システム情報を取得
            String osName = System.getProperty("os.name");
            String osVersion = System.getProperty("os.version");
            String javaVersion = System.getProperty("java.version");
            String javaVendor = System.getProperty("java.vendor");
            String processorCount = System.getProperty("os.availableProcessors");
            String totalMemory = String.valueOf(Runtime.getRuntime().totalMemory() / (1024 * 1024)) + " MB";
            String maxMemory = String.valueOf(Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " MB";
            
            writer.println("項目,値");
            writer.println("シミュレーション開始時間," + startTime.format(formatter));
            writer.println("シミュレーション終了時間," + endTime.format(formatter));
            writer.println("実行時間（秒）," + duration.getSeconds());
            writer.println("実行時間（分）," + String.format("%.2f", duration.getSeconds() / 60.0));
            writer.println("実行時間（時間）," + String.format("%.2f", duration.getSeconds() / 3600.0));
            writer.println("OS名," + osName);
            writer.println("OSバージョン," + osVersion);
            writer.println("Javaバージョン," + javaVersion);
            writer.println("Javaベンダー," + javaVendor);
            writer.println("利用可能プロセッサ数," + processorCount);
            writer.println("総メモリ," + totalMemory);
            writer.println("最大メモリ," + maxMemory);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * I(t) の5次元配列を縦持ちCSVに出力
     * 出力列: c, lambda, rho0, itr, t_index, t, I
     */
    public static void write3ArgsOneStateResultsToCSV(
            String path,
            int[][][][][] results,
            double[] cList, double[] lambdaList, double[] rho0List,
            int itrPerBatch,
            int tmax,
            double dt) {

        final boolean newFile = Files.notExists(Path.of(path));
        try (BufferedWriter w = Files.newBufferedWriter(
                Path.of(path), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            if (newFile) w.write("c,lambda,rho0,itr,t_index,t,I\n");

            int cLen = results.length;
            int lambdaLen = results[0].length;
            int rho0Len = results[0][0].length;

            for (int cIdx = 0; cIdx < cLen; cIdx++) {
                for (int lIdx = 0; lIdx < lambdaLen; lIdx++) {
                    for (int rIdx = 0; rIdx < rho0Len; rIdx++) {
                        for (int itrIdx = 0; itrIdx < itrPerBatch; itrIdx++) {
                            int[] series = results[cIdx][lIdx][rIdx][itrIdx];
                            for (int k = 0; k < series.length; k++) {
                                double t = k * dt;
                                w.write(DF.format(cList[cIdx]) + "," +
                                        DF.format(lambdaList[lIdx]) + "," +
                                        DF.format(rho0List[rIdx]) + "," +
                                        itrIdx + "," +
                                        k + "," +
                                        DF.format(t) + "," +
                                        series[k] + "\n");
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * イベント履歴を縦持ちCSVに出力
     * 出力列: c, lambda, rho0, itr, time, type, node
     */
    public static void writeEventHistoriesToCSV(
            String path,
            SISEvent[][][][][] eventHistories,
            double[] cList, double[] lambdaList, double[] rho0List,
            int itrPerBatch) {

        final boolean newFile = Files.notExists(Path.of(path));
        try (BufferedWriter w = Files.newBufferedWriter(
                Path.of(path), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            if (newFile) w.write("c,lambda,rho0,itr,time,type,node\n");

            int cLen = eventHistories.length;
            int lambdaLen = eventHistories[0].length;
            int rho0Len = eventHistories[0][0].length;

            for (int cIdx = 0; cIdx < cLen; cIdx++) {
                for (int lIdx = 0; lIdx < lambdaLen; lIdx++) {
                    for (int rIdx = 0; rIdx < rho0Len; rIdx++) {
                        for (int itrIdx = 0; itrIdx < itrPerBatch; itrIdx++) {
                            SISEvent[] events = eventHistories[cIdx][lIdx][rIdx][itrIdx];
                            if (events == null) continue;
                            for (SISEvent e : events) {
                                w.write(DF.format(cList[cIdx]) + "," +
                                        DF.format(lambdaList[lIdx]) + "," +
                                        DF.format(rho0List[rIdx]) + "," +
                                        itrIdx + "," +
                                        DF.format(e.time()) + "," +
                                        e.type().name() + "," +
                                        e.node() + "\n");
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
