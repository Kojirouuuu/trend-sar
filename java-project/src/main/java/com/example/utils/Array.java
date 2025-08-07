package com.example.utils;

import java.util.Random;

public class Array {
    public static int[] shuffle(int[] array) {
        Random random = new Random();

        int length = array.length;
        for (int i = length - 1; i > 0; i-- ) {
            int j = random.nextInt(i + 1);
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }

        return array;
    }

    public static int[] arange(int start, int end, int step) {
        int length = (int)((end - start) / step) + 1;
        int[] array = new int[length];
        for (int i = 0; i < length; i++) {
            array[i] = start + i * step;
        }
        return array;
    }
    public static double[] arange(double start, double end, double step) {
        int length = (int)((end - start) / step) + 1;
        double[] array = new double[length];
        for (int i = 0; i < length; i++) {
            array[i] = start + i * step;
        }
        return array;
    }
}
