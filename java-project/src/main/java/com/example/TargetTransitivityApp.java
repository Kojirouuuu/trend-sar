package com.example;

import com.example.network.Network;
import com.example.network.topology.EdgeTriangle;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generate networks via Edge–Triangle model with a target transitivity,
 * compute clustering metrics, and record results.
 */
public class TargetTransitivityApp {

    public static void main(String[] args) {
        // Parameters (can be adjusted or made CLI options)
        int N = 1000;
        int k = 12; // uniform degree
        double cMin = 0.00;
        double cMax = 0.40;
        double dc = 0.02;
        long seedBase = 42L;
        int repeats = 100; // repeat per target to smooth randomness

        // Degree sequence
        int[] degree = new int[N];
        for (int i = 0; i < N; i++) degree[i] = k;
        if (((long) N * k) % 2L != 0L) {
            throw new IllegalArgumentException("N*k must be even for a simple undirected graph");
        }

        // Output path
        String outDir = String.format("output/edge-triangle/z=%d/N=%d", k, N);
        String outFile = outDir + "/transitivity-sweep.csv";
        ensureParentDir(outFile);

        // Write header and rows
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile, false))) {
            bw.write("timestamp,target_transitivity,avg_clustering,transitivity,repeat,seed");
            bw.newLine();

            int idx = 0;
            for (double C = cMin; C <= cMax + 1e-12; C += dc) {
                double targetC = clamp(C, 0.0, 1.0);
                for (int r = 0; r < repeats; r++) {
                    long seed = seedBase + idx * 1_000_003L + r * 97L;

                    Network G = EdgeTriangle.generateFromDegreeAndTransitivity(N, degree, targetC, seed);
                    double avgC = G.averageClusteringCoefficient();
                    double trans = G.transitivity();

                    String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    bw.write(String.format(Locale.US, "%s,%.6f,%.6f,%.6f,%d,%d", ts, targetC, avgC, trans, r, seed));
                    bw.newLine();
                }
                idx++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Wrote clustering sweep to: " + outFile);
    }

    private static void ensureParentDir(String filename) {
        File f = new File(filename);
        File p = f.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}

