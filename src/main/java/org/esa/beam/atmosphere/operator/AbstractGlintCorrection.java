package org.esa.beam.atmosphere.operator;

import org.esa.beam.PixelData;
import org.esa.beam.meris.radiometry.smilecorr.SmileCorrectionAuxdata;
import org.esa.beam.nn.NNffbpAlphaTabFast;

/**
 * Class providing the AGC Glint correction.
 */
abstract class AbstractGlintCorrection {

    static final double[] MERIS_WAVELENGTHS = {
            412.3, 442.3, 489.7,
            509.6, 559.5, 619.4,
            664.3, 680.6, 708.1,
            753.1, 778.2, 864.6
    };

    static final int LAND = 0x01;
    static final int CLOUD_ICE = 0x02;
    static final int AOT560_OOR = 0x04;
    static final int TOA_OOR = 0x08;
    static final int TOSA_OOR = 0x10;
    static final int SOLZEN = 0x20;
    static final int ANCIL = 0x40;
    static final int SUNGLINT = 0x80;
    static final int HAS_FLINT = 0x100;
    static final int INPUT_INVALID = 0x8000;  // LAND || CLOUD_ICE || l1_flags.INVALID

    static final int L1_INVALID_FLAG = 0x80;

    static final double MAX_TAU_FACTOR = 0.84;

//    static final double[] H2O_COR_POLY = new double[]{
//            0.3832989, 1.6527957, -1.5635101, 0.5311913
//    }; // polynom coefficients for band708 correction


    NNffbpAlphaTabFast atmosphereNet;
    NNffbpAlphaTabFast invAotAngNet;
    SmileCorrectionAuxdata smileAuxdata;
    NNffbpAlphaTabFast normalizationNet;
    NNffbpAlphaTabFast autoAssocNet;
    ReflectanceEnum outputReflecAs;

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
    abstract GlintResult perform(PixelData pixel, boolean deriveRwFromPath, double temperature, double salinity, double tosaOosThresh);

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

    static double[] computeXYZCoordinates(double tetaViewSurfRad, double aziDiffSurfRad) {
        double[] xyz = new double[3];

        xyz[0] = Math.sin(tetaViewSurfRad) * Math.cos(aziDiffSurfRad);
        xyz[1] = Math.sin(tetaViewSurfRad) * Math.sin(aziDiffSurfRad);
        xyz[2] = Math.cos(tetaViewSurfRad);
        return xyz;
    }

    void computeError(double[] rlTosa, double[] autoAssocNetInput, GlintResult glintResult) {
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
    static boolean isFlintValueValid(double flintValue) {
        return (flintValue != GlintCorrectionOperator.NO_FLINT_VALUE &&
                flintValue != 0.0);
    }

    static boolean isRlToaOor(PixelData pixel) {
        return (pixel.validation & ToaReflectanceValidationOp.RLTOA_OOR_FLAG_MASK) == ToaReflectanceValidationOp.RLTOA_OOR_FLAG_MASK;
    }

    static boolean isCloudIce(PixelData pixel) {
        return (pixel.validation & ToaReflectanceValidationOp.CLOUD_ICE_FLAG_MASK) == ToaReflectanceValidationOp.CLOUD_ICE_FLAG_MASK;
    }

    static boolean isLand(PixelData pixel) {
        return (pixel.validation & ToaReflectanceValidationOp.LAND_FLAG_MASK) == ToaReflectanceValidationOp.LAND_FLAG_MASK;
    }

    /*--------------------------------------------------------------------------
     **	test TOSA radiances as input to neural network for out of training range
     **  with band_nu 17/3/05 R.D.
    --------------------------------------------------------------------------*/

    static boolean isTosaReflectanceValid(double[] tosaRefl, NNffbpAlphaTabFast atmosphereNet,
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

    static boolean isAncillaryDataValid(PixelData pixel) {
        final boolean ozoneValid = pixel.ozone >= 200 && pixel.ozone <= 500;
        final boolean pressureValid = pixel.pressure >= 500 && pixel.pressure <= 1100;
        return ozoneValid && pressureValid;
    }


    static double getAzimuthDifference(PixelData pixel) {
//        delta_azimuth=fabs(view_azimuth-sun_azimuth);
//       	if(delta_azimuth>180.0) delta_azimuth=180.0-delta_azimuth;

        double aziViewSurfRad = Math.toRadians(pixel.satazi);
        double aziSunSurfRad = Math.toRadians(pixel.solazi);
        double aziDiffSurfRad = Math.acos(Math.cos(aziViewSurfRad - aziSunSurfRad));
        return Math.toDegrees(aziDiffSurfRad);
    }

    // currently not needed
//    double correctRlTosa9forWaterVapour(PixelData pixel, double rlTosa9) {
//        double rho_885 = pixel.toa_radiance[13] / pixel.solar_flux[13];
//        double rho_900 = pixel.toa_radiance[14] / pixel.solar_flux[14];
//        double x2 = rho_900 / rho_885;
//        double trans708 = H2O_COR_POLY[0] + H2O_COR_POLY[1] * x2 + H2O_COR_POLY[2] * x2 * x2 + H2O_COR_POLY[3] * x2 * x2 * x2;
//        return rlTosa9 / trans708;
//    }

}
