package org.esa.beam.atmosphere.operator;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.util.ProductUtils;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Operator for TOA reflectance computation.
 *
 * @author Marco Peters
 * @version $Revision: 2703 $ $Date: 2010-01-21 13:51:07 +0100 (Do, 21 Jan 2010) $
 * @since BEAM 4.2
 */
@SuppressWarnings({"InstanceVariableMayNotBeInitialized", "FieldCanBeLocal"})
@OperatorMetadata(alias = "Meris.AgcRad2Refl",
                  version = "1.4.5-CC",
                  internal = true,
                  authors = "Marco Peters",
                  copyright = "(c) 2008 by Brockmann Consult",
                  description = "Converts radiances into TOA reflectances.")
public class ToaReflectanceOp extends Operator {

    private static final String SOLZEN_GRID_NAME = EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME;
    private static final String TOA_REFL_PATTERN = "toa_reflec_%d";
    private static final int NO_DATA_VALUE = -1;

    @SourceProduct(alias = "input")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;


    private Map<Band, Band> bandMap;
    private Band invalidBand;

    public static ToaReflectanceOp create(Product sourceProduct) {

        final ToaReflectanceOp op = new ToaReflectanceOp();
        op.sourceProduct = sourceProduct;
        return op;
    }

    @Override
    public void initialize() throws OperatorException {
        validateSourceProduct(sourceProduct);
        targetProduct = createCompatibleProduct(sourceProduct, String.format("%s_TOA", sourceProduct.getName()),
                                                "MER_AGC_TOA_REFL");
        bandMap = new HashMap<Band, Band>(EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS);
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {

            final Band toaReflBand = targetProduct.addBand(String.format(TOA_REFL_PATTERN, i + 1),
                                                           ProductData.TYPE_FLOAT32);
            final Band radianceBand = sourceProduct.getBandAt(i);

            ProductUtils.copySpectralBandProperties(radianceBand, toaReflBand);
            toaReflBand.setNoDataValueUsed(true);
            toaReflBand.setNoDataValue(NO_DATA_VALUE);

            bandMap.put(toaReflBand, radianceBand);
        }
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);

        BandMathsOp bandArithmeticOp = BandMathsOp.createBooleanExpressionBand("l1_flags.INVALID", sourceProduct);
        invalidBand = bandArithmeticOp.getTargetProduct().getBandAt(0);

    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        checkForCancellation();
        try {
            final Band sourceBand = bandMap.get(targetBand);
            final RasterDataNode solzenGrid = sourceProduct.getRasterDataNode(SOLZEN_GRID_NAME);
            final Tile sourceTile = getSourceTile(sourceBand, targetTile.getRectangle());
            final Tile solzenTile = getSourceTile(solzenGrid, targetTile.getRectangle());
            final Tile invalidTile = getSourceTile(invalidBand, targetTile.getRectangle());

            final ProductData toaReflSamples = targetTile.getRawSamples();
            final ProductData radianceSamples = sourceTile.getRawSamples();
            final ProductData solzenSamples = solzenTile.getRawSamples();
            final ProductData invalidSamples = invalidTile.getRawSamples();
            final float solarFlux = sourceBand.getSolarFlux();

            final int height = targetTile.getHeight();
            final int width = targetTile.getWidth();
            for (int y = 0; y < height; y++) {
                final int lineIndex = y * width;
                for (int x = 0; x < width; x++) {
                    final int index = lineIndex + x;
                    if (invalidSamples.getElemBooleanAt(index)) {
                        toaReflSamples.setElemDoubleAt(index, NO_DATA_VALUE);
                    } else {
                        final double toaRadiance = sourceBand.scale(radianceSamples.getElemFloatAt(index));
                        final double solzen = solzenGrid.scale(solzenSamples.getElemFloatAt(index));
                        final double sample = toaRadiance / (solarFlux * Math.cos(Math.toRadians(solzen)));
                        toaReflSamples.setElemDoubleAt(index, sample);
                    }
                }
            }
            targetTile.setRawSamples(toaReflSamples);
        } finally {
            pm.done();
        }

    }

    @Override
    public void dispose() {
        if (!bandMap.isEmpty()) {
            bandMap.clear();
        }

    }

    private static void validateSourceProduct(final Product product) {
        final String missedBand = validateProductBands(product);
        if (!missedBand.isEmpty()) {
            String message = MessageFormat.format("Missing required band: {0}", missedBand);
            throw new OperatorException(message);
        }
        List<String> sourceNodeNameList = new ArrayList<String>();
        sourceNodeNameList.addAll(Arrays.asList(product.getTiePointGridNames()));
        sourceNodeNameList.addAll(Arrays.asList(product.getBandNames()));
        if (!sourceNodeNameList.contains(SOLZEN_GRID_NAME)) {
            String message = MessageFormat.format("Missing required raster: {0}", SOLZEN_GRID_NAME);
            throw new OperatorException(message);
        }

    }

    private static String validateProductBands(Product product) {
        List<String> sourceBandNameList = Arrays.asList(product.getBandNames());
        for (String bandName : EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES) {
            if (!sourceBandNameList.contains(bandName)) {
                return bandName;
            }
        }
        if (!sourceBandNameList.contains(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME)) {
            return EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME;
        }

        return "";
    }

    public static Product createCompatibleProduct(Product sourceProduct, String name, String type) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product tempProduct = new Product(name, type, sceneWidth, sceneHeight);
        ProductUtils.copyTiePointGrids(sourceProduct, tempProduct);
        // copy geo-coding to the output product
        ProductUtils.copyGeoCoding(sourceProduct, tempProduct);
        tempProduct.setStartTime(sourceProduct.getStartTime());
        tempProduct.setEndTime(sourceProduct.getEndTime());
        return tempProduct;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ToaReflectanceOp.class);
        }
    }

}
