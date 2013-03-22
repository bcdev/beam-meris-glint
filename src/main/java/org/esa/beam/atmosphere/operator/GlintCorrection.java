package org.esa.beam.atmosphere.operator;

import org.esa.beam.PixelData;
import org.esa.beam.meris.radiometry.smilecorr.SmileCorrectionAuxdata;
import org.esa.beam.nn.NNffbpAlphaTabFast;
import org.esa.beam.nn.util.NeuralNetIOConverter;

import java.util.Arrays;

/**
 * Class providing the AGC Glint correction.
 */
public class GlintCorrection extends AbstractGlintCorrection {

    /**
     * @param atmosphereNet    the neural net for atmospheric correction
     * @param smileAuxdata     can be {@code null} if SMILE correction shall not be performed
     * @param normalizationNet can be {@code null} if normalization shall not be performed
     * @param outputReflecAs   output as radiance or irradiance reflectances
     */
    public GlintCorrection(NNffbpAlphaTabFast atmosphereNet,
                           NNffbpAlphaTabFast invAotAngNet,
                           SmileCorrectionAuxdata smileAuxdata,
                           NNffbpAlphaTabFast normalizationNet,
                           NNffbpAlphaTabFast autoAssocNet, ReflectanceEnum outputReflecAs) {
        this.atmosphereNet = atmosphereNet;
        this.invAotAngNet = invAotAngNet;
        this.smileAuxdata = smileAuxdata;
        this.normalizationNet = normalizationNet;
        this.autoAssocNet = autoAssocNet;
        this.outputReflecAs = outputReflecAs;
    }

    /**
     * This method performa the Glint correction, using new AC net (March 2012).
     *
     * @param pixel            the pixel input data
     * @param deriveRwFromPath whether to derive the water leaving reflectance from path or not
     * @param temperature      the water temperature
     * @param salinity         the water salinity
     * @return GlintResult
     */
    public GlintResult perform(PixelData pixel, boolean deriveRwFromPath, double temperature, double salinity, double tosaOosThresh) {

        double tetaViewSurfDeg = pixel.satzen; /* viewing zenith angle */
        tetaViewSurfDeg = correctViewAngle(tetaViewSurfDeg, pixel.pixelX, pixel.nadirColumnIndex,
                                           pixel.isFullResolution);
        final double tetaViewSurfRad = Math.toRadians(tetaViewSurfDeg);
        final double tetaSunSurfDeg = pixel.solzen; /* sun zenith angle */
        final double tetaSunSurfRad = Math.toRadians(tetaSunSurfDeg);
        final double aziDiffSurfDeg = getAzimuthDifference(pixel);
        final double aziDiffSurfRad = Math.toRadians(aziDiffSurfDeg);

        double[] xyz = computeXYZCoordinates(tetaViewSurfRad, aziDiffSurfRad);

        final GlintResult glintResult = new GlintResult();

        if (isLand(pixel)) {
            glintResult.raiseFlag(LAND);
        }

        if (isCloudIce(pixel)) {
            glintResult.raiseFlag(CLOUD_ICE);
        }

        if (isRlToaOor(pixel)) {
            glintResult.raiseFlag(TOA_OOR);
        }

        if ((glintResult.getFlag() & LAND) == LAND || (glintResult.getFlag() & CLOUD_ICE) == CLOUD_ICE ||
                (pixel.l1Flag & L1_INVALID_FLAG) == L1_INVALID_FLAG) {
            glintResult.raiseFlag(INPUT_INVALID);
            return glintResult;
        }

        Tosa tosa = new Tosa(smileAuxdata);
        tosa.init();
        final double[] rlTosa = tosa.perform(pixel, tetaViewSurfRad, tetaSunSurfRad);
        glintResult.setTosaReflec(rlTosa.clone());

        /* test if tosa reflectances are out of training range */
        if (!isTosaReflectanceValid(rlTosa, atmosphereNet, false)) {
            glintResult.raiseFlag(TOSA_OOR);
        }

        int invAotAngNetInputIndex = 0;
        double[] invAotAngNetInput = new double[invAotAngNet.getInmin().length];
        invAotAngNetInput[invAotAngNetInputIndex++] = tetaSunSurfDeg;
        invAotAngNetInput[invAotAngNetInputIndex++] = xyz[0];
        invAotAngNetInput[invAotAngNetInputIndex++] = xyz[1];
        invAotAngNetInput[invAotAngNetInputIndex++] = xyz[2];
        invAotAngNetInput[invAotAngNetInputIndex++] = temperature;
        invAotAngNetInput[invAotAngNetInputIndex++] = salinity;

        final double[] rTosa = NeuralNetIOConverter.multiplyPi(rlTosa); // rTosa = rlTosa * PI
        for (int i = 0; i < rlTosa.length; i++) {
            invAotAngNetInput[i + invAotAngNetInputIndex] = rTosa[i];
        }
        double[] invAotAngNetOutput = invAotAngNet.calc(invAotAngNetInput);
        final double aot560 = invAotAngNetOutput[0];
        final double angstrom = invAotAngNetOutput[1];

        if (aot560 < invAotAngNet.getOutmin()[0] || aot560 > invAotAngNet.getOutmax()[0]) {
            glintResult.raiseFlag(AOT560_OOR);
        }

        if (tetaSunSurfDeg > atmosphereNet.getInmax()[0] || tetaSunSurfDeg < atmosphereNet.getInmin()[0]) {
            glintResult.raiseFlag(SOLZEN);
        }

        if (!isAncillaryDataValid(pixel)) {
            glintResult.raiseFlag(ANCIL);
        }

        int autoAssocNetInputIndex = 0;
        double[] autoAssocNetInput = new double[autoAssocNet.getInmin().length];
        autoAssocNetInput[autoAssocNetInputIndex++] = tetaSunSurfDeg;
        autoAssocNetInput[autoAssocNetInputIndex++] = xyz[0];
        autoAssocNetInput[autoAssocNetInputIndex++] = xyz[1];
        autoAssocNetInput[autoAssocNetInputIndex++] = xyz[2];
        autoAssocNetInput[autoAssocNetInputIndex++] = temperature;
        autoAssocNetInput[autoAssocNetInputIndex++] = salinity;

        for (int i = 0; i < rlTosa.length; i++) {
            autoAssocNetInput[i + autoAssocNetInputIndex] = rTosa[i];
        }
        double[] autoAssocNetOutput = autoAssocNet.calc(autoAssocNetInput);
        double[] autoRlTosa = NeuralNetIOConverter.dividePi(autoAssocNetOutput);
        glintResult.setAutoTosaReflec(autoRlTosa);

        computeTosaQuality(rlTosa, autoAssocNetOutput, glintResult);
        final double tosaQualityIndicator = glintResult.getTosaQualityIndicator();
        if (tosaQualityIndicator > tosaOosThresh) {
            glintResult.raiseFlag(TOSA_OOS);
        }

        if (isL2RInvalid(pixel, tosaQualityIndicator)) {
            glintResult.raiseFlag(L2R_INVALID);
        }

        if (isL2RSuspect(pixel, tosaQualityIndicator)) {
            glintResult.raiseFlag(L2R_SUSPECT);
        }

        int atmoNetInputIndex = 0;
        double[] atmoNetInput = new double[atmosphereNet.getInmin().length];
        atmoNetInput[atmoNetInputIndex++] = tetaSunSurfDeg;
        atmoNetInput[atmoNetInputIndex++] = xyz[0];
        atmoNetInput[atmoNetInputIndex++] = xyz[1];
        atmoNetInput[atmoNetInputIndex++] = xyz[2];
        atmoNetInput[atmoNetInputIndex++] = temperature;
        atmoNetInput[atmoNetInputIndex++] = salinity;

        final double[] logRTosa = NeuralNetIOConverter.convertLogarithm(rTosa);
        for (int i = 0; i < rlTosa.length; i++) {
            atmoNetInput[i + atmoNetInputIndex] = logRTosa[i];    // for atmo_correct_meris/31x47x37_57596.9.net !!
        }
        double[] atmoNetOutput = atmosphereNet.calc(atmoNetInput);  // log_rw from 37x77x97_100157.4.net

        atmoNetOutput = NeuralNetIOConverter.convertExponential(atmoNetOutput);

        final double[] reflec = Arrays.copyOfRange(atmoNetOutput, 0, 12);
        if (ReflectanceEnum.IRRADIANCE_REFLECTANCES.equals(outputReflecAs)) {
            glintResult.setReflec(reflec);
            // changed on 20130321, current net 37x77x97_100157.4 gives rw, so multiply with PI (see mail from CB, 20130320)!
//            glintResult.setReflec(NeuralNetIOConverter.multiplyPi(reflec)); // irradiance reflectance, comparable with MERIS
        } else {
            glintResult.setReflec(reflec);
        }

        if (normalizationNet != null) {
            double[] normInNet = new double[15];
            normInNet[0] = tetaSunSurfDeg;
            normInNet[1] = tetaViewSurfDeg;
            normInNet[2] = aziDiffSurfDeg;  // new net 20120716
            for (int i = 0; i < 12; i++) {
                normInNet[i + 3] = Math.log(reflec[i] * Math.PI); // log_rlw into 90_2.8.net
            }
            final double[] normOutNet = normalizationNet.calc(normInNet);
            final double[] normReflec = new double[reflec.length];
            for (int i = 0; i < 12; i++) {
//                normReflec[i] = Math.exp(normOutNet[i]);
                normReflec[i] = Math.exp(normOutNet[i])/Math.PI;   // norm reflec must be WITHOUT PI (see mail from CB, 20130320)!
            }
            glintResult.setNormReflec(normReflec);
        }

        glintResult.setTau550(aot560);
        glintResult.setAngstrom(angstrom);
        glintResult.setTau778(Double.NaN);
        glintResult.setTau865(Double.NaN);
        glintResult.setGlintRatio(Double.NaN);
        glintResult.setBtsm(Double.NaN);
        glintResult.setAtot(Double.NaN);

        return glintResult;
    }

    private boolean isL2RInvalid(PixelData pixel, double tosaQualityIndicator) {
        final boolean isCloud = (pixel.l1pFlag & (1 << GlintCorrectionOperator.CLOUD_BIT_INDEX)) != 0;
        return tosaQualityIndicator > 3.0 || isCloud;
    }

    private boolean isL2RSuspect(PixelData pixel, double tosaQualityIndicator) {
        final boolean isCloud = (pixel.l1pFlag & (1 << GlintCorrectionOperator.CLOUD_BIT_INDEX)) != 0;
        final boolean isCloudBuffer = (pixel.l1pFlag & (1 << GlintCorrectionOperator.CLOUD_BUFFER_BIT_INDEX)) != 0;
        final boolean isCloudShadow = (pixel.l1pFlag & (1 << GlintCorrectionOperator.CLOUD_SHADOW_BIT_INDEX)) != 0;
        final boolean isSnowIce = (pixel.l1pFlag & (1 << GlintCorrectionOperator.SNOW_ICE_BIT_INDEX)) != 0;
        final boolean isMixedPixel = (pixel.l1pFlag & (1 << GlintCorrectionOperator.MIXEDPIXEL_BIT_INDEX)) != 0;
        return tosaQualityIndicator > 1.0 ||
                (isCloud || isCloudBuffer || isCloudShadow || isSnowIce || isMixedPixel);
    }

    private void writeDebugOutput(PixelData pixel, double[] normInNet, double[] normOutNet, double[] reflec, double[] normReflec, double aziDiffSurfDeg) {
        System.out.println("pixel.satazi = " + pixel.satazi);
        System.out.println("pixel.satzen = " + pixel.satzen);
        System.out.println("pixel.solazi = " + pixel.solazi);
        System.out.println("pixel.solzen = " + pixel.solzen);
        System.out.println("azimuth diff = " + aziDiffSurfDeg);
        for (int i = 0; i < reflec.length; i++) {
            System.out.println("reflec[" + i + "] = " + reflec[i]);
        }
        for (int i = 0; i < normInNet.length; i++) {
            System.out.println("normInNet[" + i + "] = " + normInNet[i]);
        }
        for (int i = 0; i < normOutNet.length; i++) {
            System.out.println("normOutNet[" + i + "] = " + normOutNet[i]);
        }
        for (int i = 0; i < normReflec.length; i++) {
            System.out.println("normReflec[" + i + "] = " + normReflec[i]);
        }
    }

}
