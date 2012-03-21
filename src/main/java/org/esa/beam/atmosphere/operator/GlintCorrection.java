package org.esa.beam.atmosphere.operator;

import org.esa.beam.PixelData;
import org.esa.beam.meris.radiometry.smilecorr.SmileCorrectionAuxdata;
import org.esa.beam.nn.NNffbpAlphaTabFast;

import java.util.Arrays;

/**
 * Class providing the AGC Glint correction.
 */
public class GlintCorrection {

    public static final double[] MERIS_WAVELENGTHS = {
            412.3, 442.3, 489.7,
            509.6, 559.5, 619.4,
            664.3, 680.6, 708.1,
            753.1, 778.2, 864.6
    };

    public static final int LAND = 0x01;
    public static final int CLOUD_ICE = 0x02;
    public static final int ATC_OOR = 0x04;
    public static final int TOA_OOR = 0x08;
    public static final int TOSA_OOR = 0x10;
    public static final int SOLZEN = 0x20;
    public static final int ANCIL = 0x40;
    public static final int SUNGLINT = 0x08;
    public static final int HAS_FLINT = 0x100;
    public static final int INVALID = 0x8000;  // LAND || CLOUD_ICE || l1_flags.INVALID

    public static final int L1_INVALID_FLAG = 0x80;

    private static final double MAX_TAU_FACTOR = 0.84;
    private static final double[] H2O_COR_POLY = new double[]{
            0.3832989, 1.6527957, -1.5635101, 0.5311913
    }; // polynom coefficients for band708 correction


    private final NNffbpAlphaTabFast atmosphereNet;
    private final SmileCorrectionAuxdata smileAuxdata;
    private NNffbpAlphaTabFast normalizationNet;
    private NNffbpAlphaTabFast autoAssocNet;
    private ReflectanceEnum outputReflecAs;


    /**
     * @param atmosphereNet    the neural net for atmospheric correction
     * @param smileAuxdata     can be {@code null} if SMILE correction shall not be performed
     * @param normalizationNet can be {@code null} if normalization shall not be performed
     * @param outputReflecAs   output as radiance or irradiance reflectances
     */
    public GlintCorrection(NNffbpAlphaTabFast atmosphereNet, SmileCorrectionAuxdata smileAuxdata,
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
    public GlintResult performFlint(PixelData pixel, boolean deriveRwFromPath, double temperature, double salinity) {

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
            glintResult.raiseFlag(INVALID);
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
        if (!isFlintMode) {
            computeError(rlTosa, atmoNetInput, glintResult);
        }


        double[] atmoNetOutput = atmosphereNet.calc(atmoNetInput);

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
            glintResult.raiseFlag(ATC_OOR);
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

    /**
     * This method performa the Glint correction, using new AC net (March 2012).
     *
     * @param pixel            the pixel input data
     * @param deriveRwFromPath whether to derive the water leaving reflectance from path or not
     * @param temperature      the water temperature
     * @param salinity         the water salinity
     *
     * @return GlintResult
     */
    public GlintResult perform(PixelData pixel, boolean deriveRwFromPath, double temperature, double salinity) {

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
            glintResult.raiseFlag(INVALID);
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
//            atmoNetInput[i + atmoNetInputIndex] = Math.log(rlTosa[i]);
            atmoNetInput[i + atmoNetInputIndex] = rlTosa[i];
        }
        // last input is log_rlglint_13 in synergyMode
        if (isFlintMode) {
            atmoNetInput[atmoNetInput.length - 1] = pixel.flintValue;
        }

        // atmoNetInput can also be used for aaNN
        // todo: check if we need this here
//        if (!isFlintMode) {
//            computeError(rlTosa, atmoNetInput, glintResult);
//        }
        glintResult.setAutoTosaReflec(new double[]{Double.NaN});
        glintResult.setTosaQualityIndicator(Double.NaN);


        double[] atmoNetOutput = atmosphereNet.calc(atmoNetInput);

        for (int i = 0; i < 12; i++) {
            // i=0,..,23: now linear output
//            atmoNetOutput[i] = Math.exp(atmoNetOutput[i]);
//            atmoNetOutput[i + 12] = Math.exp(atmoNetOutput[i + 12]);
            // i=24,..,35: output now transmittance, not edBoa
//            atmoNetOutput[i + 24] = Math.exp(
//                    atmoNetOutput[i + 24]) / cosTetaSunSurfRad; //outnet is Ed_boa, not transmittance
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

        glintResult.setAngstrom(Double.NaN);
        glintResult.setTau550(Double.NaN);
        glintResult.setTau778(Double.NaN);
        glintResult.setTau865(Double.NaN);
        glintResult.setGlintRatio(Double.NaN);
        glintResult.setBtsm(Double.NaN);
        glintResult.setAtot(Double.NaN);

        return glintResult;
    }

    public static double correctViewAngle(double teta_view_deg, int pixelX, int centerPixel, boolean isFullResolution) {
        final double ang_coef_1 = -0.004793;
        final double ang_coef_2 = isFullResolution ? 0.0093247 / 4 : 0.0093247;
        teta_view_deg = teta_view_deg + Math.abs(pixelX - centerPixel) * ang_coef_2 + ang_coef_1;
        return teta_view_deg;
    }

    public static double deriveReflecFromPath(double rwPath, double transd, double rlTosa, double cosTetaViewSurfRad,
                                              double cosTetaSunSurfRad, double radiance2IrradianceFactor) {

        double transu = Math.exp(Math.log(transd) * (cosTetaSunSurfRad / cosTetaViewSurfRad));
        transu *= radiance2IrradianceFactor;
        double edBoa = transd * cosTetaSunSurfRad;
        return (rlTosa - rwPath) / (transu * edBoa) * cosTetaSunSurfRad;

        // simplest solution but gives nearly the same results
//        return (rlTosa - rwPath) / Math.pow(transd,2);

    }

    private double correctRlTosa9forWaterVapour(PixelData pixel, double rlTosa9) {
        double rho_885 = pixel.toa_radiance[13] / pixel.solar_flux[13];
        double rho_900 = pixel.toa_radiance[14] / pixel.solar_flux[14];
        double x2 = rho_900 / rho_885;
        double trans708 = H2O_COR_POLY[0] + H2O_COR_POLY[1] * x2 + H2O_COR_POLY[2] * x2 * x2 + H2O_COR_POLY[3] * x2 * x2 * x2;
        return rlTosa9 / trans708;
    }

    private double[] computeXYZCoordinates(double tetaViewSurfRad, double aziDiffSurfRad) {
        double[] xyz = new double[3];
        double cosTetaViewSurfRad = Math.cos(tetaViewSurfRad);

        xyz[0] = cosTetaViewSurfRad * Math.cos(aziDiffSurfRad);
        xyz[1] = cosTetaViewSurfRad * Math.sin(aziDiffSurfRad);
        xyz[2] = Math.sin(tetaViewSurfRad);
        return xyz;
    }

    private void computeError(double[] rlTosa, double[] autoAssocNetInput, GlintResult glintResult) {
        double[] aaNNOutnet = autoAssocNet.calc(autoAssocNetInput);
        double[] autoRlTosa = aaNNOutnet.clone();
        for (int i = 0; i < autoRlTosa.length; i++) {
            autoRlTosa[i] = Math.exp(autoRlTosa[i]);
        }
        glintResult.setAutoTosaReflec(autoRlTosa);
        double chi_sum = 0.0;
        for (int i = 0; i < rlTosa.length; i++) {
            double logRlTosa = Math.log(rlTosa[i]);
            chi_sum += Math.pow(((logRlTosa - aaNNOutnet[i]) / logRlTosa), 2.0); //RD20110116
        }
        double error = Math.sqrt(chi_sum / rlTosa.length);
        glintResult.setTosaQualityIndicator(error);

        // todo - raise a flag
//        water->error_rltosa=error;
//        if(error > THRESH_RL_TOSA_OOS) {
//            *c2r_megs_flag |=  PCD_16; /* RL_tosa is out of scope of the nn, OR with pcd_16 */
//        }

    }


    /**
     * This method checks if the given Flint value is valid
     * (i.e., not equal to 0.0 or NO_FLINT_VALUE)
     *
     * @param flintValue - the value
     *
     * @return boolean
     */
    public static boolean isFlintValueValid(double flintValue) {
        return (flintValue != GlintCorrectionOperator.NO_FLINT_VALUE &&
                flintValue != 0.0);
    }

    private static boolean isRlToaOor(PixelData pixel) {
        return (pixel.validation & ToaReflectanceValidationOp.RLTOA_OOR_FLAG_MASK) == ToaReflectanceValidationOp.RLTOA_OOR_FLAG_MASK;
    }

    private static boolean isCloudIce(PixelData pixel) {
        return (pixel.validation & ToaReflectanceValidationOp.CLOUD_ICE_FLAG_MASK) == ToaReflectanceValidationOp.CLOUD_ICE_FLAG_MASK;
    }

    private static boolean isLand(PixelData pixel) {
        return (pixel.validation & ToaReflectanceValidationOp.LAND_FLAG_MASK) == ToaReflectanceValidationOp.LAND_FLAG_MASK;
    }

    /*--------------------------------------------------------------------------
     **	test TOSA radiances as input to neural network for out of training range
     **  with band_nu 17/3/05 R.D.
    --------------------------------------------------------------------------*/

    private static boolean isTosaReflectanceValid(double[] tosaRefl, NNffbpAlphaTabFast atmosphereNet,
                                                  boolean isFlintMode) {
        int tosaOffset = isFlintMode ? 4 : 6;
        double[] inmax = atmosphereNet.getInmax();
        double[] inmin = atmosphereNet.getInmin();
        for (int i = 0; i < tosaRefl.length; i++) {
            double currentRlTosa = Math.log(tosaRefl[i]);
            if (currentRlTosa > inmax[i + tosaOffset] || currentRlTosa < inmin[i + tosaOffset]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAncillaryDataValid(PixelData pixel) {
        final boolean ozoneValid = pixel.ozone >= 200 && pixel.ozone <= 500;
        final boolean pressureValid = pixel.pressure >= 500 && pixel.pressure <= 1100;
        return ozoneValid && pressureValid;
    }


    private static double getAzimuthDifference(PixelData pixel) {
        double aziViewSurfRad = Math.toRadians(pixel.satazi);
        double aziSunSurfRad = Math.toRadians(pixel.solazi);
        double aziDiffSurfRad = Math.acos(Math.cos(aziViewSurfRad - aziSunSurfRad));
        return Math.toDegrees(aziDiffSurfRad);
    }


}
