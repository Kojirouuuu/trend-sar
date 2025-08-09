package com.example.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.io.File;

public class Writer {
    private static void ensureDirectoryExists(String filename) {
        File file = new File(filename);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }

    public static void writeParametersToCSV(String filename, String networkType, int N, int k_ave, double lambdaMin, double lambdaMax, double dlambda, double gamma, double rho0Min, double rho0Max, double drho0, int T, int tmax, int batchNum, int itrPerBatch) {
        ensureDirectoryExists(filename);
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, false))) { // true: 追記, false: 上書き
            writer.println("networkType,N,k_ave,lambdaMin,lambdaMax,dlambda,gamma,rho0Min,rho0Max,drho0,T,tmax,batchNum,itrPerBatch");
            writer.println(networkType + "," + N + "," + k_ave + "," + lambdaMin + "," + lambdaMax + "," + dlambda + "," + gamma + "," + rho0Min + "," + rho0Max + "," + drho0 + "," + T + "," + tmax + "," + batchNum + "," + itrPerBatch);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * シミュレーション結果をCSVファイルに書き出す
     * @param filename 出力ファイル名
     * @param results 5次元配列 [stateId][list1Idx][list2Idx][itrIdx][timeIdx]
     *                stateId: 0=S, 1=A, 2=R
     * @param list1 第1パラメータリスト（lambda値）
     * @param list2 第2パラメータリスト（rho0値）
     * @param itr バッチあたりの反復回数
     * @param tmax 最大時間ステップ
     */
    public static void writeResultsToCSV(String filename, int[][][][][] results, double[] list1, double[] list2, int itr, int tmax) {
        ensureDirectoryExists(filename);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename, false))) {
            // ヘッダー行を書き込み
            writer.write("value");
            writer.newLine();
            
            // 各状態、パラメータ、時間ステップの結果を1行ずつ出力
            // 順序: [stateId][list1Idx][list2Idx][itrIdx][timeIdx]
            // stateId: 0=S, 1=A, 2=R
            for (int stateId = 0; stateId < results.length; stateId++) {
                for (int list1Idx = 0; list1Idx < list1.length; list1Idx++) {
                    for (int list2Idx = 0; list2Idx < list2.length; list2Idx++) {
                        for (int itrIdx = 0; itrIdx < itr; itrIdx++) {
                            for (int timeIdx = 0; timeIdx < tmax + 1; timeIdx++) {
                                writer.write(String.format("%d", results[stateId][list1Idx][list2Idx][itrIdx][timeIdx]));
                                writer.newLine();
                            }
                        }
                    }
                }
            }
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
}
