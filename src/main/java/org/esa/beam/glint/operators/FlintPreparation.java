package org.esa.beam.glint.operators;

import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.glint.util.GlintHelpers;
import org.jfree.data.statistics.Regression;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.Calendar;

/**
 * This class provides the preparation steps for the FLINT processor
 *
 * @author Olaf Danne
 * @version $Revision: 7907 $ $Date: 2010-01-12 11:59:36 +0100 (Di, 12 Jan 2010) $
 */
public class FlintPreparation {
    private double[] wlSpectralResponse;
    private double[] spectralResponse;
    private double[] sox;
    private double[] soy;

    public FlintPreparation() {
        try {
            wlSpectralResponse = FlintAuxData.getInstance().createAatsrSpectralResponse37Table().getWavelength();
            spectralResponse = FlintAuxData.getInstance().createAatsrSpectralResponse37Table().getResponse();
        } catch (IOException e) {
            throw new OperatorException("Failed to read spectral response table:\n" + e.getMessage(), e);
        }

        try {
            sox = FlintAuxData.getInstance().createCahalanTable().getX();
            soy = FlintAuxData.getInstance().createCahalanTable().getY();
        } catch (IOException e) {
            throw new OperatorException("Failed to read Cahalan table:\n" + e.getMessage(), e);
        }
    }

    protected int getDayOfYear(String yyyymmdd) {
        Calendar cal = Calendar.getInstance();
        int doy = -1;
        try {
            final int year = Integer.parseInt(yyyymmdd.substring(0, 4));
            final int month = Integer.parseInt(yyyymmdd.substring(4, 6)) - 1;
            final int day = Integer.parseInt(yyyymmdd.substring(6, 8));
            cal.set(year, month, day);
            doy = cal.get(Calendar.DAY_OF_YEAR);
        } catch (StringIndexOutOfBoundsException e) {
            e.printStackTrace();
        }  catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return doy;
    }

    //
    //  This method computes the solar irradiance in 3.7um channel.
    // Computation by interpolation and integration.
    //
    protected float computeSolarIrradiance37(int dayOfYear) {
        float solarIrradiance37;

        double normFactor = 0.0d;
        for (int i=0; i< spectralResponse.length-1; i+=2) {
            final double h = wlSpectralResponse[i+2] - wlSpectralResponse[i];
            normFactor += FlintAuxData.getInstance().getSimpsonIntegral(spectralResponse[i], spectralResponse[i+1], spectralResponse[i+2], h);
        }

        double ra = 0.0d;
        for (int i=0; i< spectralResponse.length-1; i+=2) {
            final int index = FlintAuxData.getInstance().getNearestCahalanTableIndex(wlSpectralResponse[i]*1000.0, sox);
            final double soi = GlintHelpers.linearInterpol(wlSpectralResponse[i], sox[index] / 1000.0, sox[index + 1] / 1000.0, soy[index],
                                                           soy[index + 1]);
            final double h = wlSpectralResponse[i+2] - wlSpectralResponse[i];
            ra += FlintAuxData.getInstance().getSimpsonIntegral(spectralResponse[i]*soi, spectralResponse[i+1]*soi, spectralResponse[i+2]*soi, h);
        }
        ra /= normFactor;
        final double rsun = 1.0 - 0.01673*Math.cos(Math.toRadians(0.9856*((float)dayOfYear - 2.0)));
        solarIrradiance37 = (float) (ra*10.0/(rsun*rsun));

        return solarIrradiance37;
    }

    protected float removeAzimuthDifferenceAmbiguity(float viewAzimuth, float sunAzimuth) {
        float correctedViewAzimuth = viewAzimuth;
        float correctedSunAzimuth = sunAzimuth;

        // first correct for angles < 0.0
        if (correctedViewAzimuth < 0.0) {
            correctedViewAzimuth += 360.0;
        }
        if (correctedSunAzimuth < 0.0) {
            correctedSunAzimuth += 360.0;
        }

        // now correct difference ambiguities
        float correctedAzimuthDifference = correctedViewAzimuth - correctedSunAzimuth;
        if (correctedAzimuthDifference > 180.0) {
            correctedAzimuthDifference = 360.0f - correctedAzimuthDifference;
        }
        if (correctedAzimuthDifference < 0.0) {
            correctedAzimuthDifference = -1.0f* correctedAzimuthDifference;
        }
        return correctedAzimuthDifference;
    }

    //
    // This method limits the processing to pixels which are:
    //      - cloud free or glint-effected (this is how the AATSR cloud mask is organized)
    //      - inside AATSR FOV
    //      - not saturated at 3.7um, no ice, no cloud.
    //
    protected boolean isUsefulPixel(boolean aatsrCloudFlagNadirLand,
                                    boolean aatsrCloudFlagNadirCloudy,
                                    boolean aatsrCloudFlagNadirGlint,
                                    float aatsrViewElevation, float aatsrBT37) {
        return ( !aatsrCloudFlagNadirLand &&
//                          (!aatsrCloudFlagNadirCloudy || aatsrCloudFlagNadirGlint) &&
                          (aatsrViewElevation > 0.0) && (aatsrBT37 > 270.0) );
    }

    /**
     *
     * This method reestablishes the viewing azimuth discontinuity at nadir.
     * Computation by first order polynominal fit on 'good' pixel left and right
     * of sub-satellite point.
     * A method like this should be integrated in BEAM later.
     * Discuss other choices of fitting (second order as in breadboard?)
     *
     * @param viewAzimuthRaster  - va input tile
     * @param rect - underlying rectangle
     */
    public void correctViewAzimuthLinear(Tile viewAzimuthRaster, Rectangle rect) {

        double[] yArray;
        for (int y=0; y<rect.height; y++) {
           int startIndex = 0;
           int endIndex = rect.width-1;

           //
           for (int x=1; x<rect.width; x++) {
               if (viewAzimuthRaster.getSampleDouble(x, y) != 0.0 &&
                   viewAzimuthRaster.getSampleDouble(x-1, y) == 0.0) {
                   startIndex = x;
                   break;
               }
           }

           for (int x=0; x<rect.width-1; x++) {
               if (viewAzimuthRaster.getSampleDouble(x, y) != 0.0 &&
                   viewAzimuthRaster.getSampleDouble(x+1, y) == 0.0) {
                   endIndex = x;
                   break;
               }
           }

           int arrayLength = endIndex - startIndex + 1;

           if (startIndex < endIndex)  {
               // if not, no correction is needed
               yArray = new double[arrayLength];

               for (int x=startIndex; x<=endIndex; x++) {
                    yArray[x-startIndex] = viewAzimuthRaster.getSampleDouble(x, y);
               }

               final double minValue = GlintHelpers.getMinimumValueInDoubleArray(yArray);
               final double maxValue = GlintHelpers.getMinimumValueInDoubleArray(yArray);

               if (minValue != 0.0 || maxValue != 0.0) {
                   double[] correctedResult = getViewAzimuthCorrectionProfile(yArray);
                   for (int x=startIndex; x<endIndex; x++) {
                       viewAzimuthRaster.setSample(x, y, correctedResult[x-startIndex]);
                   }
               }
           }
        }
    }

    //
    // This method provides a corrected view azmiuth profile
    // (currently with simple linear regression)
    protected double[] getViewAzimuthCorrectionProfile(double[] yArray) {
        double[] result = new double[yArray.length];

        // get left side of discontinuity interpolation (kind of 'second derivative'...)
        final int discontLeftIndex = getDiscontinuityInterpolationLeftSide(yArray);
        // get right side of discontinuity interpolation
        final int discontRightIndex = getDiscontinuityInterpolationRightSide(yArray);

        final int discontIndex = (discontLeftIndex + discontRightIndex)/2;

        double[][] leftPart = new double[discontLeftIndex+1][2];
        double[][] rightPart = new double[yArray.length-discontRightIndex][2];

        final int leftPartLength = Math.min(discontLeftIndex, yArray.length - 1);
        for (int x=0; x<= leftPartLength; x++) {
            leftPart[x][0] =  x*1.0;
            leftPart[x][1] =  yArray[x];
        }
        for (int x=discontRightIndex; x<yArray.length; x++) {
            rightPart[x-discontRightIndex][0] = x*1.0;
            rightPart[x-discontRightIndex][1] = yArray[x];
        }

        if (leftPart[0].length < 2 || rightPart[0].length < 2) {
            // no regression possible
            return yArray;
        } else {
            double[] leftCoeffs = Regression.getOLSRegression(leftPart);
            for (int x=0; x<=leftPartLength; x++) {
                 result[x] = yArray[x];
            }
            for (int x=discontLeftIndex; x<=discontIndex; x++) {
                 result[x] = leftCoeffs[0] + leftCoeffs[1]*x;
            }

            double[] rightCoeffs = Regression.getOLSRegression(rightPart);
            for (int x=discontIndex+1; x<discontRightIndex; x++) {
                result[x] = rightCoeffs[0] + rightCoeffs[1]*x;
            }
            System.arraycopy(yArray, discontRightIndex, result, discontRightIndex,
                             yArray.length - discontRightIndex);

            return result;
        }
    }

//    protected double[] getViewAzimuthCorrectionProfileOld(double[] yArray) {
//        double[] result = new double[yArray.length];
//
//        // get left side of discontinuity interpolation (kind of 'second derivative'...)
//        final int discontLeftIndex = getDiscontinuityInterpolationLeftSide(yArray);
//        // get right side of discontinuity interpolation
//        final int discontRightIndex = getDiscontinuityInterpolationRightSide(yArray);
//
//        final int discontIndex = (discontLeftIndex + discontRightIndex)/2;
//
//        double[] xLeftPart = new double[discontLeftIndex+1];
//        double[] yLeftPart = new double[discontLeftIndex+1];
//        double[] xRightPart = new double[yArray.length-discontRightIndex];
//        double[] yRightPart = new double[yArray.length-discontRightIndex];
//
//        final int leftPartLength = Math.min(discontLeftIndex, yArray.length - 1);
//        for (int x=0; x<= leftPartLength; x++) {
//            xLeftPart[x] = x*1.0;
//            yLeftPart[x] = yArray[x];
//        }
//        for (int x=discontRightIndex; x<yArray.length; x++) {
//            xRightPart[x-discontRightIndex] = x*1.0;
//            yRightPart[x-discontRightIndex] = yArray[x];
//        }
//
//        if (xLeftPart.length < 2 || xRightPart.length < 2) {
//            // no regression possible
//            return yArray;
//        } else {
////            Regression reg1 = new Regression(xLeftPart, yLeftPart);
////            reg1.polynomial(1);
//    //        reg1.polynomial(2);
////            double[] coeffArray1 = reg1.getBestEstimates();  // 2 coefficients: a + bx
//
//            Polyfit polyfit = null;
//            try {
//                polyfit = new Polyfit(xLeftPart,yLeftPart,1);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            Matrix coeffMatrix = polyfit.getPolyCoeffMatrix();
//
//            for (int x=0; x<=leftPartLength; x++) {
//                 result[x] = yArray[x];
//            }
//            for (int x=discontLeftIndex; x<=discontIndex; x++) {
////                 result[x] = coeffArray1[0] + coeffArray1[1]*x;
//                 result[x] = coeffMatrix.get(0,1) + coeffMatrix.get(0,0)*x;
//    //             result[x] = coeffArray1[0] + coeffArray1[1]*x + coeffArray1[2]*x*x;
//            }
//
////            Regression reg2 = new Regression(xRightPart, yRightPart);
////            reg2.polynomial(1);
//    //        reg2.polynomial(2);
////            double[] coeffArray2 = reg2.getBestEstimates();  // 2 coefficients: a + bx
//
//            try {
//                polyfit = new Polyfit(xRightPart,yRightPart,1);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            coeffMatrix = polyfit.getPolyCoeffMatrix();
//
//            for (int x=discontIndex+1; x<discontRightIndex; x++) {
////                 result[x] = coeffArray2[0] + coeffArray2[1]*x;
//                result[x] = coeffMatrix.get(0,1) + coeffMatrix.get(0,0)*x;
//    //             result[x] = coeffArray2[0] + coeffArray2[1]*x + coeffArray2[2]*x*x;
//            }
////            for (int x=discontRightIndex; x<yArray.length; x++) {
////                 result[x] = yArray[x];
////            }
//            System.arraycopy(yArray, discontRightIndex, result, discontRightIndex,
//                             yArray.length - discontRightIndex);
//
//            return result;
//        }
//    }


    int getDiscontinuityInterpolationRightSide(double[] yArray) {
        int discontRightIndex = 0;
        for (int i=yArray.length-3; i>=2; i--) {
            double yArrayDiffQuot = (yArray[i+2] - yArray[i]) / (yArray[i] - yArray[i-2]);
            if (yArrayDiffQuot < 0.1 || yArrayDiffQuot > 10.0) {
                 discontRightIndex = i;
                 break;
            }
        }
        return discontRightIndex;
    }

    int getDiscontinuityInterpolationLeftSide(double[] yArray) {
        int discontLeftIndex = yArray.length;
        for (int i=2; i<yArray.length-2; i++) {
            final double yArrayDiffQuot = (yArray[i+2] - yArray[i]) / (yArray[i] - yArray[i-2]);
            if (yArrayDiffQuot < 0.1 || yArrayDiffQuot > 10.0) {
                discontLeftIndex = i;
                break;
            }
        }
        return discontLeftIndex;
    }


//    protected QuadraticRegressionPolynom createQuadraticRegressionPolynom() {
//        return new QuadraticRegressionPolynom();
//    }
//
//    protected LinearRegressionPolynom createLinearRegressionPolynom() {
//        return new LinearRegressionPolynom();
//    }
//
//    /**
//     *  Class to evaluate the function y = a + bx + cx^2
//     */
//    class QuadraticRegressionPolynom implements RegressionFunction {
//
//         public double function(double[ ] p, double[ ] x){
//                  double y = p[0] + p[1]*x[0] + p[2]*x[0]*x[0];
//                  return y;
//         }
//    }
//
//    /**
//     *  Class to evaluate the function y = a + bx
//     */
//    class LinearRegressionPolynom implements RegressionFunction {
//
//         public double function(double[ ] p, double[ ] x){
//                  double y = p[0] + p[1]*x[0] + p[2]*x[0]*x[0];
//                  return y;
//         }
//    }
}
