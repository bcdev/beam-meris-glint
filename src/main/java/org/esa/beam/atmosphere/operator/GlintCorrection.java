package org.esa.beam.atmosphere.operator;

import org.esa.beam.PixelData;
import org.esa.beam.meris.radiometry.smilecorr.SmileCorrectionAuxdata;
import org.esa.beam.nn.NNffbpAlphaTabFast;

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
        final double cosTetaViewSurfRad = Math.cos(tetaViewSurfRad);
        final double cosTetaSunSurfRad = Math.cos(tetaSunSurfRad);

        final double aziViewSurf = pixel.satazi;
        final double aziSunSurf = pixel.solazi;

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

//        boolean isFlintMode = isFlintValueValid(pixel.flintValue);
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
        for (int i = 0; i < rlTosa.length; i++) {
            final double rTosa = rlTosa[i] * Math.PI; // rTosa = rlTosa * PI
            invAotAngNetInput[i + invAotAngNetInputIndex] = rTosa;
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
            final double rTosa = rlTosa[i] * Math.PI; // rTosa = rlTosa * PI
            autoAssocNetInput[i + autoAssocNetInputIndex] = rTosa;
        }
        double[] autoAssocNetOutput = autoAssocNet.calc(autoAssocNetInput);
        double[] autoRlTosa = new double[autoAssocNetOutput.length];
        for (int i = 0; i < rlTosa.length; i++) {
            autoRlTosa[i] = autoAssocNetOutput[i]/Math.PI;
        }
        glintResult.setAutoTosaReflec(autoRlTosa);

        computeTosaQuality(rlTosa, autoAssocNetOutput, glintResult);
        if (glintResult.getTosaQualityIndicator() > tosaOosThresh) {
            glintResult.raiseFlag(TOSA_OOS);
        }

        int atmoNetInputIndex = 0;
        double[] atmoNetInput = new double[atmosphereNet.getInmin().length];
        atmoNetInput[atmoNetInputIndex++] = tetaSunSurfDeg;
        atmoNetInput[atmoNetInputIndex++] = xyz[0];
        atmoNetInput[atmoNetInputIndex++] = xyz[1];
        atmoNetInput[atmoNetInputIndex++] = xyz[2];
        atmoNetInput[atmoNetInputIndex++] = temperature;
        atmoNetInput[atmoNetInputIndex++] = salinity;

        for (int i = 0; i < rlTosa.length; i++) {
            final double rTosa = rlTosa[i] * Math.PI; // rTosa = rlTosa * PI
            atmoNetInput[i + atmoNetInputIndex] = Math.log(rTosa);
        }
        double[] atmoNetOutput = atmosphereNet.calc(atmoNetInput);

        for (int i = 0; i < 12; i++) {
            // i=0,..,23: now linear output
//            atmoNetOutput[i] = Math.exp(atmoNetOutput[i]);
//            atmoNetOutput[i + 12] = Math.exp(atmoNetOutput[i + 12]);
            // i=24,..,35: output now transmittance, not edBoa
//            atmoNetOutput[i + 24] = Math.exp(
//                    atmoNetOutput[i + 24]) / cosTetaSunSurfRad; //outnet is Ed_boa, not transmittance
        }
        // another new net, 2012/06/08: output changed again to log... :-)
        // also, we have only 12 outputs (log_rw),
        // we do not have the rho_path, t_down, t_up any more (RD: "we don't need them")

        // another new net from RD, 2012/07/06:  (31x47x37_1618.6.net, this changed again to rw_logrtosa. In this case, comment following lines)
        // another new net from RD, 2012/07/06:  (31x47x37_21434.7.net)
        for (int i = 0; i < 12; i++) {
            atmoNetOutput[i] = Math.exp(atmoNetOutput[i]);
        }

//        final double[] transds = Arrays.copyOfRange(atmoNetOutput, 24, 36);
//        glintResult.setTrans(transds);
//        final double[] rwPaths = Arrays.copyOfRange(atmoNetOutput, 12, 24);
//        glintResult.setPath(rwPaths);
//        final double[] reflec = Arrays.copyOfRange(atmoNetOutput, 0, 12);
        final double[] reflec = Arrays.copyOfRange(atmoNetOutput, 0, 12);
        double radiance2IrradianceFactor;
        if (ReflectanceEnum.IRRADIANCE_REFLECTANCES.equals(outputReflecAs)) {
            radiance2IrradianceFactor = Math.PI; // irradiance reflectance, comparable with MERIS
        } else {
            radiance2IrradianceFactor = 1.0; // radiance reflectance
        }
        for (int i = 0; i < reflec.length; i++) {
            // in the latest net, we do not have the paths any more (see above)
//            if (deriveRwFromPath) {
//                reflec[i] = deriveReflecFromPath(rwPaths[i], transds[i], rlTosa[i], cosTetaViewSurfRad,
//                                                 cosTetaSunSurfRad, radiance2IrradianceFactor);
//            } else {
//                reflec[i] *= radiance2IrradianceFactor;
//            }
            reflec[i] *= radiance2IrradianceFactor;
        }
        glintResult.setReflec(reflec);

        // OLD normalization net
//        if (normalizationNet != null) {
//            double[] normInNet = new double[15];
////            double[] normInNet = new double[17];   // new net 20120716
//            normInNet[0] = tetaSunSurfDeg;
//            normInNet[1] = tetaViewSurfDeg;
//            normInNet[2] = aziDiffSurfDeg;  // new net 20120716
////            normInNet[2] = aziViewSurf;       // new net 20120716
////            normInNet[3] = temperature;       // new net 20120716
////            normInNet[4] = salinity;
//            for (int i = 0; i < 12; i++) {
//                normInNet[i + 3] = Math.log(reflec[i]); // log(rl)
////                normInNet[i + 5] = Math.log(reflec[i]*Math.PI); // log(r), new net 20120716, r=l*PI
//            }
//            final double[] normOutNet = normalizationNet.calc(normInNet);
//            final double[] normReflec = new double[reflec.length];
//            for (int i = 0; i < 12; i++) {
//                normReflec[i] = Math.exp(normOutNet[i]);
////                normReflec[i] = Math.exp(normOutNet[i]/Math.PI); // rl=r/PI
//            }
//            glintResult.setNormReflec(normReflec);
//            if (pixel.pixelX == 500 && pixel.pixelY == 150) {
//                writeDebugOutput(pixel, normInNet, normOutNet, reflec, normReflec, aziDiffSurfDeg);
//            }
//        }

        // NEW normalization net
        if (normalizationNet != null) {
//            double[] normInNet = new double[15];
            double[] normInNet = new double[17];   // new net 20120716
            normInNet[0] = tetaSunSurfDeg;
            normInNet[1] = tetaViewSurfDeg;
//            normInNet[2] = aziDiffSurfDeg;  // new net 20120716
            normInNet[2] = aziViewSurf;       // new net 20120716
            normInNet[3] = temperature;       // new net 20120716
            normInNet[4] = salinity;
            for (int i = 0; i < 12; i++) {
//                normInNet[i + 3] = Math.log(reflec[i]); // log(rl)
                normInNet[i + 5] = Math.log(reflec[i] * Math.PI); // log(r), new net 20120716, r=l*PI
            }
            final double[] normOutNet = normalizationNet.calc(normInNet);
            final double[] normReflec = new double[reflec.length];
            for (int i = 0; i < 12; i++) {
//                normReflec[i] = Math.exp(normOutNet[i]);
//                normReflec[i] = Math.exp(normOutNet[i] / Math.PI); // rl=r/PI
                normReflec[i] = Math.exp(normOutNet[i] - Math.log(Math.PI)); // rl = exp[log(r) - log(PI)] !!!!
            }
            glintResult.setNormReflec(normReflec);
            if (pixel.pixelX == 500 && pixel.pixelY == 150) {
                writeDebugOutput(pixel, normInNet, normOutNet, reflec, normReflec, aziDiffSurfDeg);
            }
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
