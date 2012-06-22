package org.esa.beam.atmosphere.operator;

import org.esa.beam.PixelData;
import org.esa.beam.meris.radiometry.smilecorr.SmileCorrectionAuxdata;

import static java.lang.Math.*;

/**
 * Created by Marco Peters.
 *
 * @author Marco Peters
 * @version $Revision: 2185 $ $Date: 2009-10-28 14:18:32 +0100 (Mi, 28 Okt 2009) $
 */
class Tosa {

    private static final double[] OZON_ABSORPTION = {
            8.2e-004, 2.82e-003, 2.076e-002, 3.96e-002, 1.022e-001,
            1.059e-001, 5.313e-002, 3.552e-002, 1.895e-002, 8.38e-003,
            7.2e-004, 0.0
    };

    // polynom coefficients for band708 correction
    private static final double[] H2O_COR_POLY = new double[]{
            0.3832989, 1.6527957, -1.5635101, 0.5311913
    };


    private double[] trans_oz_down_rest;
    private double[] trans_oz_up_rest;
    private double[] tau_rayl_rest;
    private double[] trans_oz_down_real;
    private double[] trans_oz_up_real;
    private double[] trans_rayl_down_rest;
    private double[] trans_rayl_up_rest;
    private double[] lrcPath;
    private double[] ed_toa;
    private double[] edTosa;
    private double[] lTosa;
    private SmileCorrectionAuxdata smileAuxdata;

    /**
     * Creates instance of this class
     *
     * @param smileAuxdata can be {@code null} if SMILE correction shall not be performed
     */
    Tosa(SmileCorrectionAuxdata smileAuxdata) {
        this.smileAuxdata = smileAuxdata;
    }

    public void init() {
        int length = 12;
        trans_oz_down_rest = new double[length];
        trans_oz_up_rest = new double[length];
        tau_rayl_rest = new double[length];
        trans_oz_down_real = new double[length];
        trans_oz_up_real = new double[length];
        trans_rayl_down_rest = new double[length];
        trans_rayl_up_rest = new double[length];
        lrcPath = new double[length];
        ed_toa = new double[length];
        edTosa = new double[length];
        lTosa = new double[length];
    }

    public double[] perform(PixelData pixel, double teta_view_surf_rad, double teta_sun_surf_rad) {

        /* angles */
        double cos_teta_sun_surf = cos(teta_sun_surf_rad);
        double sin_teta_sun_surf = sin(teta_sun_surf_rad);
        double cos_teta_view_surf = cos(teta_view_surf_rad);
        double sin_teta_view_surf = sin(teta_view_surf_rad);

        double azi_view_surf_rad = toRadians(pixel.satazi);
        double azi_sun_surf_rad = toRadians(pixel.solazi);
        double azi_diff_surf_rad = acos(cos(azi_view_surf_rad - azi_sun_surf_rad));
        double cos_azi_diff_surf = cos(azi_diff_surf_rad);

        double[] rlTosa = new double[12];
        double[] sun_toa;
        if (smileAuxdata != null) {
            sun_toa = retrieveToaFrom(doSmileCorrection(pixel.detectorIndex, pixel.solar_flux, smileAuxdata));
        } else {
            sun_toa = retrieveToaFrom(pixel.solar_flux);
        }

        double[] lToa = retrieveToaFrom(pixel.toa_radiance);

        /* calculate relative airmass rayleigh correction for correction layer*/
        if (pixel.altitude < 1.0f) {
            pixel.altitude = 1.0f;
        }

        double altitude_pressure = pixel.pressure * Math.pow((1.0 - 0.0065 * pixel.altitude / 288.15), 5.255);

        double rayl_rest_mass = (altitude_pressure - 1013.2) / 1013.2;


        /* calculate optical thickness of rayleigh for correction layer, lam in micrometer */
        for (int i = 0; i < tau_rayl_rest.length; i++) {
            final double currentWavelength = GlintCorrection.MERIS_WAVELENGTHS[i] / 1000; // wavelength in micrometer
            tau_rayl_rest[i] = rayl_rest_mass *
                               (0.008524 * pow(currentWavelength, -4.0) +
                                9.63E-5 * pow(currentWavelength, -6.0) +
                                1.1E-6 * pow(currentWavelength, -8.0));
        }

        /* calculate phase function for rayleigh path radiance*/
        double cos_scat_ang = -cos_teta_view_surf * cos_teta_sun_surf - sin_teta_view_surf * sin_teta_sun_surf * cos_azi_diff_surf;
        double delta = 0.0279;
        double gam = delta / (2.0 - delta);
        double phase_rayl = 3.0 / (4.0 * (1.0 + 2.0 * gam)) * ((1.0 - gam) * cos_scat_ang * cos_scat_ang + (1.0 + 3.0 * gam));

        /* ozon and rayleigh correction layer transmission */
        double ozon_rest_mass = (pixel.ozone / 1000.0); /* conc ozone from MERIS is in DU */
        for (int i = 0; i < trans_oz_down_rest.length; i++) {
            final double ozonAbsorption = -OZON_ABSORPTION[i];
            final double scaledTauRaylRest = -tau_rayl_rest[i] * 0.5; /* 0.5 because diffuse trans */

            trans_oz_down_real[i] = exp(ozonAbsorption * pixel.ozone / 1000.0 / cos_teta_sun_surf);
            trans_oz_up_real[i] = exp(ozonAbsorption * pixel.ozone / 1000.0 / cos_teta_view_surf);

            trans_oz_down_rest[i] = exp(ozonAbsorption * ozon_rest_mass / cos_teta_sun_surf);
            trans_oz_up_rest[i] = exp(ozonAbsorption * ozon_rest_mass / cos_teta_view_surf);

            trans_rayl_down_rest[i] = exp(scaledTauRaylRest / cos_teta_sun_surf);
            trans_rayl_up_rest[i] = exp(scaledTauRaylRest / cos_teta_view_surf);
        }

        /* Rayleigh path radiance of correction layer */

        for (int i = 0; i < lrcPath.length; i++) {
            lrcPath[i] = sun_toa[i] * trans_oz_down_real[i] * tau_rayl_rest[i]
                         * phase_rayl / (4 * Math.PI * cos_teta_view_surf);
        }

        /* compute Ed_toa from sun_toa using  cos_teta_sun */
        for (int i = 0; i < ed_toa.length; i++) {
            ed_toa[i] = sun_toa[i] * cos_teta_sun_surf;
        }

        /* Calculate Ed_tosa */
        for (int i = 0; i < edTosa.length; i++) {
            edTosa[i] = ed_toa[i] * trans_oz_down_rest[i] * trans_rayl_down_rest[i];
        }

        /* compute path radiance difference for tosa without - with smile */
        for (int i = 0; i < lTosa.length; i++) {
            /* Calculate L_tosa */
            lTosa[i] = (lToa[i] + lrcPath[i] * trans_oz_up_real[i]) / trans_oz_up_rest[i] * trans_rayl_up_rest[i];
            /* Calculate Lsat_tosa radiance reflectance as input to NN */
            rlTosa[i] = lTosa[i] / edTosa[i];
        }

        // water vapour correction for band 9 (708 nm)
        rlTosa[8] = correctRlTosa9forWaterVapour(pixel, rlTosa[8]);

        return rlTosa;
    }

    private double correctRlTosa9forWaterVapour(PixelData pixel, double rlTosa9) {
        double rho_885 = pixel.toa_radiance[13] / pixel.solar_flux[13];
        double rho_900 = pixel.toa_radiance[14] / pixel.solar_flux[14];
        double x2 = rho_900 / rho_885;
        double trans708 = H2O_COR_POLY[0] + H2O_COR_POLY[1] * x2 + H2O_COR_POLY[2] * x2 * x2 + H2O_COR_POLY[3] * x2 * x2 * x2;
        return rlTosa9 / trans708;
    }


    private static double[] doSmileCorrection(int detectorIndex, double[] solarFlux,
                                              SmileCorrectionAuxdata smileAuxData) {
        /* correct solar flux for this pixel */
        double[] solarFluxSmile = new double[solarFlux.length];
        double[] detectorSunSpectralFlux = smileAuxData.getDetectorSunSpectralFluxes()[detectorIndex];
        double[] theoreticalSunSpectralFluxes = smileAuxData.getTheoreticalSunSpectralFluxes();
        for (int i = 0; i < solarFlux.length; i++) {
            solarFluxSmile[i] = solarFlux[i] * (detectorSunSpectralFlux[i] / theoreticalSunSpectralFluxes[i]);
        }
        return solarFluxSmile;
    }

    private static double[] retrieveToaFrom(double[] values) {
        double[] toa = new double[12];
        System.arraycopy(values, 0, toa, 0, 10);
        System.arraycopy(values, 11, toa, 10, 2);
        return toa;
    }

}
