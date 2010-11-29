package org.esa.beam;

import org.esa.beam.atmosphere.operator.GlintCorrectionOperator;

/**
 * Created by Marco Peters.
 *
 * @author Marco Peters
 * @version $Revision: 1.2 $ $Date: 2007-07-12 15:39:22 $
 */
public class PixelData {

    public double[] toa_radiance;     /* toa radiance in W m-2 sr-1 µm-1 */
    public double[] solar_flux;     /* at toa W m-2 µm-1, incl. sun-earth distance */
    public double altitude;
    public double solzen;       /* Solar zenith angle in deg [0,90].........*/
    public double solazi;       /* Solar azimuth angle in deg [0-360I]		*/
    public double satzen;       /* Satellite zenith angle in deg [0,90]		*/
    public double satazi;       /* Satellite azimuth angle as viewed from pixel in deg [0-360I]	*/
    public double pressure;     /* Surface pressure in hPa	    	   	*/
    public double ozone;        /* Total ozone concentration in DU		*/
    public int l1Flag;          /* Flags of the L1b product     */
    public int validation;
    public int detectorIndex;

    // todo - find better name
    public double flintValue = GlintCorrectionOperator.NO_FLINT_VALUE;   /* value of the FLINT processor */

    // MERIS angles
    public double solzenMer;
    public double solaziMer;
    public double viewzenMer;
    public double viewaziMer;
}
