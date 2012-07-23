package org.esa.beam.nn.util;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

public class NeuralNetIOConverterTest {

    @Test
    public void testMultiplyPi() throws Exception {
        double[] arr = new double[]{1.0, 2.0, 3.0};
        double[] result = NeuralNetIOConverter.multiplyPi(arr);
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(3.141992, result[0], 1.E-3);
        assertEquals(6.283185, result[1], 1.E-3);
        assertEquals(9.424777, result[2], 1.E-3);
    }

    @Test
    public void testDividePi() throws Exception {
        double[] arr = new double[]{3.141592, 6.283185, 9.424777};
        double[] result = NeuralNetIOConverter.dividePi(arr);
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(1.0, result[0], 1.E-3);
        assertEquals(2.0, result[1], 1.E-3);
        assertEquals(3.0, result[2], 1.E-3);
    }

    @Test
    public void testConvertLogarithm() throws Exception {
        double[] arr = new double[]{Math.exp(1.0), Math.exp(2.0), Math.exp(3.0)};
        double[] result = NeuralNetIOConverter.convertLogarithm(arr);
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(1.0, result[0], 1.E-3);
        assertEquals(2.0, result[1], 1.E-3);
        assertEquals(3.0, result[2], 1.E-3);
    }

    @Test
    public void testConvertLogarithmDividePi() throws Exception {
        double[] arr = new double[]{Math.PI * Math.exp(1.0), Math.PI * Math.exp(2.0), Math.PI * Math.exp(3.0)};
        double[] result = NeuralNetIOConverter.convertLogarithmDividedPi(arr);
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(1.0, result[0], 1.E-3);
        assertEquals(2.0, result[1], 1.E-3);
        assertEquals(3.0, result[2], 1.E-3);
    }

    @Test
    public void testConvertLogarithmMultiplyPi() throws Exception {
        double[] arr = new double[]{Math.exp(1.0) / Math.PI, Math.exp(2.0) / Math.PI, Math.exp(3.0) / Math.PI};
        double[] result = NeuralNetIOConverter.convertLogarithmMultipliedPi(arr);
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(1.0, result[0], 1.E-3);
        assertEquals(2.0, result[1], 1.E-3);
        assertEquals(3.0, result[2], 1.E-3);
    }

    @Test
     public void testConvertExponential() throws Exception {
        double[] arr = new double[]{Math.log(1.0), Math.log(2.0), Math.log(3.0)};
        double[] result = NeuralNetIOConverter.convertExponential(arr);
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(1.0, result[0], 1.E-3);
        assertEquals(2.0, result[1], 1.E-3);
        assertEquals(3.0, result[2], 1.E-3);
    }

    @Test
    public void testConvertExponentialDividePi() throws Exception {
        double[] arr = new double[]{Math.log(Math.PI), Math.log(2.0*Math.PI), Math.log(3.0*Math.PI)};
        double[] result = NeuralNetIOConverter.convertExponentialDividePi(arr);
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(1.0, result[0], 1.E-3);
        assertEquals(2.0, result[1], 1.E-3);
        assertEquals(3.0, result[2], 1.E-3);
    }

    @Test
    public void testConvertExponentialMultiplyPi() throws Exception {
        double[] arr = new double[]{Math.log(1.0/Math.PI), Math.log(2.0/Math.PI), Math.log(3.0/Math.PI)};
        double[] result = NeuralNetIOConverter.convertExponentialMultiplyPi(arr);
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(1.0, result[0], 1.E-3);
        assertEquals(2.0, result[1], 1.E-3);
        assertEquals(3.0, result[2], 1.E-3);
    }
}
