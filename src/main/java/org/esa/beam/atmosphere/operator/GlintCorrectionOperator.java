package org.esa.beam.atmosphere.operator;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.PixelData;
import org.esa.beam.collocation.CollocateOp;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.glint.operators.FlintOp;
import org.esa.beam.meris.radiometry.smilecorr.SmileCorrectionAuxdata;
import org.esa.beam.nn.NNffbpAlphaTabFast;
import org.esa.beam.util.ProductUtils;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.Raster;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.esa.beam.dataio.envisat.EnvisatConstants.*;

/**
 * Main operator for the AGC Glint correction.
 *
 * @author Marco Peters, Olaf Danne
 * @version $Revision: 2703 $ $Date: 2010-01-21 13:51:07 +0100 (Do, 21 Jan 2010) $
 */
@SuppressWarnings({"InstanceVariableMayNotBeInitialized", "MismatchedReadAndWriteOfArray"})
@OperatorMetadata(alias = "Meris.GlintCorrection",
                  version = "1.2.3",
                  authors = "Roland Doerffer (HZG), Marco Peters and Olaf Danne (BC)",
                  copyright = "(c) 2008 by Brockmann Consult",
                  description = "MERIS atmospheric correction using a neural net.")
public class GlintCorrectionOperator extends Operator {

    public static final String GLINT_CORRECTION_VERSION = "1.2.3";

    private static final String AGC_FLAG_BAND_NAME = "agc_flags";
    private static final String RADIANCE_MERIS_BAND_NAME = "result_radiance_rr89";
    private static final String VALID_EXPRESSION = String.format("!%s.INVALID", AGC_FLAG_BAND_NAME);
    private static final String MERIS_ATMOSPHERIC_NET_NAME = "atmo_correct_meris/20x25x45_55990.1.net";
    private static final String FLINT_ATMOSPHERIC_NET_NAME = "atmo_correct_flint/25x30x40_6936.3.net";
    private static final String NORMALIZATION_NET_NAME = "atmo_normalization/90_2.8.net";
    private static final String ATMO_AANN_NET = "atmo_aann/12x5x12_318.4.net";

    private static final String[] REQUIRED_MERIS_TPG_NAMES = {
            MERIS_SUN_ZENITH_DS_NAME,
            MERIS_SUN_AZIMUTH_DS_NAME,
            MERIS_VIEW_ZENITH_DS_NAME,
            MERIS_VIEW_AZIMUTH_DS_NAME,
            MERIS_DEM_ALTITUDE_DS_NAME,
            "atm_press",
            "ozone",
    };

    private static final String[] REQUIRED_AATSR_TPG_NAMES = AATSR_TIE_POINT_GRID_NAMES;

    private static final String ANG_443_865 = "ang_443_865";
    private static final String TAU_550 = "tau_550";
    private static final String TAU_778 = "tau_778";
    private static final String TAU_865 = "tau_865";
    private static final String GLINT_RATIO = "glint_ratio";
    private static final String FLINT_VALUE = "flint_value";
    private static final String BTSM = "b_tsm";
    private static final String ATOT = "a_tot";
    private static final String[] TOSA_REFLEC_BAND_NAMES = {
            "tosa_reflec_1", "tosa_reflec_2", "tosa_reflec_3", "tosa_reflec_4", "tosa_reflec_5",
            "tosa_reflec_6", "tosa_reflec_7", "tosa_reflec_8", "tosa_reflec_9", "tosa_reflec_10",
            null,
            "tosa_reflec_12", "tosa_reflec_13",
            null, null
    };
    private static final String[] AUTO_TOSA_REFLEC_BAND_NAMES = {
            "tosa_reflec_auto_1", "tosa_reflec_auto_2", "tosa_reflec_auto_3", "tosa_reflec_auto_4",
            "tosa_reflec_auto_5", "tosa_reflec_auto_6", "tosa_reflec_auto_7", "tosa_reflec_auto_8",
            "tosa_reflec_auto_9", "tosa_reflec_auto_10",
            null,
            "tosa_reflec_auto_12", "tosa_reflec_auto_13",
            null, null
    };
    private static final String TOSA_QUALITY_INDICATOR_BAND_NAME = "tosa_quality_indicator";
    private static final String[] REFLEC_BAND_NAMES = {
            "reflec_1", "reflec_2", "reflec_3", "reflec_4", "reflec_5",
            "reflec_6", "reflec_7", "reflec_8", "reflec_9", "reflec_10",
            null,
            "reflec_12", "reflec_13",
            null, null
    };
    private static final String[] NORM_REFLEC_BAND_NAMES = {
            "norm_refl_1", "norm_refl_2", "norm_refl_3", "norm_refl_4", "norm_refl_5",
            "norm_refl_6", "norm_refl_7", "norm_refl_8", "norm_refl_9", "norm_refl_10",
            null,
            "norm_refl_12", "norm_refl_13",
            null, null
    };
    private static final String[] PATH_BAND_NAMES = {
            "path_1", "path_2", "path_3", "path_4", "path_5",
            "path_6", "path_7", "path_8", "path_9", "path_10",
            null,
            "path_12", "path_13",
            null, null
    };
    private static final String[] TRANS_BAND_NAMES = {
            "trans_1", "trans_2", "trans_3", "trans_4", "trans_5",
            "trans_6", "trans_7", "trans_8", "trans_9", "trans_10",
            null,
            "trans_12", "trans_13",
            null, null
    };

    @SourceProduct(label = "MERIS L1b input product", description = "The MERIS L1b input product.")
    private Product merisProduct;

    @SourceProduct(label = "AATSR L1b input product", description = "The AATSR L1b input product.",
                   optional = true)
    private Product aatsrProduct;

    private Product flintProduct;

    @TargetProduct(description = "The atmospheric corrected output product.")
    private Product targetProduct;

    @Parameter(defaultValue = "false",
               label = "Perform Smile-effect correction",
               description = "Whether to perform Smile-effect correction.")
    private boolean doSmileCorrection;

    @Parameter(defaultValue = "true", label = "Output TOSA reflectance",
               description = "Toggles the output of Top of Standard Atmosphere reflectance.")
    private boolean outputTosa;

    @Parameter(defaultValue = "false", label = "Output TOSA reflectance of auto assoc. neural net",
               description = "Toggles the output of Top of Standard Atmosphere reflectance calculated by an auto associative neural net.")
    private boolean outputAutoTosa;

    @Parameter(defaultValue = "false",
               label = "Output normalised bidirectional reflectances",
               description = "Toggles the output of normalised reflectances.")
    private boolean outputNormReflec;

    @Parameter(defaultValue = "true", label = "Output water leaving reflectance",
               description = "Toggles the output of water leaving reflectance.")
    private boolean outputReflec;

    @Parameter(defaultValue = "RADIANCE_REFLECTANCES", valueSet = {"RADIANCE_REFLECTANCES", "IRRADIANCE_REFLECTANCES"},
               label = "Output water leaving reflectance as",
               description = "Select if reflectances shall be written as radiances or irradiances. " +
                             "The irradiances are compatible with standard MERIS product.")
    private ReflectanceEnum outputReflecAs;

    @Parameter(defaultValue = "true", label = "Output path reflectance",
               description = "Toggles the output of water leaving path reflectance.")
    private boolean outputPath;

    @Parameter(defaultValue = "true", label = "Output transmittance",
               description = "Toggles the output of downwelling irradiance transmittance.")
    private boolean outputTransmittance;

    @Parameter(defaultValue = "false",
               label = "Derive water leaving reflectance from path reflectance",
               description = "Switch between computation of water leaving reflectance from path reflectance and direct use of neural net output.")
    private boolean deriveRwFromPath;

    @Parameter(defaultValue = "toa_reflec_10 > toa_reflec_6 AND toa_reflec_13 > 0.0475",
               label = "Land detection expression",
               description = "The arithmetic expression used for land detection.",
               notEmpty = true, notNull = true)
    private String landExpression;

    @Parameter(defaultValue = "toa_reflec_14 > 0.2",
               label = "Cloud/Ice detection expression",
               description = "The arithmetic expression used for cloud/ice detection.",
               notEmpty = true, notNull = true)
    private String cloudIceExpression;

    @Parameter(label = "MERIS net (full path required for other than default)",
               defaultValue = MERIS_ATMOSPHERIC_NET_NAME,
               description = "The file of the atmospheric net to be used instead of the default neural net.",
               notNull = false)
    private File atmoNetMerisFile;

    @Parameter(defaultValue = "false", label = "Use FLINT value in neural net (requires AATSR L1b source product)",
               description = "Toggles the usage of a FLINT value in neural net.")
    private boolean useFlint;

    @Parameter(label = "FLINT net (full path required for other than default)",
               defaultValue = FLINT_ATMOSPHERIC_NET_NAME,
               description = "The file of the atmospheric net to be used instead of the default neural net.",
               notNull = false)
    private File atmoNetFlintFile;

    private Band validationBand;

    public static final double NO_FLINT_VALUE = -1.0;
    private String merisNeuralNetString;
    private String flintNeuralNetString;
    private String normalizationNeuralNetString;
    private String atmoAaNeuralNetString;
    private SmileCorrectionAuxdata smileAuxData;
    private RasterDataNode l1FlagsNode;
    private RasterDataNode solzenNode;
    private RasterDataNode solaziNode;
    private RasterDataNode satzenNode;
    private RasterDataNode sataziNode;
    private RasterDataNode detectorNode;
    private RasterDataNode altitudeNode;
    private RasterDataNode pressureNode;
    private RasterDataNode ozoneNode;
    private Band[] spectralNodes;
    private int nadirColumnIndex;
    private boolean isFullResolution;


    @Override
    public void initialize() throws OperatorException {
        validateMerisProduct(merisProduct);
        if (useFlint && aatsrProduct == null) {
            throw new OperatorException("Missing required AATSR L1b product for FLINT computation.");
        }
        validateAatsrProduct(aatsrProduct);

        if (useFlint && aatsrProduct != null) {
            // create collocation product...
            Map<String, Product> collocateInput = new HashMap<>(2);
            collocateInput.put("masterProduct", merisProduct);
            collocateInput.put("slaveProduct", aatsrProduct);
            Product collocateProduct =
                    GPF.createProduct(OperatorSpi.getOperatorAlias(CollocateOp.class), GPF.NO_PARAMS, collocateInput);

            // create FLINT product
            Map<String, Product> flintInput = new HashMap<>(1);
            flintInput.put("l1bCollocate", collocateProduct);
            Map<String, Object> flintParameters = new HashMap<>();
            flintProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(FlintOp.class), flintParameters, flintInput);
            validateFlintProduct(flintProduct);
        }

        l1FlagsNode = merisProduct.getRasterDataNode(MERIS_L1B_FLAGS_DS_NAME);
        solzenNode = merisProduct.getRasterDataNode(MERIS_SUN_ZENITH_DS_NAME);
        solaziNode = merisProduct.getRasterDataNode(MERIS_SUN_AZIMUTH_DS_NAME);
        satzenNode = merisProduct.getRasterDataNode(MERIS_VIEW_ZENITH_DS_NAME);
        sataziNode = merisProduct.getRasterDataNode(MERIS_VIEW_AZIMUTH_DS_NAME);
        detectorNode = merisProduct.getRasterDataNode(MERIS_DETECTOR_INDEX_DS_NAME);
        altitudeNode = merisProduct.getRasterDataNode(MERIS_DEM_ALTITUDE_DS_NAME);
        pressureNode = merisProduct.getRasterDataNode("atm_press");
        ozoneNode = merisProduct.getRasterDataNode("ozone");
        spectralNodes = new Band[MERIS_L1B_SPECTRAL_BAND_NAMES.length];
        for (int i = 0; i < MERIS_L1B_SPECTRAL_BAND_NAMES.length; i++) {
            spectralNodes[i] = merisProduct.getBand(MERIS_L1B_SPECTRAL_BAND_NAMES[i]);
        }

        final int rasterHeight = merisProduct.getSceneRasterHeight();
        final int rasterWidth = merisProduct.getSceneRasterWidth();

        Product outputProduct = new Product(merisProduct.getName() + "_AC", "MERIS_L2_AC", rasterWidth, rasterHeight);
        outputProduct.setStartTime(merisProduct.getStartTime());
        outputProduct.setEndTime(merisProduct.getEndTime());
        ProductUtils.copyMetadata(merisProduct, outputProduct);
        ProductUtils.copyTiePointGrids(merisProduct, outputProduct);
        ProductUtils.copyGeoCoding(merisProduct, outputProduct);
        // copy altitude band if it exists and 'beam.envisat.usePixelGeoCoding' is set to true
        if (Boolean.getBoolean("beam.envisat.usePixelGeoCoding") &&
            merisProduct.containsBand(EnvisatConstants.MERIS_AMORGOS_L1B_ALTIUDE_BAND_NAME)) {
            ProductUtils.copyBand(EnvisatConstants.MERIS_AMORGOS_L1B_ALTIUDE_BAND_NAME, merisProduct, outputProduct, true);
        }

        setTargetProduct(outputProduct);

        addTargetBands(outputProduct);

        Band agcFlagsBand = outputProduct.addBand(AGC_FLAG_BAND_NAME, ProductData.TYPE_UINT16);
        final FlagCoding agcFlagCoding = createAgcFlagCoding();
        agcFlagsBand.setSampleCoding(agcFlagCoding);
        outputProduct.getFlagCodingGroup().add(agcFlagCoding);
        addAgcMasks(outputProduct);

        final ToaReflectanceValidationOp validationOp = ToaReflectanceValidationOp.create(merisProduct,
                                                                                          landExpression,
                                                                                          cloudIceExpression);
        validationBand = validationOp.getTargetProduct().getBandAt(0);

        InputStream merisNeuralNetStream = getNeuralNetStream(MERIS_ATMOSPHERIC_NET_NAME, atmoNetMerisFile);
        merisNeuralNetString = readNeuralNetFromStream(merisNeuralNetStream);

        if (useFlint && aatsrProduct != null) {
            InputStream neuralNetStream = getNeuralNetStream(FLINT_ATMOSPHERIC_NET_NAME, atmoNetFlintFile);
            flintNeuralNetString = readNeuralNetFromStream(neuralNetStream);
        }
        if (outputNormReflec) {
            final InputStream neuralNetStream = getClass().getResourceAsStream(NORMALIZATION_NET_NAME);
            normalizationNeuralNetString = readNeuralNetFromStream(neuralNetStream);
        }
        final InputStream neuralNetStream = getClass().getResourceAsStream(ATMO_AANN_NET);
        atmoAaNeuralNetString = readNeuralNetFromStream(neuralNetStream);
        if (doSmileCorrection) {
            try {
                smileAuxData = SmileCorrectionAuxdata.loadAuxdata(merisProduct.getProductType());
            } catch (IOException e) {
                throw new OperatorException("Not able to load auxiliary data for SMILE correction.", e);
            }
        }
        nadirColumnIndex = MerisFlightDirection.findNadirColumnIndex(merisProduct);
        isFullResolution = isProductMerisFullResolution(merisProduct);

        ProductUtils.copyFlagBands(merisProduct, outputProduct, true);

        // copy detector index band
        if (merisProduct.containsBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME)) {
            ProductUtils.copyBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME, merisProduct, outputProduct, true);
        }
        setTargetProduct(outputProduct);
    }


    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws
                                                                                                             OperatorException {
        pm.beginTask("Correcting atmosphere...", targetRectangle.height);
        try {
            final Map<String, ProductData> merisSampleDataMap = preLoadMerisSources(targetRectangle);
            final Map<String, ProductData> targetSampleDataMap = getTargetSampleData(targetTiles);

            NNffbpAlphaTabFast normalizationNet = null;
            if (outputNormReflec) {
                normalizationNet = new NNffbpAlphaTabFast(normalizationNeuralNetString);
            }

            NNffbpAlphaTabFast autoAssocNet = new NNffbpAlphaTabFast(atmoAaNeuralNetString);

            GlintCorrection merisGlintCorrection = new GlintCorrection(new NNffbpAlphaTabFast(merisNeuralNetString),
                                                                       smileAuxData, normalizationNet, autoAssocNet,
                                                                       outputReflecAs);
            GlintCorrection aatsrFlintCorrection = null;
            if (useFlint && flintProduct != null) {
                aatsrFlintCorrection = new GlintCorrection(new NNffbpAlphaTabFast(flintNeuralNetString), smileAuxData,
                                                           normalizationNet, autoAssocNet, outputReflecAs);
            }

            for (int y = 0; y < targetRectangle.getHeight(); y++) {
                checkForCancellation();
                final int lineIndex = y * targetRectangle.width;
                final int pixelY = targetRectangle.y + y;

                for (int x = 0; x < targetRectangle.getWidth(); x++) {
                    final int pixelIndex = lineIndex + x;
                    final PixelData inputData = loadMerisPixelData(merisSampleDataMap, pixelIndex);
                    final int pixelX = targetRectangle.x + x;
                    inputData.flintValue = getFlintValue(pixelX, pixelY);
                    inputData.pixelX = pixelX;
                    inputData.pixelY = pixelY;

                    GlintResult glintResult;
                    if (aatsrFlintCorrection != null && GlintCorrection.isFlintValueValid(inputData.flintValue)) {
                        glintResult = aatsrFlintCorrection.perform(inputData, deriveRwFromPath);
                        glintResult.raiseFlag(GlintCorrection.HAS_FLINT);
                    } else {
                        glintResult = merisGlintCorrection.perform(inputData, deriveRwFromPath);
                    }

                    fillTargetSampleData(targetSampleDataMap, pixelIndex, inputData, glintResult);
                }
                pm.worked(1);
            }
            commitSampleData(targetSampleDataMap, targetTiles);
        } catch (Exception e) {
            e.printStackTrace();
            throw new OperatorException(e);
        } finally {
            pm.done();
        }

    }

    static boolean isProductMerisFullResolution(final Product product) {
        String productType = null;
        MetadataElement metadataRoot = product.getMetadataRoot();

        final MetadataElement sph = metadataRoot.getElement("SPH");
        if(sph != null) {
            final MetadataAttribute sphDescriptor = sph.getAttribute("SPH_DESCRIPTOR");
            if(sphDescriptor != null) {
                productType = sphDescriptor.getData().getElemString();
            }
        }
        if (productType == null) {
            MetadataElement globalAttributes = metadataRoot.getElement("Global_Attributes");
            if(globalAttributes != null)         {
                MetadataAttribute attribute = globalAttributes.getAttribute("product_type");
                if(attribute != null) {
                    productType = attribute.getData().getElemString();
                }
            }
        }
        if(productType == null) {
            productType = product.getProductType();
        }

        return !productType.contains("RR");
    }

    private double getFlintValue(int pixelX, int pixelY) {
        if (flintProduct == null) {
            return NO_FLINT_VALUE;
        }

        GeoPos geoPos = targetProduct.getGeoCoding().getGeoPos(new PixelPos(pixelX + 0.5f, pixelY + 0.5f), null);
        PixelPos pixelPos = flintProduct.getGeoCoding().getPixelPos(geoPos, null);
        if (!pixelPos.isValid() || pixelPos.x < 0.0f || pixelPos.y < 0.0f) {
            return NO_FLINT_VALUE;
        }
        Band flintBand = flintProduct.getBand(RADIANCE_MERIS_BAND_NAME);
        Rectangle rect = new Rectangle((int) Math.floor(pixelPos.x), (int) Math.floor(pixelPos.y), 1, 1);
        Raster data = flintBand.getGeophysicalImage().getData(rect);
        if (!flintBand.isPixelValid(rect.x, rect.y)) {
            return NO_FLINT_VALUE;
        }
        return data.getSampleDouble(rect.x, rect.y, 0);
    }

    private static Map<String, ProductData> getTargetSampleData(Map<Band, Tile> targetTiles) {
        final Map<String, ProductData> map = new HashMap<>(targetTiles.size());
        for (Map.Entry<Band, Tile> bandTileEntry : targetTiles.entrySet()) {
            final Band band = bandTileEntry.getKey();
            final Tile tile = bandTileEntry.getValue();
            map.put(band.getName(), tile.getRawSamples());
        }
        return map;
    }

    private static void commitSampleData(Map<String, ProductData> sampleDataMap, Map<Band, Tile> targetTiles) {
        for (Map.Entry<Band, Tile> bandTileEntry : targetTiles.entrySet()) {
            final Band band = bandTileEntry.getKey();
            final Tile tile = bandTileEntry.getValue();
            tile.setRawSamples(sampleDataMap.get(band.getName()));
        }

    }

    private void fillTargetSampleData(Map<String, ProductData> targetSampleData, int pixelIndex, PixelData inputData,
                                      GlintResult glintResult) {
        final ProductData agcFlagTile = targetSampleData.get(AGC_FLAG_BAND_NAME);
        agcFlagTile.setElemIntAt(pixelIndex, glintResult.getFlag());
        final ProductData angTile = targetSampleData.get(ANG_443_865);
        angTile.setElemDoubleAt(pixelIndex, glintResult.getAngstrom());
        final ProductData tau550Tile = targetSampleData.get(TAU_550);
        tau550Tile.setElemDoubleAt(pixelIndex, glintResult.getTau550());
        final ProductData tau778Tile = targetSampleData.get(TAU_778);
        tau778Tile.setElemDoubleAt(pixelIndex, glintResult.getTau778());
        final ProductData tau865Tile = targetSampleData.get(TAU_865);
        tau865Tile.setElemDoubleAt(pixelIndex, glintResult.getTau865());
        if (flintProduct == null) {
            // glint ratio available as output only for 'non-flint' case (RD, 28.10.09)
            final ProductData glintTile = targetSampleData.get(GLINT_RATIO);
            glintTile.setElemDoubleAt(pixelIndex, glintResult.getGlintRatio());
        } else {
            final ProductData flintTile = targetSampleData.get(FLINT_VALUE);
            flintTile.setElemDoubleAt(pixelIndex, inputData.flintValue);
        }
        final ProductData btsmTile = targetSampleData.get(BTSM);
        btsmTile.setElemDoubleAt(pixelIndex, glintResult.getBtsm());
        final ProductData atotTile = targetSampleData.get(ATOT);
        atotTile.setElemDoubleAt(pixelIndex, glintResult.getAtot());

        if (outputTosa) {
            fillTargetSample(TOSA_REFLEC_BAND_NAMES, pixelIndex, targetSampleData, glintResult.getTosaReflec());
            final ProductData quality = targetSampleData.get(TOSA_QUALITY_INDICATOR_BAND_NAME);
            quality.setElemDoubleAt(pixelIndex, glintResult.getTosaQualityIndicator());
        }
        if (outputAutoTosa) {
            fillTargetSample(AUTO_TOSA_REFLEC_BAND_NAMES, pixelIndex, targetSampleData,
                             glintResult.getAutoTosaReflec());
        }
        if (outputReflec) {
            fillTargetSample(REFLEC_BAND_NAMES, pixelIndex, targetSampleData, glintResult.getReflec());
        }
        if (outputNormReflec) {
            fillTargetSample(NORM_REFLEC_BAND_NAMES, pixelIndex, targetSampleData, glintResult.getNormReflec());
        }
        if (outputPath) {
            fillTargetSample(PATH_BAND_NAMES, pixelIndex, targetSampleData, glintResult.getPath());
        }
        if (outputTransmittance) {
            fillTargetSample(TRANS_BAND_NAMES, pixelIndex, targetSampleData, glintResult.getTrans());
        }

    }

    private void fillTargetSample(String[] bandNames, int pixelIndex,
                                  Map<String, ProductData> targetData, double[] values) {
        for (int i = 0; i < bandNames.length; i++) {
            final String bandName = bandNames[i];
            if (bandName != null) {
                int bandIndex = i > 10 ? i - 1 : i;
                final ProductData tile = targetData.get(bandName);
                tile.setElemDoubleAt(pixelIndex, values[bandIndex]);
            }
        }
    }

    private PixelData loadMerisPixelData(Map<String, ProductData> sourceTileMap, int index) {
        final PixelData pixelData = new PixelData();
        pixelData.isFullResolution = isFullResolution;
        pixelData.nadirColumnIndex = nadirColumnIndex;
        pixelData.validation = sourceTileMap.get(validationBand.getName()).getElemIntAt(index);
        pixelData.l1Flag = sourceTileMap.get(MERIS_L1B_FLAGS_DS_NAME).getElemIntAt(index);
        pixelData.detectorIndex = sourceTileMap.get(MERIS_DETECTOR_INDEX_DS_NAME).getElemIntAt(index);

        pixelData.solzen = getScaledValue(sourceTileMap, solzenNode, index);
        pixelData.solazi = getScaledValue(sourceTileMap, solaziNode, index);
        pixelData.satzen = getScaledValue(sourceTileMap, satzenNode, index);
        pixelData.satazi = getScaledValue(sourceTileMap, sataziNode, index);
        pixelData.altitude = getScaledValue(sourceTileMap, altitudeNode, index);
        pixelData.pressure = getScaledValue(sourceTileMap, pressureNode, index);
        pixelData.ozone = getScaledValue(sourceTileMap, ozoneNode, index);

        pixelData.toa_radiance = new double[spectralNodes.length];
        pixelData.solar_flux = new double[spectralNodes.length];
        for (int i = 0; i < spectralNodes.length; i++) {
            final Band spectralNode = spectralNodes[i];
            pixelData.toa_radiance[i] = getScaledValue(sourceTileMap, spectralNode, index);
            pixelData.solar_flux[i] = spectralNode.getSolarFlux();
        }
        return pixelData;
    }

    private static double getScaledValue(Map<String, ProductData> sourceTileMap, RasterDataNode rasterDataNode,
                                         int index) {
        double rawValue = sourceTileMap.get(rasterDataNode.getName()).getElemFloatAt(index);
        rawValue = rasterDataNode.scale(rawValue);
        return rawValue;
    }

    private Map<String, ProductData> preLoadMerisSources(Rectangle targetRectangle) {
        final Map<String, ProductData> map = new HashMap<>(27);

        final Tile validationTile = getSourceTile(validationBand, targetRectangle);
        map.put(validationBand.getName(), validationTile.getRawSamples());

        final Tile l1FlagTile = getSourceTile(l1FlagsNode, targetRectangle);
        map.put(l1FlagTile.getRasterDataNode().getName(), l1FlagTile.getRawSamples());

        final Tile solzenTile = getSourceTile(solzenNode, targetRectangle);
        map.put(solzenTile.getRasterDataNode().getName(), solzenTile.getRawSamples());

        final Tile solaziTile = getSourceTile(solaziNode, targetRectangle);
        map.put(solaziTile.getRasterDataNode().getName(), solaziTile.getRawSamples());

        final Tile satzenTile = getSourceTile(satzenNode, targetRectangle);
        map.put(satzenTile.getRasterDataNode().getName(), satzenTile.getRawSamples());

        final Tile sataziTile = getSourceTile(sataziNode, targetRectangle);
        map.put(sataziTile.getRasterDataNode().getName(), sataziTile.getRawSamples());

        final Tile detectorTile = getSourceTile(detectorNode, targetRectangle);
        map.put(detectorTile.getRasterDataNode().getName(), detectorTile.getRawSamples());

        final Tile altitudeTile = getSourceTile(altitudeNode, targetRectangle);
        map.put(altitudeTile.getRasterDataNode().getName(), altitudeTile.getRawSamples());

        final Tile pressureTile = getSourceTile(pressureNode, targetRectangle);
        map.put(pressureTile.getRasterDataNode().getName(), pressureTile.getRawSamples());

        final Tile ozoneTile = getSourceTile(ozoneNode, targetRectangle);
        map.put(ozoneTile.getRasterDataNode().getName(), ozoneTile.getRawSamples());

        for (RasterDataNode spectralNode : spectralNodes) {
            final Tile spectralTile = getSourceTile(spectralNode, targetRectangle);
            map.put(spectralTile.getRasterDataNode().getName(), spectralTile.getRawSamples());
        }
        return map;
    }

    private static FlagCoding createAgcFlagCoding() {
        final FlagCoding flagCoding = new FlagCoding(AGC_FLAG_BAND_NAME);
        flagCoding.setDescription("Atmosphere Correction - Flag Coding");

        addFlagAttribute(flagCoding, "LAND", "Land pixels", GlintCorrection.LAND);
        addFlagAttribute(flagCoding, "CLOUD_ICE", "Cloud or ice pixels", GlintCorrection.CLOUD_ICE);
        addFlagAttribute(flagCoding, "ATC_OOR", "Atmospheric correction out of range", GlintCorrection.ATC_OOR);
        addFlagAttribute(flagCoding, "TOA_OOR", "TOA out of range", GlintCorrection.TOA_OOR);
        addFlagAttribute(flagCoding, "TOSA_OOR", "TOSA out of range", GlintCorrection.TOSA_OOR);
        addFlagAttribute(flagCoding, "SOLZEN", "Large solar zenith angle", GlintCorrection.SOLZEN);
        addFlagAttribute(flagCoding, "ANCIL", "Missing/OOR auxiliary data", GlintCorrection.ANCIL);
        addFlagAttribute(flagCoding, "SUNGLINT", "Risk of sun glint", GlintCorrection.SUNGLINT);
        addFlagAttribute(flagCoding, "HAS_FLINT", "Flint value available (pixel covered by MERIS/AATSR)",
                         GlintCorrection.HAS_FLINT);
        addFlagAttribute(flagCoding, "INVALID", "Invalid pixels (LAND || CLOUD_ICE || l1_flags.INVALID)",
                         GlintCorrection.INVALID);

        return flagCoding;
    }

    private static void addFlagAttribute(FlagCoding flagCoding, String name, String description, int value) {
        MetadataAttribute attribute = new MetadataAttribute(name, ProductData.TYPE_UINT16);
        attribute.getData().setElemInt(value);
        attribute.setDescription(description);
        flagCoding.addAttribute(attribute);
    }

    private void addTargetBands(Product product) {
        final List<String> groupList = new ArrayList<String>();
        if (outputAutoTosa) {
            groupList.add("tosa_reflec_auto");
        }
        if (outputTosa) {
            addSpectralTargetBands(product, TOSA_REFLEC_BAND_NAMES, "TOSA Reflectance at {0} nm", "sr^-1");
            groupList.add("tosa_reflec");
            addNonSpectralTargetBand(product, TOSA_QUALITY_INDICATOR_BAND_NAME, "Input spectrum out of range check",
                                     "dl");

        }
        if (outputAutoTosa) {
            addSpectralTargetBands(product, AUTO_TOSA_REFLEC_BAND_NAMES, "TOSA Reflectance at {0} nm", "sr^-1");
        }
        if (outputReflec) {
            String reflecType;
            final String reflecUnit;
            if (ReflectanceEnum.RADIANCE_REFLECTANCES.equals(outputReflecAs)) {
                reflecType = "radiance";
                reflecUnit = "sr^-1";
            } else {
                reflecType = "irradiance";
                reflecUnit = "dl";
            }
            String descriptionPattern = "Water leaving " + reflecType + " reflectance at {0} nm";
            addSpectralTargetBands(product, REFLEC_BAND_NAMES, descriptionPattern, reflecUnit);
            groupList.add("reflec");

        }
        if (outputNormReflec) {
            String descriptionPattern = "Normalised water leaving radiance reflectance at {0} nm";
            addSpectralTargetBands(product, NORM_REFLEC_BAND_NAMES, descriptionPattern, "sr^-1");
            groupList.add("norm_refl");
        }
        if (outputPath) {
            addSpectralTargetBands(product, PATH_BAND_NAMES, "Water leaving radiance reflectance path at {0} nm",
                                   "dxd");
            groupList.add("path");
        }
        if (outputTransmittance) {
            addSpectralTargetBands(product, TRANS_BAND_NAMES,
                                   "Downwelling irradiance transmittance (Ed_Boa/Ed_Tosa) at {0} nm", "dl");
            groupList.add("trans");
        }
        final StringBuilder sb = new StringBuilder();
        final Iterator<String> iterator = groupList.iterator();
        while (iterator.hasNext()) {
            sb.append(iterator.next());
            if (iterator.hasNext()) {
                sb.append(":");
            }
        }
        product.setAutoGrouping(sb.toString());
        addNonSpectralTargetBand(product, TAU_550, "Spectral aerosol optical depth at 550", "dl");
        addNonSpectralTargetBand(product, TAU_778, "Spectral aerosol optical depth at 778", "dl");
        addNonSpectralTargetBand(product, TAU_865, "Spectral aerosol optical depth at 865", "dl");

        if (flintProduct == null) {
            addNonSpectralTargetBand(product, GLINT_RATIO, "Glint ratio", "dl");
        } else {
            addNonSpectralTargetBand(product, FLINT_VALUE, "Flint value", "1/sr");
        }

        addNonSpectralTargetBand(product, BTSM, "Total suspended matter scattering", "m^-1");
        addNonSpectralTargetBand(product, ATOT, "Absorption at 443 nm of all water constituents", "m^-1");
        addNonSpectralTargetBand(product, ANG_443_865, "\"Aerosol Angstrom coefficient\"", "dl");
    }

    private Band addNonSpectralTargetBand(Product product, String name, String description, String unit) {
        final Band band = product.addBand(name, ProductData.TYPE_FLOAT32);
        band.setDescription(description);
        band.setUnit(unit);
        band.setValidPixelExpression(VALID_EXPRESSION);
        return band;
    }

    private void addSpectralTargetBands(Product product, String[] bandNames, String descriptionPattern, String unit) {
        for (int i = 0; i < MERIS_L1B_SPECTRAL_BAND_NAMES.length; i++) {
            String bandName = bandNames[i];
            if (bandName != null) {
                final Band radBand = merisProduct.getBandAt(i);
                final String descr = MessageFormat.format(descriptionPattern, radBand.getSpectralWavelength());
                final Band band = addNonSpectralTargetBand(product, bandName, descr, unit);
                ProductUtils.copySpectralBandProperties(radBand, band);
            }
        }
    }

    private static void addAgcMasks(Product product) {
        final ProductNodeGroup<Mask> maskGroup = product.getMaskGroup();
        maskGroup.add(createMask(product, "agc_land", "Land pixels", "agc_flags.LAND", Color.GREEN, 0.5f));
        maskGroup.add(createMask(product, "cloud_ice", "Cloud or ice pixels", "agc_flags.CLOUD_ICE",
                                 Color.WHITE, 0.5f));
        maskGroup.add(createMask(product, "atc_oor", "Atmospheric correction out of range", "agc_flags.ATC_OOR",
                                 Color.ORANGE, 0.5f));
        maskGroup.add(createMask(product, "toa_oor", "TOA out of range", "agc_flags.TOA_OOR", Color.MAGENTA, 0.5f));
        maskGroup.add(createMask(product, "tosa_oor", "TOSA out of range", "agc_flags.TOSA_OOR", Color.CYAN, 0.5f));
        maskGroup.add(createMask(product, "solzen", "Large solar zenith angle", "agc_flags.SOLZEN", Color.PINK, 0.5f));
        maskGroup.add(createMask(product, "ancil", "Missing/OOR auxiliary data", "agc_flags.ANCIL", Color.BLUE, 0.5f));
        maskGroup.add(createMask(product, "sunglint", "Risk of sun glint", "agc_flags.SUNGLINT", Color.YELLOW, 0.5f));
        maskGroup.add(createMask(product, "has_flint", "Flint value computed (AATSR covered)", "agc_flags.HAS_FLINT",
                                 Color.RED, 0.5f));
        maskGroup.add(createMask(product, "agc_invalid", "Invalid pixels (LAND || CLOUD_ICE || l1_flags.INVALID)",
                                 "agc_flags.INVALID", Color.RED, 0.5f));
    }

    private static Mask createMask(Product product, String name, String description, String expression, Color color,
                                   float transparency) {
        return Mask.BandMathsType.create(name, description,
                                         product.getSceneRasterWidth(), product.getSceneRasterHeight(),
                                         expression, color, transparency);
    }

    private InputStream getNeuralNetStream(String resourceNetName, File neuralNetFile) {
        InputStream neuralNetStream;
        if (neuralNetFile == null || resourceNetName.contains(neuralNetFile.getName())) {
            neuralNetStream = getClass().getResourceAsStream(resourceNetName);
        } else {
            try {
                neuralNetStream = new FileInputStream(neuralNetFile);
            } catch (FileNotFoundException e) {
                throw new OperatorException(e);
            }
        }
        return neuralNetStream;
    }

    private String readNeuralNetFromStream(InputStream neuralNetStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(neuralNetStream));
        try {
            String line = reader.readLine();
            final StringBuilder sb = new StringBuilder();
            while (line != null) {
                // have to append line terminator, cause it's not included in line
                sb.append(line).append('\n');
                line = reader.readLine();
            }
            return sb.toString();
        } catch (IOException ioe) {
            throw new OperatorException("Could not initialize neural net", ioe);
        } finally {
            try {
                reader.close();
            } catch (IOException ignore) {
            }
        }
    }

    private static void validateMerisProduct(final Product merisProduct) {
        final String missedBand = validateMerisProductBands(merisProduct);
        if (!missedBand.isEmpty()) {
            String message = MessageFormat.format("Missing required band in product {0}: {1}",
                                                  merisProduct.getName(), missedBand);
            throw new OperatorException(message);
        }
        final String missedTPG = validateMerisProductTpgs(merisProduct);
        if (!missedTPG.isEmpty()) {
            String message = MessageFormat.format("Missing required raster in product {0}: {1}",
                                                  merisProduct.getName(), missedTPG);
            throw new OperatorException(message);
        }
    }

    private static void validateAatsrProduct(final Product aatsrProduct) {
        if (aatsrProduct != null) {
            final String missedBand = validateAatsrProductBands(aatsrProduct);
            if (!missedBand.isEmpty()) {
                String message = MessageFormat.format("Missing required band in product {0}: {1}",
                                                      aatsrProduct.getName(), missedBand);
                throw new OperatorException(message);
            }
            final String missedTPG = validateAatsrProductTpgs(aatsrProduct);
            if (!missedTPG.isEmpty()) {
                String message = MessageFormat.format("Missing required raster in product {0}: {1}",
                                                      aatsrProduct.getName(), missedTPG);
                throw new OperatorException(message);
            }
        }
    }

    private static void validateFlintProduct(final Product flintProduct) {
        if (flintProduct != null) {
            if (!flintProduct.containsBand(RADIANCE_MERIS_BAND_NAME)) {
                String message = MessageFormat.format("Missing required band in product {0}: {1}",
                                                      flintProduct.getName(), RADIANCE_MERIS_BAND_NAME);
                throw new OperatorException(message);
            }
        }
    }

    private static String validateMerisProductBands(Product product) {
        List<String> sourceBandNameList = Arrays.asList(product.getBandNames());
        for (String bandName : MERIS_L1B_SPECTRAL_BAND_NAMES) {
            if (!sourceBandNameList.contains(bandName)) {
                return bandName;
            }
        }
        if (!sourceBandNameList.contains(MERIS_L1B_FLAGS_DS_NAME)) {
            return MERIS_L1B_FLAGS_DS_NAME;
        }

        return "";
    }

    private static String validateAatsrProductBands(Product product) {
        List<String> sourceBandNameList = Arrays.asList(product.getBandNames());
        for (String bandName : AATSR_L1B_BAND_NAMES) {
            if (!sourceBandNameList.contains(bandName)) {
                return bandName;
            }
        }

        return "";
    }


    private static String validateMerisProductTpgs(Product product) {
        List<String> sourceNodeNameList = new ArrayList<>();
        sourceNodeNameList.addAll(Arrays.asList(product.getTiePointGridNames()));
        sourceNodeNameList.addAll(Arrays.asList(product.getBandNames()));
        for (String tpgName : REQUIRED_MERIS_TPG_NAMES) {
            if (!sourceNodeNameList.contains(tpgName)) {
                return tpgName;
            }
        }

        return "";
    }

    private static String validateAatsrProductTpgs(Product product) {
        List<String> sourceNodeNameList = new ArrayList<>();
        sourceNodeNameList.addAll(Arrays.asList(product.getTiePointGridNames()));
        sourceNodeNameList.addAll(Arrays.asList(product.getBandNames()));
        for (String tpgName : REQUIRED_AATSR_TPG_NAMES) {
            if (!sourceNodeNameList.contains(tpgName)) {
                return tpgName;
            }
        }

        return "";
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlintCorrectionOperator.class);
        }
    }
}
