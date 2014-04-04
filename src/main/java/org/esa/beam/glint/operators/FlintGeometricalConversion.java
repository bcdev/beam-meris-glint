package org.esa.beam.glint.operators;

import com.bc.jnn.JnnException;
import com.bc.jnn.JnnNet;
import org.esa.beam.glint.util.GlintHelpers;

import java.io.IOException;

/**
 * This class provides the computation of the geometrical conversion of the specular reflection from
 * AATSR observation geometry to MERIS observation geometry.
 * (FUB MERIS/AATSR Synergy ATBD (Draft), Ch. 2)
 *
 * @author Olaf Danne
 * @version $Revision: 5451 $ $Date: 2009-06-05 18:36:49 +0200 (Fr, 05 Jun 2009) $
 */
public class FlintGeometricalConversion implements Cloneable {

    public static final double refractiveIndexReal037 = 1.37;
    public static final double refractiveIndexReal088 = 1.33;

    private JnnNet neuralNetWindspeed;

    @Override
    protected FlintGeometricalConversion clone()  {
        FlintGeometricalConversion conversion = new FlintGeometricalConversion();
        conversion.neuralNetWindspeed = neuralNetWindspeed.clone();
        return conversion;
    }

    //
    // This method loads required Flint Auxdata
    //
    protected void loadFlintAuxData() throws IOException, JnnException {
        neuralNetWindspeed = FlintAuxData.getInstance().loadNeuralNet(FlintAuxData.NEURAL_NET_WINDSPEED_FILE_NAME);
    }

    protected float applyGauss2DRecall(float merisViewZenith, float aatsrAzimuthDifference, double[] gaussPars) {
        return gauss2DRecall(merisViewZenith, aatsrAzimuthDifference, gaussPars);
    }

    protected void applyNeuralNetWindspeed(double[] nnIn, double[] gaussPars) {
        neuralNetWindspeed.process(nnIn, gaussPars);
    }

    
    //
    // This method provides the final result (datapair [windspeed, MERIS normalized radiance])
    // after ambiguity reduction (ECMWF wind method)
    // (breadboard step 2.b.1)
    //
    protected static float[] getAmbiguityReducedRadiance(float[][] merisNormalizedRadianceResult,
                                                  float zonalWind, float meridionalWind) {
        float[] result = new float[]{-1.0f, -1.0f};

        double windSpeed = Math.sqrt(zonalWind*zonalWind + meridionalWind*meridionalWind);

        final double wsDiff1 = Math.abs(merisNormalizedRadianceResult[0][0] - windSpeed);
        final double wsDiff2 = Math.abs(merisNormalizedRadianceResult[1][0] - windSpeed);

        if (wsDiff1 > wsDiff2 || merisNormalizedRadianceResult[0][0] == -1.0f) {
            result[0] = merisNormalizedRadianceResult[1][0];
            result[1] = merisNormalizedRadianceResult[1][1];
        } else {
            result[0] = merisNormalizedRadianceResult[0][0];
            result[1] = merisNormalizedRadianceResult[0][1];
        }
        return result;
    }

    //
    // This method indicates if a windspeed/radiance result was found from the LUT approach
    //
    protected static int windspeedFound(float[][] radianceResult) {
        int numberWindspeedsFound = 0;
        
        if (radianceResult[0][0] != -1.0f || radianceResult[1][0] != -1.0f) {
            if (radianceResult[0][0] != -1.0f && radianceResult[1][0] != -1.0f) {
                numberWindspeedsFound = 2;
            }  else {
                numberWindspeedsFound = 1;
            }
        }
        return numberWindspeedsFound;
    }

    //
    // This method does all the steps of the geometrical conversion.
    // A 2x2 array of Meris of effective windspeeds and corresponding normalised radiances is returned:
    //      |ws0 rad0|
    //      |ws1 rad1|
    //
    //      - in the 'normal' case, the first element contains the required result
    //      - in the 'ambiguous' case, both elements are filled, and ambiguity must be removed
    //
    // (breadboard step 2.a)
    //
    protected float[][] convertAatsrRad37ToMerisRad(float aatsrRad, float merisSunZenith, float merisViewZenith,
                                                float aatsrAzimuthDifference, float merisAzimuthDifference) {

        float[][] merisNormalizedRadianceResult = new float[][]{{-1.0f, -1.0f}, {-1.0f, -1.0f}};

        final double[][] normalizedRadianceLUT = createNormalizedRadianceLUT(merisSunZenith, merisViewZenith,
                aatsrAzimuthDifference);

        final double maximumAcceptableDiff = getMaximumAcceptableRadianceDiffInLUT(normalizedRadianceLUT[1]);

        final int maximumNormalizedRadianceIndex = GlintHelpers.getMaximumValueIndexInDoubleArray(normalizedRadianceLUT[1]);

        if (maximumNormalizedRadianceIndex > 0 && maximumNormalizedRadianceIndex < normalizedRadianceLUT[1].length-1) {
            // two LUT solutions possible
            merisNormalizedRadianceResult[0] = getRadianceFromLUT(normalizedRadianceLUT, 0, maximumNormalizedRadianceIndex-1,
                    aatsrRad, merisSunZenith, maximumAcceptableDiff, merisViewZenith, merisAzimuthDifference);

            merisNormalizedRadianceResult[1] = getRadianceFromLUT(normalizedRadianceLUT, maximumNormalizedRadianceIndex,
                    normalizedRadianceLUT[0].length-1,
                    aatsrRad, merisSunZenith, maximumAcceptableDiff, merisViewZenith, merisAzimuthDifference);
        } else {
            // monotone
            final int lutLength = normalizedRadianceLUT[1].length;
            merisNormalizedRadianceResult[0] = getRadianceFromLUT(normalizedRadianceLUT, 0, lutLength-1,
                    aatsrRad, merisSunZenith, maximumAcceptableDiff, merisViewZenith, aatsrAzimuthDifference);
        }

        return merisNormalizedRadianceResult;
    }

    private float[] getRadianceFromLUT(double[][] lut, int startIndex, int endIndex, float aatsrRad,
                                      float merisSunZenith, double maximumAcceptableDiff,
                                      float merisViewZenith, float merisAzimuthDifference) {

        float[] radianceResult = new float[]{-1.0f, -1.0f};

        final int lutLength = endIndex - startIndex + 1;
        double[] radianceDiffs = new double[lutLength];
        for (int i=startIndex; i<=endIndex; i++) {
            radianceDiffs[i-startIndex] = Math.abs(lut[1][i] - aatsrRad);
        }
        final int minRadianceDiffIndexInLUT = GlintHelpers.getMinimumValueIndexInDoubleArray(radianceDiffs);

        final double minRadianceDiffInLUT = GlintHelpers.getMinimumValueInDoubleArray(radianceDiffs);
        final double windspeed = lut[0][startIndex+minRadianceDiffIndexInLUT];

        final double[] nnIn = new double[3];
        final double[] gaussPars = new double[4];

        // apply FUB NN...
        nnIn[0] = windspeed;
        nnIn[1] = refractiveIndexReal088;
        nnIn[2] = Math.cos(Math.toRadians(merisSunZenith));  // angle in degree!

        if (minRadianceDiffInLUT <= maximumAcceptableDiff) {
            radianceResult[0] = (float) windspeed;
            applyNeuralNetWindspeed(nnIn, gaussPars);
            radianceResult[1] = applyGauss2DRecall(merisViewZenith, merisAzimuthDifference, gaussPars);
        }

        return radianceResult;
    }


    //
    // This method generates a 1D LUT of AATSR normalized radiances for different wind speeds
    // (breadboard step 2.a.1)
    //
    private double[][] createNormalizedRadianceLUT(float merisSunZenith, float merisViewZenith,
                                                     float aatsrAzimuthDifference) {

        final int numberOfWindspeeds = 151;

        double[][] lookupTable = new double[2][numberOfWindspeeds];

        double[] windspeed = new double[numberOfWindspeeds];
        double[] aatsrReflectanceSimulated = new double[numberOfWindspeeds];

        final double[] nnIn = new double[3];
        final double[] gaussPars = new double[4];

        for (int i = 0; i < numberOfWindspeeds; i++) {
            windspeed[i] = i * 13.0 / (numberOfWindspeeds - 1) + 1.0;

            // apply FUB NN...
            nnIn[0] = windspeed[i];
            nnIn[1] = refractiveIndexReal037;
            nnIn[2] = Math.cos(Math.toRadians(merisSunZenith));  // angle in degree!

            applyNeuralNetWindspeed(nnIn, gaussPars);

            aatsrReflectanceSimulated[i] = gauss2DRecall(merisViewZenith, aatsrAzimuthDifference, gaussPars);
            lookupTable[0][i] = windspeed[i];
            lookupTable[1][i] = aatsrReflectanceSimulated[i];
        }

        return lookupTable;
    }

    //
    // This method provides a maximum acceptable distance for windspeed/radiance LUT
    // (breadboard step 2.a.2)
    //
    private double getMaximumAcceptableRadianceDiffInLUT(double[] lutRadiance) {
        double diffAcceptable = 0.0;
        for (int i = 0; i < lutRadiance.length - 1; i++) {
            final double diff = Math.abs(lutRadiance[i] - lutRadiance[i + 1]);
            if (diff > diffAcceptable) {
                diffAcceptable = diff;
            }
        }

        return diffAcceptable;
    }

    private float gauss2DRecall(float merisViewZenith, float aatsrAzimuthDifference, double[] gaussPars) {
        float normalizedRadiance;

        final double x = Math.cos(Math.toRadians(90.0 - merisViewZenith)) *
                Math.sin(Math.toRadians(aatsrAzimuthDifference));
        final double y = Math.cos(Math.toRadians(90.0 - merisViewZenith)) *
                Math.cos(Math.toRadians(aatsrAzimuthDifference));

        final double yy = y - gaussPars[3];
        final double u = x * x / (gaussPars[1] * gaussPars[1]) + yy * yy / (gaussPars[2] * gaussPars[2]);

        normalizedRadiance = (float) (gaussPars[0] * Math.exp(-u / 2.0));

        return normalizedRadiance;
    }
}
