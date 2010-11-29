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


    /**
     * @param atmosphereNet the neural net for atmospheric correction
     * @param smileAuxdata  can be {@code null} if SMILE correction shall not be performed
     */
    public GlintCorrection(NNffbpAlphaTabFast atmosphereNet, SmileCorrectionAuxdata smileAuxdata) {
        this.atmosphereNet = atmosphereNet;
        this.smileAuxdata = smileAuxdata;
    }

    /**
     * This method performa the Glint correction.
     *
     * @param pixel            - the pixel input data
     * @param deriveRwFromPath -
     *
     * @return GlintResult
     */
    public GlintResult perform(PixelData pixel, boolean deriveRwFromPath) {

        final double tetaViewSurfDeg = pixel.satzen; /* viewing zenith angle */
        final double tetaViewSurfRad = Math.toRadians(tetaViewSurfDeg);
        final double tetaSunSurfDeg = pixel.solzen; /* sun zenith angle */
        final double tetaSunSurfRad = Math.toRadians(tetaSunSurfDeg);
        final double aziDiffSurfRad = Math.toRadians(getAzimuthDifference(pixel));
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
        final double[] rlTosa = tosa.perform(pixel, tetaViewSurfRad, tetaSunSurfRad, aziDiffSurfRad);
        glintResult.setTosaReflec(rlTosa.clone());

        /* test if tosa reflectances are out of training range */
        if (!isTosaReflectanceValid(rlTosa, atmosphereNet)) {
            glintResult.raiseFlag(TOSA_OOR);
        }
        if (tetaSunSurfDeg > atmosphereNet.getInmax()[0] || tetaSunSurfDeg < atmosphereNet.getInmin()[0]) {
            glintResult.raiseFlag(SOLZEN);
        }

        if (!isAncillaryDataValid(pixel)) {
            glintResult.raiseFlag(ANCIL);
        }


        // water vapour correction for band 9 (708 nm)
        double rho_885 = pixel.toa_radiance[13] / pixel.solar_flux[13];
        double rho_900 = pixel.toa_radiance[14] / pixel.solar_flux[14];
        double x2 = rho_900 / rho_885;
        double trans708 = H2O_COR_POLY[0] + H2O_COR_POLY[1] * x2 + H2O_COR_POLY[2] * x2 * x2 + H2O_COR_POLY[3] * x2 * x2 * x2;
        rlTosa[8] /= trans708;

        double[] atmoInnet = new double[atmosphereNet.getInmin().length];
        atmoInnet[0] = tetaSunSurfDeg;   // replace by tetaSunDeg
        // calculate xyz coordinates
        atmoInnet[1] = -Math.sin(tetaViewSurfRad) * Math.cos(aziDiffSurfRad);
        atmoInnet[2] = Math.abs(-Math.sin(tetaViewSurfRad) * Math.sin(aziDiffSurfRad));
        atmoInnet[3] = cosTetaViewSurfRad;
        for (int i = 0; i < rlTosa.length; i++) {
            atmoInnet[i + 4] = Math.log(rlTosa[i]);
        }
        // last input is log_rlglint_13 in synergyMode
        if (isFlintValueValid(pixel.flintValue)) {
            atmoInnet[atmoInnet.length - 1] = pixel.flintValue;
        }

        double[] atmoOutnet = atmosphereNet.calc(atmoInnet);

        for (int i = 0; i < 12; i++) {
            atmoOutnet[i] = Math.exp(atmoOutnet[i]);
            atmoOutnet[i + 12] = Math.exp(atmoOutnet[i + 12]);
            atmoOutnet[i + 24] = Math.exp(atmoOutnet[i + 24]) / cosTetaSunSurfRad; //outnet is Ed_boa, not transmittance
        }

        final double[] transds = Arrays.copyOfRange(atmoOutnet, 24, 36);
        glintResult.setTrans(transds);
        final double[] rwPaths = Arrays.copyOfRange(atmoOutnet, 12, 24);
        glintResult.setPath(rwPaths);
        final double[] reflec = Arrays.copyOfRange(atmoOutnet, 0, 12);
        for (int i = 0; i < reflec.length; i++) {
            if (deriveRwFromPath) {
                final double v = transds[i]; /*probably a bug: / cosTetaSunRad **/
                double transu = Math.exp(Math.log(v) * (cosTetaSunSurfRad / cosTetaViewSurfRad));
                reflec[i] = (rlTosa[i] - rwPaths[i]) / transu * Math.PI;
            } else {
                reflec[i] *= Math.PI;
            }
        }
        glintResult.setReflec(reflec);

        /* compute angstrom coefficient from band 12 and 13 778 and 865 nm */
        double ang_443_865 = -Math.log(atmoOutnet[36] / atmoOutnet[39]) / Math.log(
                MERIS_WAVELENGTHS[1] / MERIS_WAVELENGTHS[11]);
        glintResult.setAngstrom(ang_443_865);
        glintResult.setTau550(atmoOutnet[37]);
        glintResult.setTau778(atmoOutnet[38]);
        glintResult.setTau865(atmoOutnet[39]);
        if (!(atmoOutnet[37] <= atmosphereNet.getOutmax()[37] * MAX_TAU_FACTOR)) {
            glintResult.raiseFlag(ATC_OOR);
        }

        if (atmoOutnet.length == 43) {
            // glint ratio available as output only for 'non-flint' case (RD, 28.10.09)
            glintResult.setGlintRatio(atmoOutnet[40]);
            glintResult.setBtsm(Math.exp(atmoOutnet[41]));
            glintResult.setAtot(Math.exp(atmoOutnet[42]));

            if (atmoOutnet[40] > atmosphereNet.getOutmax()[40] * 0.97) {
                glintResult.raiseFlag(SUNGLINT);
            }
        } else {
            glintResult.setGlintRatio(pixel.flintValue);    // test
            glintResult.setBtsm(Math.exp(atmoOutnet[40]));
            glintResult.setAtot(Math.exp(atmoOutnet[41]));
        }


        return glintResult;
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

    private static boolean isTosaReflectanceValid(double[] tosaRefl, NNffbpAlphaTabFast atmosphereNet) {
        for (int i = 0; i < tosaRefl.length; i++) {
            double currentRlTosa = Math.log(tosaRefl[i]);
            if (currentRlTosa > atmosphereNet.getInmax()[i + 4] || currentRlTosa < atmosphereNet.getInmin()[i + 4]) {
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
        double azi_diff_deg = Math.abs(pixel.solazi - pixel.satazi); /* azimuth difference */

        /* reverse azi difference */
        azi_diff_deg = 180.0 - azi_diff_deg; /* different definitions in MERIS data and MC /HL simulation */

        if (azi_diff_deg > 180.0) {
            azi_diff_deg = 360.0 - azi_diff_deg;
        }
        return azi_diff_deg;
    }


}
