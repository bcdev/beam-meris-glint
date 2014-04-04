package org.esa.beam.glint.operators;

import com.bc.jnn.JnnException;
import org.jfree.data.statistics.Regression;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Unit test for simple App.
 */
public class FlintOpTest {

    private FlintPreparation flintPreparationUnderTest;
    private FlintSolarPart37 flintSolarPart37UnderTest;
    private FlintGeometricalConversion flintGeometricalConversionUnderTest;

    @Before
    public void setUp() {
        flintPreparationUnderTest = new FlintPreparation();
        flintSolarPart37UnderTest = new FlintSolarPart37();
        flintGeometricalConversionUnderTest = new FlintGeometricalConversion();

        try {
            flintSolarPart37UnderTest.loadFlintAuxData();
            flintGeometricalConversionUnderTest.loadFlintAuxData();
        } catch (IOException e) {
            fail("Auxdata cloud not be loaded: " + e.getMessage());
        } catch (JnnException e) {
            fail("Neural net cloud not be loaded: " + e.getMessage());
        }
    }


    @Test
    public void testGetDayOfYear() {
        String date1 = "20070203";
        int dayOfYear1 = flintPreparationUnderTest.getDayOfYear(date1);

        assertEquals(34, dayOfYear1);

        String date2 = "20090630";
        int dayOfYear2 = flintPreparationUnderTest.getDayOfYear(date2);
        assertEquals(181, dayOfYear2);
    }

    @Test
    public void testComputeSolarIrradiance() {
        // test results according to FUB IDL breadboard results
        int dayOfYear = 371;
        float solarIrradiance = flintPreparationUnderTest.computeSolarIrradiance37(dayOfYear);
        // sufficient tolerance with regard to IDL due to simplified integration method
        assertEquals(11.736396f, solarIrradiance, 1.0e-1);

        dayOfYear = 164;
        solarIrradiance = flintPreparationUnderTest.computeSolarIrradiance37(dayOfYear);
        assertEquals(10.999949f, solarIrradiance, 1.0e-1);
    }

    @Test
    public void testQuadraticRegression() {

        // Flanagan library currently removed from project

//        // x data array
//        double[] xArray = {0.0,1.0,2.0,3.0,4.0,5.0,6.0};
//        // observed y data array - follows exactly y = 1 + 2x + 3x^2
//        double[] yArray1 = {1.0, 6.0, 17.0, 34.0, 57.0, 86.0, 121.0};
//
//        Regression reg1 = new Regression(xArray, yArray1);
//        reg1.polynomial(2);
//        double[] coeffArray1 = reg1.getBestEstimates();  // 3 coefficients: a + bx + cx^2
//
//        assertEquals(3, coeffArray1.length);
//        assertEquals(1.0, coeffArray1[0]);
//        assertEquals(2.0, coeffArray1[1]);
//        assertEquals(3.0, coeffArray1[2]);
//
//        // other observed y data array - still close to y = 1 + 2x + 3x^2
//        double[] yArray2 = {1.0, 7.0, 16.0, 35.0, 56.0, 87.0, 120.0};
//
//        Regression reg2 = new Regression(xArray, yArray2);
//        reg2.polynomial(2);
//        double[] coeffArray2 = reg2.getBestEstimates();
//
//        assertEquals(3, coeffArray2.length);
//        assertEquals(1.0, coeffArray2[0], 0.2);
//        assertEquals(2.0, coeffArray2[1], 0.2);
//        assertEquals(3.0, coeffArray2[2], 0.2);

    }

    @Test
    public void testPolyval() {

        // jamlab library currently removed from project

//        // x data array
//        double[] xArray = {0.0,1.0,2.0,3.0,4.0,5.0,6.0};
//        // observed y data array - follows exactly y = 1 + 2x + 3x^2
//        double[] yArray1 = {1.0, 6.0, 17.0, 34.0, 57.0, 86.0, 121.0};
//
//        Polyfit polyfit = null;
//        try {
//            polyfit = new Polyfit(xArray,yArray1,2);
//        } catch (Exception e) {
//            fail("Could not set up polynominal fit.");
//        }
//        Matrix coeffMatrix = polyfit.getPolyCoeffMatrix();
//
//        assertEquals(3, coeffMatrix.getColumnDimension());
//        assertEquals(1, coeffMatrix.getRowDimension());
//        assertEquals(3.0, coeffMatrix.get(0,0), 1.e-8);
//        assertEquals(2.0, coeffMatrix.get(0,1), 1.e-8);
//        assertEquals(1.0, coeffMatrix.get(0,2), 1.e-8);
//
//        // other observed y data array - still close to y = 1 + 2x + 3x^2
//        double[] yArray2 = {1.0, 7.0, 16.0, 35.0, 56.0, 87.0, 120.0};
//
//        try {
//            polyfit = new Polyfit(xArray,yArray2,2);
//        } catch (Exception e) {
//            fail("Could not set up polynominal fit.");
//        }
//        coeffMatrix = polyfit.getPolyCoeffMatrix();
//        assertEquals(3.0, coeffMatrix.get(0,0), 0.2);
//        assertEquals(2.0, coeffMatrix.get(0,1), 0.2);
//        assertEquals(1.0, coeffMatrix.get(0,2), 0.2);
//
//        // other observed y data array - follows exactly y = 1 + 2x
//        double[] yArray3 = {1.0, 3.0, 5.0, 7.0, 9.0, 11.0, 13.0};
//        try {
//            polyfit = new Polyfit(xArray,yArray3,1);
//        } catch (Exception e) {
//            fail("Could not set up polynominal fit.");
//        }
//        coeffMatrix = polyfit.getPolyCoeffMatrix();
//        assertEquals(2, coeffMatrix.getColumnDimension());
//        assertEquals(1, coeffMatrix.getRowDimension());
//        assertEquals(2.0, coeffMatrix.get(0,0), 0.2);
//        assertEquals(1.0, coeffMatrix.get(0,1), 0.2);
    }

    @Test
    public void testJFreeRegression() {

        // x data array
        double[] xArray = {0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        // observed y data array - follows exactly y = 1 + 2x
        double[] yArray = {1.0, 3.0, 5.0, 7.0, 9.0, 11.0, 13.0};

        double[][] regData = new double[7][2];
        for (int i = 0; i < 7; i++) {
            regData[i][0] = xArray[i];
            regData[i][1] = yArray[i];
        }

        double[] coeffs = Regression.getOLSRegression(regData);
        assertEquals(2, coeffs.length);
        assertEquals(1.0, coeffs[0], 1.0e-8);
        assertEquals(2.0, coeffs[1], 1.0e-8);
    }

    @Test
    public void testGetViewAzimuthCorrectionProfile() {
        double[] yArray = new double[20];

        for (int i = 0; i <= 6; i++) {
            // 10, 11, 12, 13, 14, 15, 16
            yArray[i] = 10.0 + i * 1.0;
        }
        yArray[7] = 15.0;
        yArray[8] = 14.0;
        yArray[9] = 13.0;
        yArray[10] = 12.0;
        yArray[11] = 11.0;
        yArray[12] = 10.0;
        yArray[13] = 9.0;

        for (int i = 14; i < 20; i++) {
            // 8, 9, 10, 11, 12, 13
            yArray[i] = 8.0 + (i - 14) * 1.0;
        }

        double[] result = flintPreparationUnderTest.getViewAzimuthCorrectionProfile(yArray);
        assertEquals(20, result.length);
        assertEquals(12.0, result[2], 1.0e-8);
        assertEquals(16.0, result[6], 1.0e-8);
        assertEquals(17.0, result[7], 1.0e-8);
        assertEquals(18.0, result[8], 1.0e-8);
        assertEquals(19.0, result[9], 1.0e-8);
        assertEquals(20.0, result[10], 1.0e-8);
        assertEquals(5.0, result[11], 1.0e-8);
        assertEquals(6.0, result[12], 1.0e-8);
        assertEquals(7.0, result[13], 1.0e-8);
        assertEquals(8.0, result[14], 1.0e-8);
        assertEquals(9.0, result[15], 1.0e-8);
        assertEquals(13.0, result[19], 1.0e-8);
    }

    @Test
    public void testConvertBT2Radiance() {
        float temp = 272.663f;
        float radiance = flintSolarPart37UnderTest.convertBT2Radiance(temp);
        assertEquals(0.125572f, radiance, 1.0e-6);

        temp = 272.839f;
        radiance = flintSolarPart37UnderTest.convertBT2Radiance(temp);
        assertEquals(0.126713f, radiance, 1.0e-6);

        temp = 273.015f;
        radiance = flintSolarPart37UnderTest.convertBT2Radiance(temp);
        assertEquals(0.127856f, radiance, 1.0e-6);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testIsUsefulPixel() {
        // land
        boolean aatsrCloudFlagNadirLand = true;
        // cloud free
        boolean aatsrCloudFlagNadirCloudy = false;
        // glint
        boolean aatsrCloudFlagNadirGlint = true;
        // aatsr view elev > 0.0
        float aatsrViewElevation = 5.0f;
        // aatsr BT37 > 270 K
        float aatsrBT37 = 280.0f;

        assertEquals(false, flintPreparationUnderTest.isUsefulPixel(
                aatsrCloudFlagNadirLand,
                aatsrCloudFlagNadirCloudy,
                aatsrCloudFlagNadirGlint, aatsrViewElevation, aatsrBT37));

        // ocean
        aatsrCloudFlagNadirLand = false;

        // cloud free
        aatsrCloudFlagNadirCloudy = false;
        // glint
        aatsrCloudFlagNadirGlint = true;
        // aatsr view elev > 0.0
        aatsrViewElevation = 5.0f;
        // aatsr BT37 > 270 K
        aatsrBT37 = 280.0f;

        assertEquals(true, flintPreparationUnderTest.isUsefulPixel(
                aatsrCloudFlagNadirLand,
                aatsrCloudFlagNadirCloudy,
                aatsrCloudFlagNadirGlint, aatsrViewElevation, aatsrBT37));

        // cloudy
        aatsrCloudFlagNadirCloudy = true;
        // glint
        aatsrCloudFlagNadirGlint = true;
        // aatsr view elev > 0.0
        aatsrViewElevation = 5.0f;
        // aatsr BT37 > 270 K
        aatsrBT37 = 280.0f;

        assertEquals(true, flintPreparationUnderTest.isUsefulPixel(
                aatsrCloudFlagNadirLand,
                aatsrCloudFlagNadirCloudy,
                aatsrCloudFlagNadirGlint, aatsrViewElevation, aatsrBT37));

        // cloud free
        aatsrCloudFlagNadirCloudy = false;
        // no glint
        aatsrCloudFlagNadirGlint = false;
        // aatsr view elev > 0.0
        aatsrViewElevation = 5.0f;
        // aatsr BT37 > 270 K
        aatsrBT37 = 280.0f;

        assertEquals(true, flintPreparationUnderTest.isUsefulPixel(
                aatsrCloudFlagNadirLand,
                aatsrCloudFlagNadirCloudy,
                aatsrCloudFlagNadirGlint, aatsrViewElevation, aatsrBT37));

        // cloud free
        aatsrCloudFlagNadirCloudy = false;
        // glint
        aatsrCloudFlagNadirGlint = true;
        // aatsr view elev > 0.0
        aatsrViewElevation = 5.0f;
        // aatsr BT37 <270 K
        aatsrBT37 = 260.0f;

        assertEquals(false, flintPreparationUnderTest.isUsefulPixel(
                aatsrCloudFlagNadirLand,
                aatsrCloudFlagNadirCloudy,
                aatsrCloudFlagNadirGlint, aatsrViewElevation, aatsrBT37));

        // cloudy
        aatsrCloudFlagNadirCloudy = true;
        // no glint
        aatsrCloudFlagNadirGlint = false;
        // aatsr view elev > 0.0
        aatsrViewElevation = 5.0f;
        // aatsr BT37 > 270 K
        aatsrBT37 = 280.0f;

        assertEquals(true, flintPreparationUnderTest.isUsefulPixel(
                aatsrCloudFlagNadirLand,
                aatsrCloudFlagNadirCloudy,
                aatsrCloudFlagNadirGlint, aatsrViewElevation, aatsrBT37));
    }

    @org.junit.Test
    public void testCalcTrans() {
        // test results according to FUB IDL breadboard results
        int channel = 37;
        float waterVapourColumn = 2.05317f;
        float aatsrSunElevation = 26.9514f;
        float aatsrViewElevation = 21.1147f;
        float transmission = flintSolarPart37UnderTest.computeTransmission(channel, waterVapourColumn,
                                                                           aatsrSunElevation, aatsrViewElevation);
        assertEquals(0.670916, transmission, 1.0e-4);

    }

    @org.junit.Test
    public void testWsToGauss() {
        // test results according to FUB IDL breadboard results
        double[] nnIn = new double[]{1.0, 1.37, 0.891719};
        double[] gaussPars = new double[4];
        flintGeometricalConversionUnderTest.applyNeuralNetWindspeed(nnIn, gaussPars);
        assertEquals(4, gaussPars.length);
        assertEquals(0.277409, gaussPars[0], 1.0e-5);
        assertEquals(0.113425, gaussPars[1], 1.0e-5);
        assertEquals(0.113615, gaussPars[2], 1.0e-5);
        assertEquals(0.450840, gaussPars[3], 1.0e-5);

        nnIn = new double[]{10.0133, 1.37, 0.891719};
        gaussPars = new double[4];
        flintGeometricalConversionUnderTest.applyNeuralNetWindspeed(nnIn, gaussPars);
        assertEquals(0.0426205, gaussPars[0], 1.0e-5);
        assertEquals(0.310001, gaussPars[1], 1.0e-5);
        assertEquals(0.347387, gaussPars[2], 1.0e-5);
        assertEquals(0.519014, gaussPars[3], 1.0e-5);

        nnIn = new double[]{14.0, 1.37, 0.891719};
        gaussPars = new double[4];
        flintGeometricalConversionUnderTest.applyNeuralNetWindspeed(nnIn, gaussPars);
        assertEquals(0.0320366, gaussPars[0], 1.0e-5);
        assertEquals(0.376013, gaussPars[1], 1.0e-5);
        assertEquals(0.431822, gaussPars[2], 1.0e-5);
        assertEquals(0.566908, gaussPars[3], 1.0e-5);

        nnIn = new double[]{14.0, 1.37, 0.925029};
        gaussPars = new double[4];
        flintGeometricalConversionUnderTest.applyNeuralNetWindspeed(nnIn, gaussPars);
        assertEquals(0.0299489, gaussPars[0], 1.0e-5);
        assertEquals(0.385847, gaussPars[1], 1.0e-5);
        assertEquals(0.423678, gaussPars[2], 1.0e-5);
        assertEquals(0.456022, gaussPars[3], 1.0e-5);
    }

    @org.junit.Test
    public void testGauss2DRecall() {
        float merisViewZenith = 19.6901f;
        float aatsrAzimuthDiff = 143.561f;
        double[] gaussPars = new double[]{0.277409, 0.113425, 0.113615, 0.450840};
        float result = flintGeometricalConversionUnderTest.applyGauss2DRecall(merisViewZenith, aatsrAzimuthDiff, gaussPars);
        assertEquals(1.0077E-10, result, 1.0e-5);

        gaussPars = new double[]{0.0426205, 0.310001, 0.347387, 0.519014};
        result = flintGeometricalConversionUnderTest.applyGauss2DRecall(merisViewZenith, aatsrAzimuthDiff, gaussPars);
        assertEquals(0.00260562, result, 1.0e-5);


        merisViewZenith = 9.43384f;
        aatsrAzimuthDiff = 4.09227f;
        gaussPars = new double[]{0.172165, 0.145542, 0.147719, 0.397425};
        result = flintGeometricalConversionUnderTest.applyGauss2DRecall(merisViewZenith, aatsrAzimuthDiff, gaussPars);
        assertEquals(0.0489722, result, 1.0e-5);

        merisViewZenith = 8.10340f;
        aatsrAzimuthDiff = 4.19373f;
        gaussPars = new double[]{0.172116, 0.145572, 0.147745, 0.396997};
        result = flintGeometricalConversionUnderTest.applyGauss2DRecall(merisViewZenith, aatsrAzimuthDiff, gaussPars);
        assertEquals(0.0380782, result, 1.0e-5);
    }

}
