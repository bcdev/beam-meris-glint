package org.esa.beam.atmosphere.operator;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 18.07.12
 * Time: 13:23
 *
 * @author olafd
 */
public class GlintCorrectionTest {

    @Test
    public void testGetChiSqrFromLargestDiffs() throws Exception {

        double[] arr1 = new double[]{2.0, 4.0, 3.0, 1.0};
        double[] arr2 = new double[]{3.0, 1.0, 0.0, 11.0};
        final double result1 = AbstractGlintCorrection.getChiSqrFromLargestDiffs(arr1, arr2, 1);
        assertEquals(100.0, result1, 1.E-3);
        final double result2 = AbstractGlintCorrection.getChiSqrFromLargestDiffs(arr1, arr2, 2);
        assertEquals(50.5, result2, 1.E-3);
        final double result3 = AbstractGlintCorrection.getChiSqrFromLargestDiffs(arr1, arr2, 3);
        assertEquals(33.854167, result3, 1.E-3);
        final double result4 = AbstractGlintCorrection.getChiSqrFromLargestDiffs(arr1, arr2, 4);
        assertEquals(25.453125, result4, 1.E-3);
    }
}
