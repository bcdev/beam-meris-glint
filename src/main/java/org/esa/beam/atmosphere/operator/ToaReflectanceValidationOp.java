package org.esa.beam.atmosphere.operator;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.operators.common.BandArithmeticOp;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Operator for validation of TOA reflectances.
 *
 * @author Marco Peters
 * @version $Revision: 2703 $ $Date: 2010-01-21 13:51:07 +0100 (Do, 21 Jan 2010) $
 * @since BEAM 4.2
 */
@SuppressWarnings({"InstanceVariableMayNotBeInitialized", "FieldCanBeLocal"})
@OperatorMetadata(alias = "Meris.AgcToaReflValid",
                  version = "0.1",
                  internal = true,
                  authors = "Marco Peters",
                  copyright = "(c) 2007 by Brockmann Consult",
                  description = "Validation of TOA reflectances.")
public class ToaReflectanceValidationOp extends Operator {
    public static final int LAND_FLAG_MASK = 0x01;
    public static final int CLOUD_ICE_FLAG_MASK = 0x02;
    public static final int RLTOA_OOR_FLAG_MASK = 0x04;

    @SourceProduct(alias = "input")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(defaultValue = "toa_reflec_10 > toa_reflec_6 AND toa_reflec_13 > 0.0475", label = "Land detection expression", notEmpty = true, notNull = true)
    private String landExpression = "l1_flags.INVALID";
    @Parameter(defaultValue = "toa_reflec_14 > 0.2", label = "Cloud/Ice detection expression", notEmpty = true, notNull = true)
    private String cloudIceExpression = "l1_flags.INVALID";

    @Parameter(defaultValue = "toa_reflec_13 >  0.035", label = "Out of range detection expression")
    private String rlToaOorExpression;

    private Band landWaterBand;
    private Band cloudIceBand;
    private Band rlToaOorBand;


    public static ToaReflectanceValidationOp create(Product sourceProduct, String landExpression, String cloudIceExpression) {
        final ToaReflectanceValidationOp validationOp = new ToaReflectanceValidationOp();
        validationOp.sourceProduct = sourceProduct;
        validationOp.landExpression = landExpression;
        validationOp.cloudIceExpression = cloudIceExpression;
        return validationOp;
    }

    @Override
    public void initialize() throws OperatorException {
        validateSourceProduct(sourceProduct);
        targetProduct = new Product(String.format("%s_cls", sourceProduct.getName()),
                                    String.format("%s_CLS", sourceProduct.getProductType()),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());
        final Product reflProduct = ToaReflectanceOp.create(sourceProduct).getTargetProduct();

        BandArithmeticOp landWaterOp = BandArithmeticOp.createBooleanExpressionBand(landExpression, reflProduct);
        landWaterBand = landWaterOp.getTargetProduct().getBandAt(0);

        BandArithmeticOp cloudIceOp = BandArithmeticOp.createBooleanExpressionBand(cloudIceExpression, reflProduct);
        cloudIceBand = cloudIceOp.getTargetProduct().getBandAt(0);

        BandArithmeticOp rlToaOorOp = BandArithmeticOp.createBooleanExpressionBand(rlToaOorExpression, reflProduct);
        rlToaOorBand = rlToaOorOp.getTargetProduct().getBandAt(0);

        final FlagCoding flagCoding = new FlagCoding("rlToa_flags");
        flagCoding.addFlag("land", LAND_FLAG_MASK, "Pixel is land");
        flagCoding.addFlag("cloud_ice", CLOUD_ICE_FLAG_MASK, "Pixel is cloud or ice");
        flagCoding.addFlag("rlToa_OOR", RLTOA_OOR_FLAG_MASK, "RlToa is Out Of Range");
        targetProduct.getFlagCodingGroup().add(flagCoding);

        Band classBand = targetProduct.addBand("rlToa_flags", ProductData.TYPE_INT8);
        classBand.setNoDataValue(-1);
        classBand.setNoDataValueUsed(true);
        classBand.setSampleCoding(flagCoding);

    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        try {
            pm.beginTask("Computing TOA_Reflectance classification", 4 * targetTile.getHeight());
            ProductData targetSamples = targetTile.getRawSamples();

            final Tile landWaterTile = getSourceTile(landWaterBand, targetTile.getRectangle(),
                                                   SubProgressMonitor.create(pm, targetTile.getHeight()));
            final Tile cloudIceTile = getSourceTile(cloudIceBand, targetTile.getRectangle(),
                                                   SubProgressMonitor.create(pm, targetTile.getHeight()));
            final Tile rlToaOorTile = getSourceTile(rlToaOorBand, targetTile.getRectangle(),
                                                   SubProgressMonitor.create(pm, targetTile.getHeight()));
            final ProductData landWaterSamples = landWaterTile.getRawSamples();
            final ProductData cloudIceSamples = cloudIceTile.getRawSamples();
            final ProductData rlToaOorSamples = rlToaOorTile.getRawSamples();


            for (int y = 0; y < targetTile.getHeight(); y++) {
                checkForCancelation(pm);
                final int lineIndex = y * targetTile.getWidth();

                for (int x = 0; x < targetTile.getWidth(); x++) {
                    byte value = 0;
                    final int index = lineIndex + x;
                    if(landWaterSamples.getElemBooleanAt(index)) {
                        value |= LAND_FLAG_MASK;
                    }
                    if(cloudIceSamples.getElemBooleanAt(index)) {
                        value |= CLOUD_ICE_FLAG_MASK;
                    }
                    if(rlToaOorSamples.getElemBooleanAt(index)) {
                        value |= RLTOA_OOR_FLAG_MASK;
                    }
                    targetSamples.setElemIntAt(index, value);
                }
                pm.worked(1);
            }
            targetTile.setRawSamples(targetSamples);
        } finally {
            pm.done();
        }


    }

    private static void validateSourceProduct(final Product product) {
        final String missedBand = validateProductBands(product);
        if (!missedBand.isEmpty()) {
            String message = MessageFormat.format("Missing required band: {0}", missedBand);
            throw new OperatorException(message);
        }
        final String missedTPG = validateProductTpgs(product);
        if (!missedTPG.isEmpty()) {
            String message = MessageFormat.format("Missing required tie-point grid: {0}", missedTPG);
            throw new OperatorException(message);
        }
    }

    private static String validateProductBands(Product product) {
        List<String> sourceBandNameList = Arrays.asList(product.getBandNames());
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            String bandName = EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i];
            if (!sourceBandNameList.contains(bandName)) {
                return bandName;
            }
        }
        if (!sourceBandNameList.contains(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME)) {
            return EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME;
        }

        return "";
    }

    private static String validateProductTpgs(Product product) {
        List<String> sourceTpgNameList = Arrays.asList(product.getTiePointGridNames());
        for (String tpgName : EnvisatConstants.MERIS_TIE_POINT_GRID_NAMES) {
            if (!sourceTpgNameList.contains(tpgName)) {
                return tpgName;
            }
        }

        return "";
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ToaReflectanceValidationOp.class);
        }
    }

}
