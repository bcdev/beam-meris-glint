package org.esa.beam.glint.operators;

import com.bc.jnn.Jnn;
import com.bc.jnn.JnnException;
import com.bc.jnn.JnnNet;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.glint.util.GlintHelpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

/**
 * @author Olaf Danne
 * @version $Revision: 5451 $ $Date: 2009-06-05 18:36:49 +0200 (Fr, 05 Jun 2009) $
 */
public class FlintAuxData {

    private static FlintAuxData instance;
    
    private static final String AATSR_SPECTRAL_RESPONSE37_FILE_NAME = "aatsr_ir37.dat";
    // make sure that the following value corresponds to the file above
    private static final int AATSR_SPECTRAL_RESPONSE37_TABLE_LENGTH = 255;
    private static final int AATSR_SPECTRAL_RESPONSE37_TABLE_HEADER_LINES = 3;

    private static final String CAHALAN_FILE_NAME = "cahalan.d";
    // make sure that the following value corresponds to the file above
    private static final int CAHALAN_TABLE_LENGTH = 2496;

    private static final String TEMP2RAD_FILE_NAME = "temp_to_rad_36.d";
    // make sure that the following value corresponds to the file above
    private static final int TEMP2RAD_TABLE_LENGTH = 200;

    private static final String A_COEFF_0370_FILE_NAME = "ck_flex_cd_AATSR_sfp1000_03700.00.and.5.ck.koeff.d";
    private static final String A_WEIGHT_0370_FILE_NAME = "ck_flex_cd_AATSR_sfp1000_03700.00.and.5.ck.weight.d";
    private static final String H_COEFF_0370_FILE_NAME = "ck_flex_cd_AATSR_sfp1000_03700.00.h2o.5.ck.koeff.d";
    private static final String H_WEIGHT_0370_FILE_NAME = "ck_flex_cd_AATSR_sfp1000_03700.00.h2o.5.ck.weight.d";

    private static final String A_COEFF_1600_FILE_NAME = "ck_flex_cd_AATSR_sfp1000_01600.00.and.4.ck.koeff.d";
    private static final String A_WEIGHT_1600_FILE_NAME = "ck_flex_cd_AATSR_sfp1000_01600.00.and.4.ck.weight.d";
    private static final String H_COEFF_1600_FILE_NAME = "ck_flex_cd_AATSR_sfp1000_01600.00.h2o.4.ck.koeff.d";
    private static final String H_WEIGHT_1600_FILE_NAME = "ck_flex_cd_AATSR_sfp1000_01600.00.h2o.4.ck.weight.d";

    public static final String NEURAL_NET_WV_OCEAN_MERIS_FILE_NAME = "wv_ocean_meris.nna";
    public static final String NEURAL_NET_WINDSPEED_FILE_NAME = "cm_ws_to_gauss2d.nna";


    public static FlintAuxData getInstance() {
        if (instance == null) {
            instance = new FlintAuxData();
        }

        return instance;
    }

    public JnnNet loadNeuralNet(String filename) throws IOException, JnnException {
        InputStream inputStream = FlintOp.class.getResourceAsStream(filename);
        final InputStreamReader reader = new InputStreamReader(inputStream);

        JnnNet neuralNet= null;

        try {
            Jnn.setOptimizing(true);
            neuralNet = Jnn.readNna(reader);
        } finally {
            reader.close();
        }
        
        return neuralNet;
    }


    public float[][] readWaterVapourCoefficients(int channel, String index) throws IOException {
       InputStream inputStream;

       float[][] tmpCoeffs;
       float[] coeffs;
       int COEFF_ROWS;
       int COEFF_COLUMNS = 8;
    
       if (channel == 37) {
            COEFF_ROWS = 45;
            tmpCoeffs = new float[COEFF_ROWS][COEFF_COLUMNS];
            coeffs = new float[COEFF_ROWS*COEFF_COLUMNS]; // final result
            if (index.toUpperCase().equals("A")) {
                inputStream = FlintOp.class.getResourceAsStream(A_COEFF_0370_FILE_NAME);
            } else if (index.toUpperCase().equals("H")) {
                inputStream = FlintOp.class.getResourceAsStream(H_COEFF_0370_FILE_NAME);
            } else {
                throw new OperatorException("Failed to read WV coefficients - index must be 'H' or 'A'.\n");
            }
       } else if (channel == 16) {
           COEFF_ROWS = 54;
            tmpCoeffs = new float[COEFF_ROWS][COEFF_COLUMNS];
            coeffs = new float[COEFF_ROWS*COEFF_COLUMNS]; // final result
            if (index.toUpperCase().equals("A")) {
                inputStream = FlintOp.class.getResourceAsStream(A_COEFF_1600_FILE_NAME);
            } else if (index.toUpperCase().equals("H")) {
                inputStream = FlintOp.class.getResourceAsStream(H_COEFF_1600_FILE_NAME);
            } else {
                throw new OperatorException("Failed to read WV coefficients - index must be 'H' or 'A'.\n");
            }
       } else {
           throw new OperatorException("Failed to read WV coefficients - channel must be '16' or '37'.\n");
       }

       BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
       StringTokenizer st;
       try {
           int lineIndex = 0;
           String line;
           while ((line = bufferedReader.readLine()) != null && lineIndex < COEFF_ROWS) {
               line = line.trim();
               st = new StringTokenizer(line, "   ", false);
               int colIndex = 0;
               while (st.hasMoreTokens() && colIndex < 8) {
                   tmpCoeffs[lineIndex][colIndex] = Float.parseFloat(st.nextToken());
                   colIndex++;
               }
               lineIndex++;
           }

           for (int i=0; i<COEFF_COLUMNS; i++) {
               for (int j=0; j<COEFF_ROWS; j++) {
                    coeffs[i*COEFF_ROWS + j] = tmpCoeffs[j][i];
               }
           }
       } catch (IOException e) {
           throw new OperatorException("Failed to load WV coefficients: \n" + e.getMessage(), e);
       } catch (NumberFormatException e) {
           throw new OperatorException("Failed to load WV coefficients: \n" + e.getMessage(), e);
       } finally {
           inputStream.close();
       }
//        return coeffs;
        return tmpCoeffs;
   }

    

    public float[] readTransmissionWeights(int channel, String index) throws IOException {
       InputStream inputStream;

       float[] coeffs;
       int COEFF_ROWS;

       if (channel == 37) {
            COEFF_ROWS = 45;
            coeffs = new float[COEFF_ROWS]; // result
            if (index.toUpperCase().equals("A")) {
                inputStream = FlintOp.class.getResourceAsStream(A_WEIGHT_0370_FILE_NAME);
            } else if (index.toUpperCase().equals("H")) {
                inputStream = FlintOp.class.getResourceAsStream(H_WEIGHT_0370_FILE_NAME);
            } else {
                throw new OperatorException("Failed to read WV weights - index must be 'H' or 'A'.\n");
            }
       } else if (channel == 16) {
            COEFF_ROWS = 54;
            coeffs = new float[COEFF_ROWS]; // result
            if (index.toUpperCase().equals("A")) {
                inputStream = FlintOp.class.getResourceAsStream(A_WEIGHT_1600_FILE_NAME);
            } else if (index.toUpperCase().equals("H")) {
                inputStream = FlintOp.class.getResourceAsStream(H_WEIGHT_1600_FILE_NAME);
            } else {
                throw new OperatorException("Failed to read WV weights - index must be 'H' or 'A'.\n");
            }
       } else {
           throw new OperatorException("Failed to read WV weights - channel must be '16' or '37'.\n");
       }

       BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
       StringTokenizer st;
       try {
           int lineIndex = 0;
           String line;
           while ((line = bufferedReader.readLine()) != null && lineIndex < COEFF_ROWS) {
               line = line.trim();
               st = new StringTokenizer(line, "   ", false);
               if (st.hasMoreTokens()) {
                   coeffs[lineIndex] = Float.parseFloat(st.nextToken());
               }
               lineIndex++;
           }
       } catch (IOException e) {
           throw new OperatorException("Failed to load WV weights: \n" + e.getMessage(), e);
       } catch (NumberFormatException e) {
           throw new OperatorException("Failed to load WV weights: \n" + e.getMessage(), e);
       } finally {
           inputStream.close();
       }
        return coeffs;
   }



    public AatsrSpectralResponse37Table createAatsrSpectralResponse37Table() throws IOException {
        final InputStream inputStream = FlintOp.class.getResourceAsStream(AATSR_SPECTRAL_RESPONSE37_FILE_NAME);
        AatsrSpectralResponse37Table aatsrSpectralResponse37Table = new AatsrSpectralResponse37Table();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringTokenizer st;
        try {

            // skip header lines
            for (int i = 0; i < AATSR_SPECTRAL_RESPONSE37_TABLE_HEADER_LINES; i++) {
                bufferedReader.readLine();
            }

            int i = 0;
            String line;
            while ((line = bufferedReader.readLine()) != null && i < AATSR_SPECTRAL_RESPONSE37_TABLE_LENGTH) {
                line = line.substring(1); // skip one blank
                line = line.trim();
                st = new StringTokenizer(line, "   ", false);

                if (st.hasMoreTokens()) {
                    // wavelengthh
                    aatsrSpectralResponse37Table.setWavelength(i, Double.parseDouble(st.nextToken()));
                }
                if (st.hasMoreTokens()) {
                    // response
                    aatsrSpectralResponse37Table.setResponse(i, Double.parseDouble(st.nextToken()));
                }
                i++;
            }
        } catch (IOException e) {
            throw new OperatorException("Failed to load AATSR Spectral Response Table: \n" + e.getMessage(), e);
        } catch (NumberFormatException e) {
            throw new OperatorException("Failed to load AATSR Spectral Response Table: \n" + e.getMessage(), e);
        } finally {
            inputStream.close();
        }
        return aatsrSpectralResponse37Table;
    }

    public CahalanTable createCahalanTable() throws IOException {
        final InputStream inputStream = FlintOp.class.getResourceAsStream(CAHALAN_FILE_NAME);
        CahalanTable cahalanTable = new CahalanTable();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringTokenizer st;
        try {
            int i = 0;
            String line;
            while ((line = bufferedReader.readLine()) != null && i < CAHALAN_TABLE_LENGTH) {
                line = line.trim();
                st = new StringTokenizer(line, "   ", false);

                if (st.hasMoreTokens()) {
                    // x (whatever that is)
                    cahalanTable.setX(i, Double.parseDouble(st.nextToken()));
                }
                if (st.hasMoreTokens()) {
                    // y
                    cahalanTable.setY(i, Double.parseDouble(st.nextToken()));
                }
                i++;
            }
        } catch (IOException e) {
            throw new OperatorException("Failed to load Cahalan Table: \n" + e.getMessage(), e);
        } catch (NumberFormatException e) {
            throw new OperatorException("Failed to load Cahalan Table: \n" + e.getMessage(), e);
        } finally {
            inputStream.close();
        }
        return cahalanTable;
    }

    public Temp2RadianceTable createTemp2RadianceTable() throws IOException {
        final InputStream inputStream = FlintOp.class.getResourceAsStream(TEMP2RAD_FILE_NAME);
        Temp2RadianceTable temp2radTable = new Temp2RadianceTable();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringTokenizer st;
        try {
            int i = 0;
            String line;
            while ((line = bufferedReader.readLine()) != null && i < TEMP2RAD_TABLE_LENGTH) {
                line = line.trim();
                st = new StringTokenizer(line, "   ", false);

                if (st.hasMoreTokens()) {
                    // temperature (K)
                    temp2radTable.setTemp(i, Double.parseDouble(st.nextToken()));
                }
                if (st.hasMoreTokens()) {
                    // radiance
                    temp2radTable.setRad(i, Double.parseDouble(st.nextToken()));
                }
                i++;
            }
        } catch (IOException e) {
            throw new OperatorException("Failed to load Temp2RadianceTable Table: \n" + e.getMessage(), e);
        } catch (NumberFormatException e) {
            throw new OperatorException("Failed to load Temp2RadianceTable Table: \n" + e.getMessage(), e);
        } finally {
            inputStream.close();
        }
        return temp2radTable;
    }


    public int getNearestCahalanTableIndex(double wavelength, double[] tableWavelengths) {
       return GlintHelpers.getNearestValueIndexInDescendingDoubleArray(wavelength, tableWavelengths);
    }

    public int getNearestTemp2RadianceTableIndex(double temp, double[] tableTemps) {
       return GlintHelpers.getNearestValueIndexInAscendingDoubleArray(temp, tableTemps);
    }

    
    /**
     * This method provides an integration of a function y(x) over the interval [x1, x3]
     * following Simpson's rule for a constant stepsize h in x direction.
     * It must be made sure that y2 = y(x2)!
     *
     * @param y1 , y(x1)
     * @param y2 , y(x2)
     * @param y3 , y(x3)
     * @param  intervalSize, x3-x1
     *
     * @return double
     */
    public double getSimpsonIntegral(double y1, double y2, double y3, double intervalSize) {
        double h = intervalSize/6.0;
        return h*(y1 + 4.0*y2 + y3);
    }

    public class AatsrSpectralResponse37Table {
        private double[] wavelength = new double[AATSR_SPECTRAL_RESPONSE37_TABLE_LENGTH];
        private double[] response = new double[AATSR_SPECTRAL_RESPONSE37_TABLE_LENGTH];

        public double[] getWavelength() {
            return wavelength;
        }

        public void setWavelength(int index, double value) {
            wavelength[index] = value;
        }

        public double[] getResponse() {
            return response;
        }

        public void setResponse(int index, double value) {
            response[index] = value;
        }
    }

    public class CahalanTable {
        // todo: clarify the meaning of the columns and give proper names
        private double[] x = new double[CAHALAN_TABLE_LENGTH];
        private double[] y = new double[CAHALAN_TABLE_LENGTH];

        public double[] getX() {
            return x;
        }

        public void setX(int index, double value) {
            x[index] = value;
        }

        public double[] getY() {
            return y;
        }

        public void setY(int index, double value) {
            y[index] = value;
        }
    }

    public class Temp2RadianceTable {
        // todo: clarify the meaning of the columns and give proper names
        private double[] temp = new double[TEMP2RAD_TABLE_LENGTH];
        private double[] rad = new double[TEMP2RAD_TABLE_LENGTH];

        public double[] getTemp() {
            return temp;
        }

        public void setTemp(int index, double value) {
            temp[index] = value;
        }

        public double[] getRad() {
            return rad;
        }

        public void setRad(int index, double value) {
            rad[index] = value;
        }
    }

}
