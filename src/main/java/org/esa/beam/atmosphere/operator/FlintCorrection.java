package org.esa.beam.atmosphere.operator;

import org.esa.beam.PixelData;
import org.esa.beam.meris.radiometry.smilecorr.SmileCorrectionAuxdata;
import org.esa.beam.nn.NNffbpAlphaTabFast;

import java.util.Arrays;

/**
* Class providing the AGC Glint correction (Flint mode compatible).
*/
public class FlintCorrection extends AbstractGlintCorrection {

    /**
     * @param atmosphereNet    the neural net for atmospheric correction
     * @param smileAuxdata     can be {@code null} if SMILE correction shall not be performed
     * @param normalizationNet can be {@code null} if normalization shall not be performed
     * @param outputReflecAs   output as radiance or irradiance reflectances
     */
    public FlintCorrection(NNffbpAlphaTabFast atmosphereNet, SmileCorrectionAuxdata smileAuxdata,
                           NNffbpAlphaTabFast normalizationNet,
                           NNffbpAlphaTabFast autoAssocNet, ReflectanceEnum outputReflecAs) {
        this.atmosphereNet = atmosphereNet;
        this.smileAuxdata = smileAuxdata;
        this.normalizationNet = normalizationNet;
        this.autoAssocNet = autoAssocNet;
        this.outputReflecAs = outputReflecAs;
    }

    /**
     * This method performa the Glint correction, using Flint or old AC net.
     *
     * @param pixel            the pixel input data
     * @param deriveRwFromPath whether to derive the water leaving reflectance from path or not
     * @param temperature      the water temperature
     * @param salinity         the water salinity
     *
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

        boolean isFlintMode = isFlintValueValid(pixel.flintValue);
        /* test if tosa reflectances are out of training range */
        if (!isTosaReflectanceValid(rlTosa, atmosphereNet, isFlintMode)) {
            glintResult.raiseFlag(TOSA_OOR);
        }
        if (tetaSunSurfDeg > atmosphereNet.getInmax()[0] || tetaSunSurfDeg < atmosphereNet.getInmin()[0]) {
            glintResult.raiseFlag(SOLZEN);
        }

        if (!isAncillaryDataValid(pixel)) {
            glintResult.raiseFlag(ANCIL);
        }


        double[] xyz = computeXYZCoordinates(tetaViewSurfRad, aziDiffSurfRad);

        int atmoNetInputIndex = 0;
        double[] atmoNetInput = new double[atmosphereNet.getInmin().length];
        atmoNetInput[atmoNetInputIndex++] = tetaSunSurfDeg;
        atmoNetInput[atmoNetInputIndex++] = xyz[0];
        atmoNetInput[atmoNetInputIndex++] = xyz[1];
        atmoNetInput[atmoNetInputIndex++] = xyz[2];
        if (!isFlintMode) {
            atmoNetInput[atmoNetInputIndex++] = temperature;
            atmoNetInput[atmoNetInputIndex++] = salinity;
        }
        for (int i = 0; i < rlTosa.length; i++) {
            atmoNetInput[i + atmoNetInputIndex] = Math.log(rlTosa[i]);
        }
        // last input is log_rlglint_13 in synergyMode
        if (isFlintMode) {
            atmoNetInput[atmoNetInput.length - 1] = pixel.flintValue;
        }

        // atmoNetInput can also be used for aaNN
        double[] atmoNetOutput = atmosphereNet.calc(atmoNetInput);
        double[] autoRlTosa = new double[atmoNetOutput.length];
        for (int i = 0; i < rlTosa.length; i++) {
            autoRlTosa[i] = atmoNetOutput[i]/Math.PI;
        }
        glintResult.setAutoTosaReflec(autoRlTosa);

        if (!isFlintMode) {
            computeTosaQuality(rlTosa, atmoNetOutput, glintResult);
        }

        for (int i = 0; i < 12; i++) {
            atmoNetOutput[i] = Math.exp(atmoNetOutput[i]);
            atmoNetOutput[i + 12] = Math.exp(atmoNetOutput[i + 12]);
            atmoNetOutput[i + 24] = Math.exp(
                    atmoNetOutput[i + 24]) / cosTetaSunSurfRad; //outnet is Ed_boa, not transmittance
        }

        final double[] transds = Arrays.copyOfRange(atmoNetOutput, 24, 36);
        glintResult.setTrans(transds);
        final double[] rwPaths = Arrays.copyOfRange(atmoNetOutput, 12, 24);
        glintResult.setPath(rwPaths);
        final double[] reflec = Arrays.copyOfRange(atmoNetOutput, 0, 12);
        double radiance2IrradianceFactor;
        if (ReflectanceEnum.IRRADIANCE_REFLECTANCES.equals(outputReflecAs)) {
            radiance2IrradianceFactor = Math.PI; // irradiance reflectance, comparable with MERIS
        } else {
            radiance2IrradianceFactor = 1.0; // radiance reflectance
        }
        for (int i = 0; i < reflec.length; i++) {
            if (deriveRwFromPath) {
                reflec[i] = deriveReflecFromPath(rwPaths[i], transds[i], rlTosa[i], cosTetaViewSurfRad,
                                                 cosTetaSunSurfRad, radiance2IrradianceFactor);
            } else {
                reflec[i] *= radiance2IrradianceFactor;
            }
        }
        glintResult.setReflec(reflec);

        if (normalizationNet != null) {
            double[] normInNet = new double[15];
            normInNet[0] = tetaSunSurfDeg;
            normInNet[1] = tetaViewSurfDeg;
            normInNet[2] = aziDiffSurfDeg;
            for (int i = 0; i < 12; i++) {
                normInNet[i + 3] = Math.log(reflec[i]);
            }
            final double[] normOutNet = normalizationNet.calc(normInNet);
            final double[] normReflec = new double[reflec.length];
            for (int i = 0; i < 12; i++) {
                normReflec[i] = Math.exp(normOutNet[i]);
            }
            glintResult.setNormReflec(normReflec);
        }

        /* compute angstrom coefficient from band 12 and 13 778 and 865 nm */
        double ang_443_865 = -Math.log(atmoNetOutput[36] / atmoNetOutput[39]) / Math.log(
                MERIS_WAVELENGTHS[1] / MERIS_WAVELENGTHS[11]);
        glintResult.setAngstrom(ang_443_865);
        glintResult.setTau550(atmoNetOutput[37]);
        glintResult.setTau778(atmoNetOutput[38]);
        glintResult.setTau865(atmoNetOutput[39]);
        if (!(atmoNetOutput[37] <= atmosphereNet.getOutmax()[37] * MAX_TAU_FACTOR)) {
            glintResult.raiseFlag(AOT560_OOR);
        }

        if (atmoNetOutput.length == 43) {
            // glint ratio available as output only for 'non-flint' case (RD, 28.10.09)
            glintResult.setGlintRatio(atmoNetOutput[40]);
            glintResult.setBtsm(Math.exp(atmoNetOutput[41]));
            glintResult.setAtot(Math.exp(atmoNetOutput[42]));

            if (atmoNetOutput[40] > atmosphereNet.getOutmax()[40] * 0.97) {
                glintResult.raiseFlag(SUNGLINT);
            }
        } else {
            glintResult.setGlintRatio(pixel.flintValue);    // test
            glintResult.setBtsm(Math.exp(atmoNetOutput[40]));
            glintResult.setAtot(Math.exp(atmoNetOutput[41]));
        }

        return glintResult;
    }

}
