package org.esa.beam.glint.operators;

import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.glint.util.GlintHelpers;
import org.esa.beam.util.logging.BeamLogManager;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provides the computation of the AATSR solar part at 3.7um by using the thermal radiance
 * at AATSR 11 and 12um.
 * (FUB MERIS/AATSR Synergy ATBD (Draft), Ch. 1)
 *
 * @author Olaf Danne
 * @version $Revision: 5451 $ $Date: 2009-06-05 18:36:49 +0200 (Fr, 05 Jun 2009) $
 */
public class FlintSolarPart37 {

    private float[][] aCoeff37;
    private float[][] hCoeff37;
    private float[] hWeight37;
    private float[][] aCoeff16;
    private float[][] hCoeff16;
    private float[] hWeight16;

    private Logger logger;
    private double[] tempFromTable;
    private double[] radianceFromTable;



    public FlintSolarPart37() {
        logger = BeamLogManager.getSystemLogger();

        try {
            tempFromTable = FlintAuxData.getInstance().createTemp2RadianceTable().getTemp();
            radianceFromTable = FlintAuxData.getInstance().createTemp2RadianceTable().getRad();
        } catch (IOException e) {
             throw new OperatorException("Failed to read BT to radiance conversion table:\n" + e.getMessage(), e);
        }
    }

    //
    // This method loads required Flint Auxdata
    //
    protected void loadFlintAuxData() throws IOException {
        aCoeff37 = FlintAuxData.getInstance().readWaterVapourCoefficients(37, "A");
        hCoeff37 = FlintAuxData.getInstance().readWaterVapourCoefficients(37, "H");
        aCoeff16 = FlintAuxData.getInstance().readWaterVapourCoefficients(16, "A");
        hCoeff16 = FlintAuxData.getInstance().readWaterVapourCoefficients(16, "H");

        hWeight37 = FlintAuxData.getInstance().readTransmissionWeights(37, "H");
        hWeight16 = FlintAuxData.getInstance().readTransmissionWeights(16, "H");
    }

    //
    // This method computes the thermal part of radiance in 3.7um channel (breadboard step 1.a)
    //
    protected float extrapolateTo37(float aa11, float aa12) {
        final float[] par = new float[] {4.91348f, 0.978489f, 1.37919f};

        return ( par[0] + par[1]*aa11 + par[2]*(aa11-aa12) );
    }


    
    //
    //  This method computes the transmission in 3.7um (and 1.6um) channel..
    // Computation by wieghted sum of k-terms.
    // (breadboard step 1.b.2)
    //
    protected float computeTransmission(int channel, float waterVapourColumn,
                                     float aatsrSunElevation, float aatsrViewElevation) {
        float transmission = 1.0f;

        double am = 1.0/Math.cos(Math.toRadians(aatsrSunElevation)) + 1.0/Math.cos(Math.toRadians(aatsrViewElevation));

        if (channel == 37) {
            final int numLayers = aCoeff37[0].length;
            final int numSpectralIntervals = aCoeff37.length;
            double weightedIntegral = 0.0;
            for (int i=0; i<numSpectralIntervals; i++) {
                double layerIntegral = 0.0f;
                for (int j=0; j<numLayers; j++) {
                    double sumCoeffsWv = aCoeff37[i][j] + hCoeff37[i][j] * waterVapourColumn / 2.7872;
                    layerIntegral += sumCoeffsWv;
                }
                weightedIntegral += hWeight37[i] * Math.exp(-am * layerIntegral);
            }
            transmission = (float) weightedIntegral;
        } else if (channel == 16) {
            final int numLayers = aCoeff16[0].length;
            final int numSpectralIntervals = aCoeff16.length;
            double weightedIntegral = 0.0f;
            for (int i=0; i<numSpectralIntervals; i++) {
                double layerIntegral = 0.0f;
                for (int j=0; j<numLayers; j++) {
                    double sumCoeffsWv = aCoeff16[i][j] + hCoeff16[i][j] * waterVapourColumn / 2.7872;
                    layerIntegral += sumCoeffsWv;
                }
                weightedIntegral += hWeight16[i] * Math.exp(-am * layerIntegral);
            }
            transmission = (float) weightedIntegral;
        }  else {
            logger.log(Level.ALL,
                        "Wrong channel " + channel + " provided to 'computeTransmission' - transmission kept to zero.");
        }
        return transmission;
    }

    //
    //  This method converts the units of 3.7um from BT(K) to real normalized radiance units (1/sr).
    // Computation by interpolation.
    // (breadboard step 1.c)
    //
    protected float convertBT2Radiance(float brightnessTemp) {
        float radiance = 0.0f;

        final int index = FlintAuxData.getInstance().getNearestTemp2RadianceTableIndex(brightnessTemp, tempFromTable);
        if (index >= 0 && index < radianceFromTable.length-1) {
            radiance = (float) GlintHelpers.linearInterpol(brightnessTemp, tempFromTable[index],
                                                           tempFromTable[index + 1], radianceFromTable[index], radianceFromTable[index + 1]);
        }

        return radiance;
    }

    //
    // This method converts the difference between the measured and the thermal part, which is the solar part at TOA.
    // Correction for transmission --> specular reflection
    // (breadboard step 1.d)
    //
    protected float computeSolarPart(float aatsrRad37, float aatsrThermalPart37, float aatsrTrans37) {
        return ( (aatsrRad37 - aatsrThermalPart37)/aatsrTrans37 ); // in 1/sr
    }

    //
    // This method converts the solar part at TOA to 'AATSR' units
    // (breadboard step 1.d)
    //
    protected float convertToAatsrUnits(float solarPart, float aatsrSunElevation) {
        return ( (float) (solarPart*Math.PI*100.0/
                Math.cos(Math.toRadians(90.0-aatsrSunElevation))) );
    }

    //
    // This method computes a simple additional cloud mask.
    // The threshold of 0.79 may be changed.
    // (breadboard step 1.e)
    //
    protected boolean computeAdditionalCloudMask(float solarPartAatsrUnits, float specularReflAatsrUnits) {
        double specularPartAatsrUnits = specularReflAatsrUnits / 0.79;
        return ( (Math.abs(specularPartAatsrUnits - solarPartAatsrUnits) < 2.0) );
    }

}
