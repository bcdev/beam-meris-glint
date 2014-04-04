package org.esa.beam.glint.operators;

import org.esa.beam.glint.util.GlintHelpers;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Olaf Danne
 * @version $Revision: 5338 $ $Date: 2009-05-27 14:22:32 +0200 (Mi, 27 Mai 2009) $
 */
public class GlintHelpersTest {

    @Test
    public void testGetNearestValueIndexInDoubleArrays() {
        double[] testArray1 = new double[]{1.0, 2.0, 3.0, 6.0, 8.0, 10.0, 13.0, 14.0, 15.0};
        double value = 2.8;

        int index = GlintHelpers.getNearestValueIndexInAscendingDoubleArray(value, testArray1);
        assertEquals(2, index);

        double[] testArray2 = new double[]{21.0, 20.0, 13.0, 6.0, 2.0, 1.0, 0.0};
        index = GlintHelpers.getNearestValueIndexInDescendingDoubleArray(value, testArray2);
        assertEquals(4, index);
    }

    @Test
    public void testGetMinMaxInDoubleArrays() {
        double[] testArray1 = new double[]{1.0, 2.0, 3.0, 6.0, 8.0, 10.0, 16.0, 14.0, 15.0};

        int index = GlintHelpers.getMaximumValueIndexInDoubleArray(testArray1);
        assertEquals(6, index);
        double value = GlintHelpers.getMaximumValueInDoubleArray(testArray1);
        assertEquals(16.0, value, 1.0e-8);


        double[] testArray2 = new double[]{21.0, 20.0, 13.0, 6.0, 2.0, -3.0, 0.0};
        index = GlintHelpers.getMinimumValueIndexInDoubleArray(testArray2);
        assertEquals(5, index);
        value = GlintHelpers.getMinimumValueInDoubleArray(testArray2);
        assertEquals(-3.0, value, 1.0e-8);
    }

    @Test
    public void testLinearInterpol() {
        double x1 = 1.5;
        double x2 = 2.0;
        double y1 = 2.4;
        double y2 = 4.2;

        double x = 1.5;
        double y = GlintHelpers.linearInterpol(x, x1, x2, y1, y2);
        assertEquals(2.4, y, 1.0e-8);

        x = 1.7;
        y = GlintHelpers.linearInterpol(x, x1, x2, y1, y2);
        assertEquals(3.12, y, 1.0e-8);

        x = 1.95;
        y = GlintHelpers.linearInterpol(x, x1, x2, y1, y2);
        assertEquals(4.02, y, 1.0e-8);

        x = 2.0;
        y = GlintHelpers.linearInterpol(x, x1, x2, y1, y2);
        assertEquals(4.2, y, 1.0e-8);
    }
}
