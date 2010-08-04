package org.esa.beam.atmosphere.operator;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.PixelData;
import org.esa.beam.collocation.CollocateOp;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.BitmaskDef;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
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
import org.esa.beam.nn.NNffbpAlphaTabFast;
import org.esa.beam.util.Debug;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main operator for the AGC Glint correction.
 *
 * @author Marco Peters, Olaf Danne
 * @version $Revision: 2703 $ $Date: 2010-01-21 13:51:07 +0100 (Do, 21 Jan 2010) $
 */
@SuppressWarnings({"InstanceVariableMayNotBeInitialized", "MismatchedReadAndWriteOfArray"})
@OperatorMetadata(alias = "Meris.GlintCorrection",
                  version = "0.1",
                  authors = "Marco Peters, Roland Doerffer",
                  copyright = "(c) 2008 by Brockmann Consult",
                  description = "MERIS atmospheric correction using a neural net.")
public class GlintCorrectionOperator extends Operator {

    public static final String GLINT_CORRECTION_VERSION = "1.1.1";

    private static final String[] REQUIRED_MERIS_TPG_NAMES = {
            EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME,
            EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME,
            EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME,
            EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME,
            EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME,
            "atm_press",
            "ozone",
    };

    private static final String[] REQUIRED_AATSR_TPG_NAMES =
            EnvisatConstants.AATSR_TIE_POINT_GRID_NAMES;

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
    };
    private static final String[] REFLEC_BAND_NAMES = {
            "reflec_1", "reflec_2", "reflec_3", "reflec_4", "reflec_5",
            "reflec_6", "reflec_7", "reflec_8", "reflec_9", "reflec_10",
            null,
            "reflec_12", "reflec_13",
    };
    private static final String[] PATH_BAND_NAMES = {
            "path_1", "path_2", "path_3", "path_4", "path_5",
            "path_6", "path_7", "path_8", "path_9", "path_10",
            null,
            "path_12", "path_13",
    };
    private static final String[] TRANS_BAND_NAMES = {
            "trans_1", "trans_2", "trans_3", "trans_4", "trans_5",
            "trans_6", "trans_7", "trans_8", "trans_9", "trans_10",
            null,
            "trans_12", "trans_13",
    };
    private static final String AGC_FLAG_BAND_NAME = "agc_flags";

    private static final BitmaskDef[] BITMASK_DEFINITIONS = new BitmaskDef[]{
            new BitmaskDef("agc_land", "Land pixels", "agc_flags.LAND", Color.GREEN, 0.5f),
            new BitmaskDef("cloud_ice", "Cloud or ice pixels", "agc_flags.CLOUD_ICE", Color.WHITE, 0.5f),
            new BitmaskDef("atc_oor", "Atmospheric correction out of range", "agc_flags.ATC_OOR", Color.ORANGE, 0.5f),
            new BitmaskDef("toa_oor", "TOA out of range", "agc_flags.TOA_OOR", Color.MAGENTA, 0.5f),
            new BitmaskDef("tosa_oor", "TOSA out of range", "agc_flags.TOSA_OOR", Color.CYAN, 0.5f),
            new BitmaskDef("solzen", "Large solar zenith angle", "agc_flags.SOLZEN", Color.PINK, 0.5f),
            new BitmaskDef("ancil", "Missing/OOR auxiliary data", "agc_flags.ANCIL", Color.BLUE, 0.5f),
            new BitmaskDef("sunglint", "Risk of sun glint", "agc_flags.SUNGLINT", Color.YELLOW, 0.5f),
            new BitmaskDef("has_flint", "Flint value computed (AATSR covered)", "agc_flags.HAS_FLINT", Color.RED, 0.5f),

    };


//    private static final String RADIANCE_MERIS_BAND_NAME = "radiance_meris";
    private static final String RADIANCE_MERIS_BAND_NAME = "result_radiance_rr89";

    @SourceProduct(label = "MERIS L1b input product", description = "The MERIS L1b input product.")
    private Product merisProduct;

    @SourceProduct(label = "AATSR L1b input product", description = "The AATSR L1b input product.",
                   optional = true)
    private Product aatsrProduct;

//    @SourceProduct(label = "FLINT input product", description = "The output product of the FLINT operator.",
//                   optional = true)
    private Product flintProduct;

    @TargetProduct(description = "The atmospheric corrected output product.")
    private Product targetProduct;

    @Parameter(defaultValue = "true", label = "Output TOSA reflectance",
               description = "Toggles the output of TOSA reflectance.")
    private boolean outputTosa;

    @Parameter(defaultValue = "true", label = "Output water leaving reflectance",
               description = "Toggles the output of water leaving irrediance reflectance.")
    private boolean outputReflec;

    @Parameter(defaultValue = "true", label = "Output path reflectance",
               description = "Toggles the output of water leaving path reflectance.")
    private boolean outputPath;

    @Parameter(defaultValue = "true", label = "Output transmittance",
               description = "Toggles the output of downwelling irrediance transmittance.")
    private boolean outputTransmittance;

    @Parameter(defaultValue = "false",
               label = "Derive water leaving reflectance from path reflectance",
               description = "Switch between computation of water leaving reflectance from path reflectance and direct use of neural net output.")
    private boolean deriveRwFromPath;

    @Parameter(defaultValue = "toa_reflec_10 > toa_reflec_6 AND toa_reflec_13 > 0.1",
               label = "Land detection expression",
               description = "The arithmetic expression used for land detection.",
               notEmpty = true, notNull = true)
    private String landExpression;

    @Parameter(defaultValue = "toa_reflec_14 > 0.3",
               label = "Cloud/Ice detection expression",
               description = "The arithmetic expression used for cloud/ice detection.",
               notEmpty = true, notNull = true)
    private String cloudIceExpression;

//    @Parameter(defaultValue = "1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0",
//               label = "Radiance adjustment factors", description = "Factor to adjust radiances")
//    private double[] radianceAdjustmentFactors;
    // do not offer as option any more (MB, CB):
    private double[] radianceAdjustmentFactors =
        new double[]{1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};

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

    private GlintCorrection glintCorrectionMeris;
    private GlintCorrection glintCorrectionFlint;
    private static final String VALID_EXPRESSION = String.format("!%s.INVALID",
                                                                 EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
//    private static final String MERIS_ATMOSPHERIC_NET_NAME = "25x30x40_5365.2.net";
    
    private static final String MERIS_ATMOSPHERIC_NET_NAME = "25x30x40_9164.3.net";
    //    private static final String FLINT_ATMOSPHERIC_NET_NAME = "25x30x40_3829.6_flint.net";
    private static final String FLINT_ATMOSPHERIC_NET_NAME = "25x30x40_6936.3.net";
    private Band validationBand;

    public static final double NO_FLINT_VALUE = -1.0;

    @Override
    public void initialize() throws OperatorException {
        validateMerisProduct(merisProduct);
        if (useFlint && aatsrProduct == null) {
            throw new OperatorException("Missing required AATSR L1b product for FLINT computation.");
        }
        validateAatsrProduct(aatsrProduct);

        if (useFlint && aatsrProduct != null) {
            // create collocation product...
            Map<String, Product> collocateInput = new HashMap<String, Product>(2);
            collocateInput.put("masterProduct", merisProduct);
            collocateInput.put("slaveProduct", aatsrProduct);
            Product collocateProduct =
                GPF.createProduct(OperatorSpi.getOperatorAlias(CollocateOp.class), GPF.NO_PARAMS, collocateInput);

            // create FLINT product
            Map<String, Product> flintInput = new HashMap<String, Product>(1);
            flintInput.put("l1bCollocate", collocateProduct);
            Map<String, Object> flintParameters = new HashMap<String, Object>();
            flintProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(FlintOp.class), flintParameters, flintInput);
            validateFlintProduct(flintProduct);
        }

        Product outputProduct = new Product(merisProduct.getName() + "_AC", "MERIS_L2_AC",
                                            merisProduct.getSceneRasterWidth(), merisProduct.getSceneRasterHeight());
        ProductUtils.copyMetadata(merisProduct, outputProduct);
        ProductUtils.copyTiePointGrids(merisProduct, outputProduct);
        ProductUtils.copyGeoCoding(merisProduct, outputProduct);

        addTargetBands(outputProduct);
        ProductUtils.copyFlagBands(merisProduct, outputProduct);
        Band agcFlagsBand = outputProduct.addBand(AGC_FLAG_BAND_NAME, ProductData.TYPE_INT16);
        final FlagCoding flagCoding = createAgcFlagCoding();
        agcFlagsBand.setSampleCoding(flagCoding);
        outputProduct.getFlagCodingGroup().add(flagCoding);
        addBitmasks(outputProduct);

        final ToaReflectanceValidationOp validationOp = ToaReflectanceValidationOp.create(merisProduct,
                                                                                          landExpression,
                                                                                          cloudIceExpression);
        validationBand = validationOp.getTargetProduct().getBandAt(0);

        InputStream isMeris = null;
        InputStream isFlint = null;
        try {
            if (atmoNetMerisFile.getName().equals(MERIS_ATMOSPHERIC_NET_NAME)) {
                isMeris = getClass().getResourceAsStream(MERIS_ATMOSPHERIC_NET_NAME);
            } else {
                try {
                    isMeris = new FileInputStream(atmoNetMerisFile);
                } catch (FileNotFoundException e) {
                    throw new OperatorException(e);
                }
            }
            glintCorrectionMeris = createAtmosphericCorrection(isMeris);

            if (atmoNetFlintFile.getName().equals(FLINT_ATMOSPHERIC_NET_NAME)) {
                isFlint = getClass().getResourceAsStream(FLINT_ATMOSPHERIC_NET_NAME);
            } else {
                try {
                    isFlint = new FileInputStream(atmoNetFlintFile);
                } catch (FileNotFoundException e) {
                    throw new OperatorException(e);
                }
            }
            glintCorrectionFlint = createAtmosphericCorrection(isFlint);
        } finally {
            try {
                if (isMeris != null) {
                    isMeris.close();
                }
                if (isFlint!= null) {
                    isFlint.close();
                }
            } catch (IOException e) {
                Debug.trace(e);
            }
        }

        setTargetProduct(outputProduct);
    }


    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws
                                                                                                             OperatorException {
        try {
            pm.beginTask("Correcting atmosphere...", targetRectangle.height);
            final Map<String, ProductData> merisSampleDataMap = preLoadMerisSources(targetRectangle);
            final Map<String, ProductData> targetSampleDataMap = getTargetSampleData(targetTiles);

            for (int y = 0; y < targetRectangle.getHeight(); y++) {
                checkForCancellation(pm);
                final int lineIndex = y * targetRectangle.width;
                for (int x = 0; x < targetRectangle.getWidth(); x++) {
                    final int index = lineIndex + x;
                    final PixelData inputData = loadMerisPixelData(merisSampleDataMap, index);
                    final int pixelX = targetRectangle.x + x;
                    final int pixelY = targetRectangle.y + y;
                    inputData.flintValue = getFlintValue(pixelX, pixelY);
                    GlintResult glintResult;
                    if (!useFlint || !GlintCorrection.isFlintValueValid(inputData.flintValue)) {
                        glintResult = glintCorrectionMeris.perform(inputData, deriveRwFromPath);
                    } else {
                        glintResult = glintCorrectionFlint.perform(inputData, deriveRwFromPath);
                        glintResult.raiseFlag(GlintCorrection.HAS_FLINT);
                    }
                    fillTargetSampleData(targetSampleDataMap, index, inputData, glintResult);
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
        final Map<String, ProductData> map = new HashMap<String, ProductData>(targetTiles.size());
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

    private void fillTargetSampleData(Map<String, ProductData> targetSampleData, int index, PixelData inputData,
                                      GlintResult glintResult) {
        final ProductData agcFlagTile = targetSampleData.get(AGC_FLAG_BAND_NAME);
        agcFlagTile.setElemIntAt(index, glintResult.getFlag());
        final ProductData l1FlagTile = targetSampleData.get(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
        l1FlagTile.setElemIntAt(index, inputData.l1Flag);
        final ProductData angTile = targetSampleData.get(ANG_443_865);
        angTile.setElemDoubleAt(index, glintResult.getAngstrom());
        final ProductData tau550Tile = targetSampleData.get(TAU_550);
        tau550Tile.setElemDoubleAt(index, glintResult.getTau550());
        final ProductData tau778Tile = targetSampleData.get(TAU_778);
        tau778Tile.setElemDoubleAt(index, glintResult.getTau778());
        final ProductData tau865Tile = targetSampleData.get(TAU_865);
        tau865Tile.setElemDoubleAt(index, glintResult.getTau865());
        if (flintProduct == null) {
            // glint ratio available as output only for 'non-flint' case (RD, 28.10.09)
            final ProductData glintTile = targetSampleData.get(GLINT_RATIO);
            glintTile.setElemDoubleAt(index, glintResult.getGlintRatio());
        } else {
            final ProductData flintTile = targetSampleData.get(FLINT_VALUE);
            flintTile.setElemDoubleAt(index, inputData.flintValue);
        }
        final ProductData btsmTile = targetSampleData.get(BTSM);
        btsmTile.setElemDoubleAt(index, glintResult.getBtsm());
        final ProductData atotTile = targetSampleData.get(ATOT);
        atotTile.setElemDoubleAt(index, glintResult.getAtot());
        if (outputTosa) {
            for (int i = 0; i < TOSA_REFLEC_BAND_NAMES.length; i++) {
                final String bandName = TOSA_REFLEC_BAND_NAMES[i];
                if (bandName != null) {
                    int bandIndex = i > 10 ? i - 1 : i;
                    final ProductData tile = targetSampleData.get(bandName);
                    tile.setElemDoubleAt(index, glintResult.getTosaReflec()[bandIndex]);
                }
            }
        }
        if (outputReflec) {
            for (int i = 0; i < REFLEC_BAND_NAMES.length; i++) {
                final String bandName = REFLEC_BAND_NAMES[i];
                if (bandName != null) {
                    int bandIndex = i > 10 ? i - 1 : i;
                    final ProductData tile = targetSampleData.get(bandName);
                    tile.setElemDoubleAt(index, glintResult.getReflec()[bandIndex]);
                }
            }
        }
        if (outputPath) {
            for (int i = 0; i < PATH_BAND_NAMES.length; i++) {
                final String bandName = PATH_BAND_NAMES[i];
                if (bandName != null) {
                    int bandIndex = i > 10 ? i - 1 : i;
                    final ProductData tile = targetSampleData.get(bandName);
                    tile.setElemDoubleAt(index, glintResult.getPath()[bandIndex]);
                }
            }
        }
        if (outputTransmittance) {
            for (int i = 0; i < TRANS_BAND_NAMES.length; i++) {
                final String bandName = TRANS_BAND_NAMES[i];
                if (bandName != null) {
                    int bandIndex = i > 10 ? i - 1 : i;
                    final ProductData tile = targetSampleData.get(bandName);
                    tile.setElemDoubleAt(index, glintResult.getTrans()[bandIndex]);
                }
            }
        }

    }

    private PixelData loadMerisPixelData(Map<String, ProductData> sourceTileMap, int index) {
        final PixelData pixelData = new PixelData();
        pixelData.validation = sourceTileMap.get(validationBand.getName()).getElemIntAt(index);
        pixelData.l1Flag = sourceTileMap.get(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME).getElemIntAt(index);

        pixelData.solzen = getScaledValue(sourceTileMap,
                                          merisProduct.getRasterDataNode(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME),
                                          index);
        pixelData.solazi = getScaledValue(sourceTileMap,
                                          merisProduct.getRasterDataNode(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME),
                                          index);
        pixelData.satzen = getScaledValue(sourceTileMap,
                                          merisProduct.getRasterDataNode(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME),
                                          index);
        pixelData.satazi = getScaledValue(sourceTileMap,
                                          merisProduct.getRasterDataNode(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME),
                                          index);
        pixelData.altitude = getScaledValue(sourceTileMap,
                                            merisProduct.getRasterDataNode(
                                                    EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME),
                                            index);
        pixelData.pressure = getScaledValue(sourceTileMap,
                                            merisProduct.getRasterDataNode("atm_press"),
                                            index);
        pixelData.ozone = getScaledValue(sourceTileMap,
                                         merisProduct.getRasterDataNode("ozone"),
                                         index);

        pixelData.toa_radiance = new double[EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES.length];
        pixelData.solar_flux = new double[EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES.length];
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES.length; i++) {
            String spectralBandName = EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i];
            pixelData.toa_radiance[i] = getScaledValue(sourceTileMap,
                                                       merisProduct.getRasterDataNode(spectralBandName),
                                                       index);
            pixelData.toa_radiance[i] *= radianceAdjustmentFactors[i];
            pixelData.solar_flux[i] = merisProduct.getBand(spectralBandName).getSolarFlux();
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
        final Map<String, ProductData> map = new HashMap<String, ProductData>(27);

        final Tile validationTile = getSourceTile(validationBand, targetRectangle, ProgressMonitor.NULL);
        map.put(validationTile.getRasterDataNode().getName(), validationTile.getRawSamples());

        final Tile l1FlagTile = getSourceTile(merisProduct.getRasterDataNode(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME),
                                              targetRectangle, ProgressMonitor.NULL);
        map.put(l1FlagTile.getRasterDataNode().getName(), l1FlagTile.getRawSamples());

        final Tile solzenTile = getSourceTile(
                merisProduct.getRasterDataNode(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRectangle,
                ProgressMonitor.NULL);
        map.put(solzenTile.getRasterDataNode().getName(), solzenTile.getRawSamples());

        final Tile solaziTile = getSourceTile(
                merisProduct.getRasterDataNode(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), targetRectangle,
                ProgressMonitor.NULL);
        map.put(solaziTile.getRasterDataNode().getName(), solaziTile.getRawSamples());

        final Tile satzenTile = getSourceTile(
                merisProduct.getRasterDataNode(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRectangle,
                ProgressMonitor.NULL);
        map.put(satzenTile.getRasterDataNode().getName(), satzenTile.getRawSamples());

        final Tile sataziTile = getSourceTile(
                merisProduct.getRasterDataNode(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), targetRectangle,
                ProgressMonitor.NULL);
        map.put(sataziTile.getRasterDataNode().getName(), sataziTile.getRawSamples());

        final Tile altitudeTile = getSourceTile(
                merisProduct.getRasterDataNode(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME), targetRectangle,
                ProgressMonitor.NULL);
        map.put(altitudeTile.getRasterDataNode().getName(), altitudeTile.getRawSamples());

        final Tile pressureTile = getSourceTile(merisProduct.getRasterDataNode("atm_press"), targetRectangle,
                                                ProgressMonitor.NULL);
        map.put(pressureTile.getRasterDataNode().getName(), pressureTile.getRawSamples());

        final Tile ozoneTile = getSourceTile(merisProduct.getRasterDataNode("ozone"), targetRectangle,
                                             ProgressMonitor.NULL);
        map.put(ozoneTile.getRasterDataNode().getName(), ozoneTile.getRawSamples());

        for (int i = 0; i < EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES.length; i++) {
            String spectralBandName = EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i];
            final RasterDataNode dataNode = merisProduct.getRasterDataNode(spectralBandName);
            final Tile spectralTile = getSourceTile(dataNode, targetRectangle, ProgressMonitor.NULL);
            map.put(spectralTile.getRasterDataNode().getName(), spectralTile.getRawSamples());
        }
        return map;
    }

    private static FlagCoding createAgcFlagCoding() {
        final FlagCoding flagCoding = new FlagCoding(AGC_FLAG_BAND_NAME);
        flagCoding.setDescription("Atmosphere Correction - Flag Coding");

        MetadataAttribute attribute = new MetadataAttribute("LAND", ProductData.TYPE_UINT16);
        attribute.getData().setElemInt(GlintCorrection.LAND);
        attribute.setDescription("Land pixels");
        flagCoding.addAttribute(attribute);

        attribute = new MetadataAttribute("CLOUD_ICE", ProductData.TYPE_UINT16);
        attribute.getData().setElemInt(GlintCorrection.CLOUD_ICE);
        attribute.setDescription("Cloud or ice pixels");
        flagCoding.addAttribute(attribute);

        attribute = new MetadataAttribute("ATC_OOR", ProductData.TYPE_UINT16);
        attribute.getData().setElemInt(GlintCorrection.ATC_OOR);
        attribute.setDescription("Atmospheric correction out of range");
        flagCoding.addAttribute(attribute);

        attribute = new MetadataAttribute("TOA_OOR", ProductData.TYPE_UINT16);
        attribute.getData().setElemInt(GlintCorrection.TOA_OOR);
        attribute.setDescription("TOA out of range");
        flagCoding.addAttribute(attribute);

        attribute = new MetadataAttribute("TOSA_OOR", ProductData.TYPE_UINT16);
        attribute.getData().setElemInt(GlintCorrection.TOSA_OOR);
        attribute.setDescription("TOSA out of range");
        flagCoding.addAttribute(attribute);

        attribute = new MetadataAttribute("SOLZEN", ProductData.TYPE_UINT16);
        attribute.getData().setElemInt(GlintCorrection.SOLZEN);
        attribute.setDescription("Large solar zenith angle");
        flagCoding.addAttribute(attribute);

        attribute = new MetadataAttribute("ANCIL", ProductData.TYPE_UINT16);
        attribute.getData().setElemInt(GlintCorrection.ANCIL);
        attribute.setDescription("Missing/OOR auxiliary data");
        flagCoding.addAttribute(attribute);

        attribute = new MetadataAttribute("SUNGLINT", ProductData.TYPE_UINT16);
        attribute.getData().setElemInt(GlintCorrection.SUNGLINT);
        attribute.setDescription("Risk of sun glint");
        flagCoding.addAttribute(attribute);

        attribute = new MetadataAttribute("HAS_FLINT", ProductData.TYPE_UINT16);
        attribute.getData().setElemInt(GlintCorrection.HAS_FLINT);
        attribute.setDescription("Flint value available (pixel covered by MERIS/AATSR)");
        flagCoding.addAttribute(attribute);

        return flagCoding;

    }

    private void addTargetBands(Product product) {
        if (outputTosa) {
            for (int i = 0; i < TOSA_REFLEC_BAND_NAMES.length; i++) {
                String bandName = TOSA_REFLEC_BAND_NAMES[i];
                if (bandName != null) {
                    final Band radBand = merisProduct.getBandAt(i);
                    final Band band = product.addBand(bandName, ProductData.TYPE_FLOAT32);
                    final float wavelength = radBand.getSpectralWavelength();
                    band.setDescription(MessageFormat.format("TOSA Reflectance at {0} nm", wavelength));
                    band.setSpectralWavelength(wavelength);
                    band.setSpectralBandwidth(radBand.getSpectralBandwidth());
                    band.setSpectralBandIndex(radBand.getSpectralBandIndex());
                    band.setUnit("sr^-1");
                    band.setValidPixelExpression(VALID_EXPRESSION);
                }
            }
        }
        if (outputReflec) {
            for (int i = 0; i < REFLEC_BAND_NAMES.length; i++) {
                String bandName = REFLEC_BAND_NAMES[i];
                if (bandName != null) {
                    final Band radBand = merisProduct.getBandAt(i);
                    final Band band = product.addBand(bandName, ProductData.TYPE_FLOAT32);
                    final float wavelength = radBand.getSpectralWavelength();
                    band.setDescription(
                            MessageFormat.format("Water leaving radiance reflectance at {0} nm", wavelength));
                    band.setSpectralWavelength(wavelength);
                    band.setSpectralBandwidth(radBand.getSpectralBandwidth());
                    band.setSpectralBandIndex(radBand.getSpectralBandIndex());
                    band.setUnit("sr^-1");
                    band.setValidPixelExpression(VALID_EXPRESSION);
                }
            }
        }
        if (outputPath) {
            for (int i = 0; i < PATH_BAND_NAMES.length; i++) {
                String bandName = PATH_BAND_NAMES[i];
                if (bandName != null) {
                    final Band radBand = merisProduct.getBandAt(i);
                    final Band band = product.addBand(bandName, ProductData.TYPE_FLOAT32);
                    final float wavelength = radBand.getSpectralWavelength();
                    band.setDescription(
                            MessageFormat.format("Water leaving radiance reflectance path at {0} nm", wavelength));
                    band.setSpectralWavelength(wavelength);
                    band.setSpectralBandwidth(radBand.getSpectralBandwidth());
                    band.setSpectralBandIndex(radBand.getSpectralBandIndex());
                    band.setUnit("dxd");
                    band.setValidPixelExpression(VALID_EXPRESSION);
                }
            }
        }
        if (outputTransmittance) {
            for (int i = 0; i < TRANS_BAND_NAMES.length; i++) {
                String bandName = TRANS_BAND_NAMES[i];
                if (bandName != null) {
                    final Band radBand = merisProduct.getBandAt(i);
                    final Band band = product.addBand(bandName, ProductData.TYPE_FLOAT32);
                    final float wavelength = radBand.getSpectralWavelength();
                    band.setDescription(MessageFormat.format(
                            "Downwelling irrediance transmittance (Ed_Boa/Ed_Tosa) at {0} nm", wavelength));
                    band.setSpectralWavelength(wavelength);
                    band.setSpectralBandwidth(radBand.getSpectralBandwidth());
                    band.setSpectralBandIndex(radBand.getSpectralBandIndex());
                    band.setUnit("dl");
                    band.setValidPixelExpression(VALID_EXPRESSION);
                }
            }
        }
        final Band tau550Band = product.addBand(TAU_550, ProductData.TYPE_FLOAT32);
        tau550Band.setDescription("Spectral aerosol optical depth at 550");
        tau550Band.setUnit("dl");
        tau550Band.setValidPixelExpression(VALID_EXPRESSION);
        final Band tau778Band = product.addBand(TAU_778, ProductData.TYPE_FLOAT32);
        tau778Band.setDescription("Spectral aerosol optical depth at 778");
        tau778Band.setUnit("dl");
        tau778Band.setValidPixelExpression(VALID_EXPRESSION);
        final Band tau865Band = product.addBand(TAU_865, ProductData.TYPE_FLOAT32);
        tau865Band.setDescription("Spectral aerosol optical depth at 865");
        tau865Band.setUnit("dl");
        tau865Band.setValidPixelExpression(VALID_EXPRESSION);

        if (flintProduct == null) {
            final Band glintRatioBand = product.addBand(GLINT_RATIO, ProductData.TYPE_FLOAT32);
            glintRatioBand.setDescription("Glint ratio");
            glintRatioBand.setUnit("dl");
            glintRatioBand.setValidPixelExpression(VALID_EXPRESSION);
        } else {
            final Band flintBand = product.addBand(FLINT_VALUE, ProductData.TYPE_FLOAT32);
            flintBand.setDescription("Flint value");
            flintBand.setValidPixelExpression(VALID_EXPRESSION);
        }

        final Band btsmBand = product.addBand(BTSM, ProductData.TYPE_FLOAT32);
        btsmBand.setDescription("Total supended matter scattering");
        btsmBand.setUnit("m^-1");
        btsmBand.setValidPixelExpression(VALID_EXPRESSION);
        final Band atotBand = product.addBand(ATOT, ProductData.TYPE_FLOAT32);
        atotBand.setDescription("Absorption at 443 nm of all water constituents");
        atotBand.setUnit("m^-1");
        atotBand.setValidPixelExpression(VALID_EXPRESSION);
        final Band angBand = product.addBand(ANG_443_865, ProductData.TYPE_FLOAT32);
        angBand.setDescription("\"Aerosol Angstrom coefficient\"");
        angBand.setUnit("dl");
        angBand.setValidPixelExpression(VALID_EXPRESSION);
    }

    private static void addBitmasks(Product product) {
        for (BitmaskDef bitmaskDef : BITMASK_DEFINITIONS) {
            // need a copy, cause the BitmaskDefs are otherwise disposed
            // if the outputProduct gets disposed after processing
            product.addBitmaskDef(bitmaskDef.createCopy());
        }
    }

    private static GlintCorrection createAtmosphericCorrection(InputStream neuralNetStream) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(neuralNetStream));
            String line = reader.readLine();
            final StringBuilder sb = new StringBuilder();
            while (line != null) {
                // have to append line terminator, cause it's not included in line
                sb.append(line).append('\n');
                line = reader.readLine();
            }
            return new GlintCorrection(new NNffbpAlphaTabFast(sb.toString()));
        } catch (IOException ioe) {
            throw new OperatorException("Could not initialize neural net", ioe);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
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
            String message = MessageFormat.format("Missing required tie-point grid in product {0}: {1}",
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
                String message = MessageFormat.format("Missing required tie-point grid in product {0}: {1}",
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

    private static String validateAatsrProductBands(Product product) {
        List<String> sourceBandNameList = Arrays.asList(product.getBandNames());
        for (String bandName : EnvisatConstants.AATSR_L1B_BAND_NAMES) {
            if (!sourceBandNameList.contains(bandName)) {
                return bandName;
            }
        }

        return "";
    }


    private static String validateMerisProductTpgs(Product product) {
        List<String> sourceTpgNameList = Arrays.asList(product.getTiePointGridNames());
        for (String tpgName : REQUIRED_MERIS_TPG_NAMES) {
            if (!sourceTpgNameList.contains(tpgName)) {
                return tpgName;
            }
        }

        return "";
    }

    private static String validateAatsrProductTpgs(Product product) {
        List<String> sourceTpgNameList = Arrays.asList(product.getTiePointGridNames());
        for (String tpgName : REQUIRED_AATSR_TPG_NAMES) {
            if (!sourceTpgNameList.contains(tpgName)) {
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
