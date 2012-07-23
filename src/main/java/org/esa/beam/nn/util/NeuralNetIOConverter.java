package org.esa.beam.nn.util;

/**
 * Helper class providing combinations of exponential/logarithm and multiplication/division for double arrays
 * (to be used for frequent changes of input/output of RD's neural nets)
 * Date: 23.07.12
 * Time: 15:14
 *
 * @author olafd
 */
public class NeuralNetIOConverter {

    public static double[] multiplyPi(double[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = arr[i] * Math.PI;
        }
        return result;
    }
    public static double[] dividePi(double[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = arr[i] / Math.PI;
        }
        return result;
    }

    public static double[] convertLogarithm(double[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = Math.log(arr[i]);
        }
        return result;
    }

    public static double[] convertLogarithmMultipliedPi(double[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = Math.log(arr[i] * Math.PI);
        }
        return result;
    }

    public static double[] convertLogarithmDividedPi(double[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = Math.log(arr[i] / Math.PI);
        }
        return result;
    }

    public static double[] convertExponential(double[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = Math.exp(arr[i]);
        }
        return result;
    }

    public static double[] convertExponentialMultiplyPi(double[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = Math.exp(arr[i] + Math.log(Math.PI));
        }
        return result;
    }

    public static double[] convertExponentialDividePi(double[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = Math.exp(arr[i] - Math.log(Math.PI));
        }
        return result;
    }

}
