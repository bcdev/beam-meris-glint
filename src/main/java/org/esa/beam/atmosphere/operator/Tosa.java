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
            -8.2e-004, -2.82e-003, -2.076e-002, -3.96e-002, -1.022e-001,
            -1.059e-001, -5.313e-002, -3.552e-002, -1.895e-002, -8.38e-003,
            -7.2e-004, -0.0
    };


    private double[] trans_oz_toa_tosa_down_surf;
    private double[] trans_oz_toa_tosa_up_surf;
    private double[] tau_rayl_toa_tosa;
    private double[] trans_ozon_down_surf;
    private double[] trans_ozon_up_surf;
    private double[] trans_rayl_down_surf;
    private double[] trans_rayl_up_surf;
    private double[] trans_ozon_down_meris;
    private double[] trans_ozon_up_meris;
    private double[] trans_rayl_down_meris;
    private double[] trans_rayl_up_meris;
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
        trans_oz_toa_tosa_down_surf = new double[length];
        trans_oz_toa_tosa_up_surf = new double[length];
        tau_rayl_toa_tosa = new double[length];
        trans_ozon_down_surf = new double[length];
        trans_ozon_up_surf = new double[length];
        trans_rayl_down_surf = new double[length];
        trans_rayl_up_surf = new double[length];
        trans_ozon_down_meris = new double[length];
        trans_ozon_up_meris = new double[length];
        trans_rayl_down_meris = new double[length];
        trans_rayl_up_meris = new double[length];
        lrcPath = new double[length];
        ed_toa = new double[length];
        edTosa = new double[length];
        lTosa = new double[length];
    }

    public double[] perform(PixelData pixel, double teta_view_surf_rad, double teta_sun_surf_rad, double azi_diff_rad) {

        /* angles */
        double cos_teta_sun_surf = cos(teta_sun_surf_rad);
        double sin_teta_sun_surf = sin(teta_sun_surf_rad);
        double cos_teta_view_surf = cos(teta_view_surf_rad);
        double sin_teta_view_surf = sin(teta_view_surf_rad);

        double azi_view_surf_rad = toRadians(pixel.satazi);
        double azi_sun_surf_rad = toRadians(pixel.solazi);
        double azi_diff_surf_rad = acos(cos(azi_view_surf_rad - azi_sun_surf_rad));
        double cos_azi_diff_surf = cos(azi_diff_surf_rad);

        double azi_view_meris_rad = toRadians(pixel.viewaziMer);
        double azi_sun_meris_rad = toRadians((pixel.solaziMer));
        double azi_diff_meris_rad = acos(cos(azi_view_meris_rad - azi_sun_meris_rad));
        double cos_azi_diff_meris = cos(azi_diff_meris_rad);

        // todo - different to breadboard line 159 in mer_wat_***01.c
        double teta_view_meris_rad = teta_view_surf_rad / 1.1364;
        double teta_sun_meris_rad = toRadians(pixel.solzenMer);

        double sin_teta_view_meris = sin(teta_view_meris_rad);
        double sin_teta_sun_meris = sin(teta_sun_meris_rad);

        double cos_teta_view_meris = cos(teta_view_meris_rad);
        double cos_teta_sun_meris = cos(teta_sun_meris_rad);


        double[] rlTosa = new double[12];
        double[] tau_rayl_standard = new double[12];
        double[] sun_toa;
        if (smileAuxdata != null) {
            sun_toa = retrieveToaFrom(doSmileCorrection(pixel.detectorIndex, pixel.solar_flux, smileAuxdata));
        } else {
            sun_toa = retrieveToaFrom(pixel.solar_flux);
        }

        double[] lToa = retrieveToaFrom(pixel.toa_radiance);

        /* compute Ed_toa from sun_toa using  cos_teta_sun */
        for (int i = 0; i < ed_toa.length; i++) {
            ed_toa[i] = sun_toa[i] * cos_teta_sun_surf;
        }

        /* calculate relative airmass rayleigh correction for correction layer*/
        if (pixel.altitude < 1.0f) {
            pixel.altitude = 1.0f;
        }

        double altitude_pressure = pixel.pressure * Math.pow((1.0 - 0.0065 * pixel.altitude / 288.15), 5.255);

        double rayl_rest_mass = (altitude_pressure - 1013.2) / 1013.2;


        /* calculate optical thickness of rayleigh for correction layer, lam in micrometer */
        for (int i = 0; i < tau_rayl_standard.length; i++) {
            tau_rayl_standard[i] = 0.008735 * Math.pow(GlintCorrection.MERIS_WAVELENGTHS[i] / 1000.0,
                                                       -4.08);/* lam in µm */
            tau_rayl_toa_tosa[i] = tau_rayl_standard[i] * rayl_rest_mass;
        }

        /* calculate phase function for rayleigh path radiance*/
        double cos_scat_ang_surf = -cos_teta_view_surf * cos_teta_sun_surf - sin_teta_view_surf * sin_teta_sun_surf * cos_azi_diff_surf;
        double cos_scat_ang_meris = -cos_teta_view_meris * cos_teta_sun_meris - sin_teta_view_meris * sin_teta_sun_meris * cos_azi_diff_meris;
        double phase_rayl_surf = 0.75 * (1.0 + cos_scat_ang_surf * cos_scat_ang_surf);
        double phase_rayl_meris = 0.75 * (1.0 + cos_scat_ang_meris * cos_scat_ang_meris);

        double[] LRpathDiff = new double[trans_oz_toa_tosa_down_surf.length];
        /* ozon and rayleigh correction layer transmission */
        double ozon_rest_mass = (pixel.ozone / 1000.0 - 0.35); /* conc ozone from MERIS is in DU */
        for (int i = 0; i < trans_oz_toa_tosa_down_surf.length; i++) {
            final double ozonAbsorption = OZON_ABSORPTION[i];
            final double scaledTauToaTosa = -tau_rayl_toa_tosa[i] * 0.5; /* 0.5 because diffuse trans */

            trans_oz_toa_tosa_down_surf[i] = exp(ozonAbsorption * ozon_rest_mass / cos_teta_sun_surf);
            trans_oz_toa_tosa_up_surf[i] = exp(ozonAbsorption * ozon_rest_mass / cos_teta_view_surf);

            trans_ozon_down_surf[i] = exp(ozonAbsorption * pixel.ozone / 1000.0 / cos_teta_sun_surf);
            trans_ozon_up_surf[i] = exp(ozonAbsorption * pixel.ozone / 1000.0 / cos_teta_view_surf);
            trans_rayl_down_surf[i] = exp(scaledTauToaTosa / cos_teta_sun_surf);
            trans_rayl_up_surf[i] = exp(scaledTauToaTosa / cos_teta_view_surf);

            double LRpath_surf = sun_toa[i] * trans_ozon_down_surf[i] * trans_ozon_up_surf[i] *
                                 cos_teta_sun_surf / cos_teta_sun_meris * tau_rayl_standard[i] * phase_rayl_surf /
                                 (4.0 * PI * cos_teta_view_surf);

            trans_ozon_down_meris[i] = exp(ozonAbsorption * pixel.ozone / 1000.0 / cos_teta_sun_meris);
            trans_ozon_up_meris[i] = exp(ozonAbsorption * pixel.ozone / 1000.0 / cos_teta_view_meris);
            trans_rayl_down_meris[i] = exp(scaledTauToaTosa / cos_teta_sun_meris);
            trans_rayl_up_meris[i] = exp(scaledTauToaTosa / cos_teta_view_meris);

            double LRpath_meris = sun_toa[i] * trans_ozon_down_surf[i] * tau_rayl_standard[i] * phase_rayl_meris /
                                  (4.0 * PI * cos_teta_view_meris);

            LRpathDiff[i] = LRpath_surf - LRpath_meris;


        }

        /* Rayleigh path radiance of correction layer */

        for (int i = 0; i < lrcPath.length; i++) {
            lrcPath[i] = ed_toa[i] * tau_rayl_toa_tosa[i] * trans_ozon_down_surf[i]
                         * phase_rayl_surf / (4 * Math.PI * cos_teta_view_surf * cos_teta_sun_surf);
        }

        /* Calculate Ed_tosa */

        for (int i = 0; i < edTosa.length; i++) {
            edTosa[i] = ed_toa[i] * trans_oz_toa_tosa_down_surf[i] * trans_rayl_down_surf[i];
        }

        /* compute path radiance difference for tosa without - with smile */
        for (int i = 0; i < lTosa.length; i++) {
            /* Calculate L_tosa */
            lTosa[i] = (lToa[i] - lrcPath[i] * trans_ozon_up_surf[i]) / (trans_oz_toa_tosa_up_surf[i] * trans_rayl_up_surf[i]) + LRpathDiff[i];
            /* Calculate Lsat_tosa radiance reflectance as input to NN */
            rlTosa[i] = lTosa[i] / edTosa[i];
        }

        return rlTosa;
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
