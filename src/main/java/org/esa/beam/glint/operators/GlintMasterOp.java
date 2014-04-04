package org.esa.beam.glint.operators;

import org.esa.beam.collocation.CollocateOp;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;

import java.util.HashMap;
import java.util.Map;

/**
 * GLINT main operator setting up:
 * - FLINT algorithm
 * - ...
 *
 * @author Olaf Danne
 * @version $Revision: 5340 $ $Date: 2009-05-27 18:30:05 +0200 (Mi, 27 Mai 2009) $
 */
@OperatorMetadata(alias = "glint.GlintMaster",
                  version = "1.2.1",
                  authors = "Olaf Danne",
                  copyright = "(c) 2008 by Brockmann Consult",
                  description = "This operator sets up other operators, currently just for FLINT algorithm.")
public class GlintMasterOp extends Operator {

    @SourceProduct(alias = "sourceMeris",
                   label = "Name (MERIS L1b product)",
                   description = "The MERIS L1b source product.")
    Product merisSourceProduct;

    @SourceProduct(alias = "sourceAATSR",
                   label = "Name (AATSR L1b product)",
                   description = "The AATSR L1b source product.")
    Product aatsrSourceProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Parameter(defaultValue = "false",
               label = "Water Vapour")
    boolean writeWaterVapour;

    @Parameter(defaultValue = "false",
               label = "Transmission at 3.7um")
    boolean writeTransmission37;

    @Parameter(defaultValue = "false",
               label = "Transmission at 1.6um")
    boolean writeTransmission16;

    @Parameter(defaultValue = "false",
               label = "Thermal Part of Radiance at 3.7um")
    boolean writeThermalPart37;

    @Parameter(defaultValue = "false",
               label = "Solar Part of Radiance at 3.7um")
    boolean writeSolarPart37;

    @Parameter(defaultValue = "false",
               label = "Solar Part of Radiance at 3.7um (AATSR Units)")
    boolean writeSolarPart37AatsrUnits;

    @Parameter(defaultValue = "false",
               label = "Number of Effective Windspeeds")
    boolean writeNumberEffectiveWindspeeds;

    @Parameter(defaultValue = "false",
               label = "Effective Windspeed 1")
    boolean writeEffectiveWindspeed1;

    @Parameter(defaultValue = "false",
               label = "Effective Windspeed 2")
    boolean writeEffectiveWindspeed2;

    @Parameter(defaultValue = "false",
               label = "Radiance 1")
    boolean writeRadiance1;

    @Parameter(defaultValue = "false",
               label = "Radiance 2")
    boolean writeRadiance2;

    @Parameter(defaultValue = "false",
               label = "Effective Windspeed (Final Result)")
    boolean writeEffectiveWindspeedFinal;

    @Parameter(defaultValue = "true",
               label = "Normalized Radiance (Final Result)")
    boolean writeNormalizedRadianceFinal;

    public void initialize() throws OperatorException {
        // create collocation product...
        Map<String, Product> collocateInput = new HashMap<String, Product>(2);
        collocateInput.put("masterProduct", merisSourceProduct);
        collocateInput.put("slaveProduct", aatsrSourceProduct);
        Product collocateProduct =
                GPF.createProduct(OperatorSpi.getOperatorAlias(CollocateOp.class), GPF.NO_PARAMS, collocateInput);

        Map<String, Product> flintInput = new HashMap<String, Product>(1);
        flintInput.put("l1bCollocate", collocateProduct);
        Map<String, Object> flintParameters = new HashMap<String, Object>(13);
        flintParameters.put("writeWaterVapour", writeWaterVapour);
        flintParameters.put("writeTransmission37", writeTransmission37);
        flintParameters.put("writeTransmission16", writeTransmission16);
        flintParameters.put("writeThermalPart37", writeThermalPart37);
        flintParameters.put("writeSolarPart37", writeSolarPart37);
        flintParameters.put("writeSolarPart37AatsrUnits", writeSolarPart37AatsrUnits);
        flintParameters.put("writeNumberEffectiveWindspeeds", writeNumberEffectiveWindspeeds);
        flintParameters.put("writeEffectiveWindspeed1", writeEffectiveWindspeed1);
        flintParameters.put("writeEffectiveWindspeed2", writeEffectiveWindspeed2);
        flintParameters.put("writeRadiance1", writeRadiance1);
        flintParameters.put("writeRadiance2", writeRadiance2);
        flintParameters.put("writeEffectiveWindspeedFinal", writeEffectiveWindspeedFinal);
        flintParameters.put("writeNormalizedRadianceFinal", writeNormalizedRadianceFinal);
        Product flintProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(FlintOp.class), flintParameters, flintInput);

        targetProduct = flintProduct;
    }

    /**
     * This method creates the target product
     */
    private void createTargetProduct() {
        // todo: implement
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlintMasterOp.class);
        }
    }
}
