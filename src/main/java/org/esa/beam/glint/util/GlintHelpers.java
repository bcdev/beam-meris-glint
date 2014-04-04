package org.esa.beam.glint.util;

/**
 * Helper class
 *
 * @author Olaf Danne
 * @version $Revision: 5340 $ $Date: 2009-05-27 18:30:05 +0200 (Mi, 27 Mai 2009) $
 */
public class GlintHelpers {

/**
     * This method returns the index of descending double array element closest to x
     *
     * @param x
     * @param array
     * @return
     */
    public static int getNearestValueIndexInAscendingDoubleArray(double x, double[] array) {
        int nearestValueIndex = -1;

        for (int i = 1; i < array.length; i++) {
            if (x < array[i]) {
                if (array[i]-x > x-array[i-1]) {
                    nearestValueIndex = i-1;
                } else {
                    nearestValueIndex = i;
                }
                break;
            }
        }

        return nearestValueIndex;
    }

    /**
     * This method returns the index of asscending double array element closest to x
     *
     * @param x
     * @param array
     * @return
     */
    public static int getNearestValueIndexInDescendingDoubleArray(double x, double[] array) {
        int nearestValueIndex = -1;

        for (int i = 1; i < array.length; i++) {
            if (x > array[i]) {
                if (array[i]-x < x-array[i-1]) {
                    nearestValueIndex = i-1;
                } else {
                    nearestValueIndex = i;
                }
                break;
            }
        }

        return nearestValueIndex;
    }

    /**
     * This method returns the index of the maximum in double array
     *
     * @param array
     * @return
     */
    public static int getMaximumValueIndexInDoubleArray(double[] array) {
        int maximumValueIndex = -1;
        double maximumValue = Double.MIN_VALUE;

        for (int i = 0; i < array.length; i++) {
            if (array[i] > maximumValue) {
                maximumValue = array[i];
                maximumValueIndex = i;
            }
        }

        return maximumValueIndex;
    }

    /**
     * This method returns the maximum in a double array
     *
     * @param array
     * @return
     */
    public static double getMaximumValueInDoubleArray(double[] array) {
        double maximumValue = Double.MIN_VALUE;

        for (int i = 0; i < array.length; i++) {
            if (array[i] > maximumValue) {
                maximumValue = array[i];
            }
        }

        return maximumValue;
    }


    /**
     * This method returns the index of the minumum in double array
     *
     * @param array
     * @return
     */
    public static int getMinimumValueIndexInDoubleArray(double[] array) {
        int minimumValueIndex = -1;
        double minimumValue = Double.MAX_VALUE;

        for (int i = 0; i < array.length; i++) {
            if (array[i] < minimumValue) {
                minimumValue = array[i];
                minimumValueIndex = i;
            }
        }

        return minimumValueIndex;
    }

      /**
     * This method returns the minumum in a double array
     *
     * @param array
     * @return
     */
    public static double getMinimumValueInDoubleArray(double[] array) {
        double minimumValue = Double.MAX_VALUE;

        for (int i = 0; i < array.length; i++) {
            if (array[i] < minimumValue) {
                minimumValue = array[i];
            }
        }

        return minimumValue;
    }

    /**
     * This method provides a simple linear interpolation
     *
     * @param x  , position in [x1,x2] to interpolate at
     * @param x1 , left neighbour of x
     * @param x2 , right neighbour of x
     * @param y1 , y(x1)
     * @param y2 , y(x2)
     *
     * @return double z = y(x), the interpolated value
     */
    public static double linearInterpol(double x, double x1, double x2, double y1, double y2) {
        double z;

        if (x < x1 || x > x2)
            z = 0.0;

        if (x1 == x2) {
            z = y1;
        } else {
            final double slope = (y2 - y1) / (x2 - x1);
            z = y1 + slope * (x - x1);
        }

        return z;
    }

}
