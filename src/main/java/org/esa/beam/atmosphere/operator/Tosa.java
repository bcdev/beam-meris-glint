package org.esa.beam.atmosphere.operator;

import org.esa.beam.PixelData;

/**
 * Created by Marco Peters.
 *
 * @author Marco Peters
 * @version $Revision: 2185 $ $Date: 2009-10-28 14:18:32 +0100 (Mi, 28 Okt 2009) $
 */
class Tosa {
    private static final double[] OZON_ABSORBTION = {
            -8.2e-004, -2.82e-003, -2.076e-002, -3.96e-002, -1.022e-001,
            -1.059e-001, -5.313e-002, -3.552e-002, -1.895e-002, -8.38e-003,
            -7.2e-004, -0.0};


    private double[] trans_oz_toa_tosa_down;
    private double[] trans_oz_toa_tosa_up;
    private double[] tau_rayl_toa_tosa;
    private double[] trans_oz_tosa_down;
    private double[] trans_oz_tosa_up;
    private double[] trans_rayl_down;
    private double[] trans_rayl_up;
    private double[] lrcPath;
    private double[] ed_toa;
    private double[] edTosa;
    private double[] lTosa;

    public void init() {
        int length = 12;
        trans_oz_toa_tosa_down = new double[length];
        trans_oz_toa_tosa_up = new double[length];
        tau_rayl_toa_tosa = new double[length];
        trans_oz_tosa_down = new double[length];
        trans_oz_tosa_up = new double[length];
        trans_rayl_down = new double[length];
        trans_rayl_up = new double[length];
        lrcPath = new double[length];
        ed_toa = new double[length];
        edTosa = new double[length];
        lTosa = new double[length];
    }

    public double[] perform(PixelData pixel, double teta_view_rad, double teta_sun_rad, double azi_diff_rad) {

        /* angles */
        double cos_teta_sun = Math.cos(teta_sun_rad);
        double sin_teta_sun = Math.sin(teta_sun_rad);
        double cos_teta_view = Math.cos(teta_view_rad);
        double sin_teta_view = Math.sin(teta_view_rad);
        double cos_azi_diff = Math.cos(azi_diff_rad);

        double altitude_pressure;
        double[] rlTosa = new double[12];
        double[] tau_rayl_standard = new double[12];

        double[] sun_toa = retrieveToaFrom(pixel.solar_flux);
        double[] lToa = retrieveToaFrom(pixel.toa_radiance);

        /* compute Ed_toa from sun_toa using  cos_teta_sun */
        for (int i = 0; i < ed_toa.length; i++) {
            ed_toa[i] = sun_toa[i] * cos_teta_sun;
        }

        /* calculate relative airmass rayleigh correction for correction layer*/
        if(pixel.altitude < 1.0f){
            pixel.altitude= 1.0f;
        }
        altitude_pressure = pixel.pressure * Math.pow((1.0 - 0.0065 * pixel.altitude / 288.15), 5.255);

        double rayl_rel_mass_toa_tosa = (altitude_pressure - 1013.2) / 1013.2;


        /* calculate optical thickness of rayleigh for correction layer, lam in micrometer */
        for (int i = 0; i < tau_rayl_standard.length; i++) {
            tau_rayl_standard[i] = 0.008735 * Math.pow(GlintCorrection.MERIS_WAVLENGTHS[i] / 1000.0, -4.08);/* lam in µm */
            tau_rayl_toa_tosa[i] = tau_rayl_standard[i] * rayl_rel_mass_toa_tosa;
        }

        /* calculate phase function for rayleigh path radiance*/
        double cos_scat_ang = -cos_teta_view * cos_teta_sun - sin_teta_view * sin_teta_sun * cos_azi_diff;
        double phase_rayl = 0.75 * (1.0 + cos_scat_ang * cos_scat_ang);

        /* ozon and rayleigh correction layer transmission */
        double ozon_rest_mass = (pixel.ozone / 1000.0 - 0.35); /* conc ozone from MERIS is in DU */
        for (int i = 0; i < trans_oz_toa_tosa_down.length; i++) {
            final double ozonAbsorbtion = OZON_ABSORBTION[i];
            trans_oz_toa_tosa_down[i] = Math.exp(ozonAbsorbtion * ozon_rest_mass / cos_teta_sun);
            trans_oz_toa_tosa_up[i] = Math.exp(ozonAbsorbtion * ozon_rest_mass / cos_teta_view);
            trans_oz_tosa_down[i] = Math.exp(ozonAbsorbtion * pixel.ozone / 1000.0 / cos_teta_sun);
            trans_oz_tosa_up[i] = Math.exp(ozonAbsorbtion * pixel.ozone / 1000.0 / cos_teta_view);
            final double scaledTauToaTosa = -tau_rayl_toa_tosa[i] * 0.5; /* 0.5 because diffuse trans */
            trans_rayl_down[i] = Math.exp(scaledTauToaTosa / cos_teta_sun);
            trans_rayl_up[i] = Math.exp(scaledTauToaTosa / cos_teta_view);
        }

        /* Rayleigh path radiance of correction layer */

        for (int i = 0; i < lrcPath.length; i++) {
            lrcPath[i] = ed_toa[i] * tau_rayl_toa_tosa[i] * trans_oz_tosa_down[i]
                             * phase_rayl / (4 * Math.PI * cos_teta_view * cos_teta_sun);
        }

        /* Calculate Ed_tosa */

        for (int i = 0; i < edTosa.length; i++) {
            edTosa[i] = ed_toa[i] * trans_oz_toa_tosa_down[i] * trans_rayl_down[i];
        }

        /* compute path radiance difference for tosa without - with smile */
        for (int i = 0; i < lTosa.length; i++) {
            /* Calculate L_tosa */
            lTosa[i] = (lToa[i] - lrcPath[i]*trans_oz_tosa_up[i])/ trans_oz_toa_tosa_up[i];
           /* Calculate Lsat_tosa radiance reflectance as input to NN */
            rlTosa[i] = lTosa[i] / edTosa[i];
        }

        return rlTosa;
    }

    private static double[] retrieveToaFrom(double[] values) {
        double[] toa = new double[12];
        System.arraycopy(values, 0, toa, 0, 10);
        System.arraycopy(values, 11, toa, 10, 2);
        return toa;
    }

}
