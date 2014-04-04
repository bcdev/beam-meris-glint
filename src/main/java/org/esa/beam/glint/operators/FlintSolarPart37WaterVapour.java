/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.glint.operators;

import com.bc.jnn.JnnException;
import com.bc.jnn.JnnNet;

import java.io.IOException;


class FlintSolarPart37WaterVapour {

    private static final float WATER_VAPOUR_STANDARD_VALUE = 2.8f;

    private JnnNet neuralNetWv;

    @Override
    protected FlintSolarPart37WaterVapour clone()  {
        FlintSolarPart37WaterVapour waterVapour = new FlintSolarPart37WaterVapour();
        waterVapour.neuralNetWv = neuralNetWv.clone();
        return waterVapour;
    }

    void loadFlintAuxData() throws IOException, JnnException {
        neuralNetWv = FlintAuxData.getInstance().loadNeuralNet(FlintAuxData.NEURAL_NET_WV_OCEAN_MERIS_FILE_NAME);
    }

    //
    //  This method computes the water vapour column to correct for transmission in 3.7um (and 1.6um) channel..
    // Computation by FUB neural net.
    // (breadboard step 1.b.1)
    //
    float computeWaterVapour(float zonalWind, float meridionalWind,
                                       float merisAzimuthDifference,
                                     float merisViewZenith, float merisSunZenith,
                                     float merisRadiance14, float merisRadiance15) {
        float waterVapour = WATER_VAPOUR_STANDARD_VALUE;   // standard value

        final double[] nnIn = new double[5];
        final double[] nnOut = new double[1];

        double windSpeed = Math.sqrt(zonalWind*zonalWind + meridionalWind*meridionalWind);

        // apply FUB NN...
        nnIn[0] = windSpeed;
        nnIn[1] = Math.cos(Math.toRadians(merisAzimuthDifference))*
                  Math.sin(Math.toRadians(merisViewZenith));  // angles in degree!
        nnIn[2] = Math.cos(Math.toRadians(merisViewZenith));  // angle in degree!
        nnIn[3] = Math.cos(Math.toRadians(merisSunZenith));  // angle in degree!
        nnIn[4] = Math.log(Math.max(merisRadiance15, 1.0E-4)/Math.max(merisRadiance14, 1.0E-4));

        float[][] nnLimits = new float[][]{{3.75e-02f, 1.84e+01f},
                                           {-6.33e-01f, 6.31e-01f},
                                           {7.73e-01f, 1.00e+00f},
                                           {1.60e-01f, 9.26e-01f},
                                           {-6.98e-01f, 7.62e+00f}};

        for (int i=0; i<nnIn.length; i++) {
            if (nnIn[i] >= nnLimits[i][0] && nnIn[i] >= nnLimits[i][1]) {
                // otherwise do not apply NN, keep WV to standard value
                neuralNetWv.process(nnIn, nnOut);
                waterVapour = (float) nnOut[0];
            }
        }

        return waterVapour;
    }
}
