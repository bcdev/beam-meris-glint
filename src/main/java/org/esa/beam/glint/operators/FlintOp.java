package org.esa.beam.glint.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.BandMathsHelper;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;

/**
 * Operator for FUB Glint processing ('FLINT').
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "glint.Flint",
                  version = "1.2.1",
                  authors = "Jürgen Fischer and Rene Preusker (FUB), Olaf Danne (BC)",
                  copyright = "(c) 2014 by Brockmann Consult",
                  description = "Flint Processor.",
                  internal = true)
public class FlintOp extends Operator {
    public static final String CONFID_NADIR_FLAGS = "confid_flags_nadir_S";
    public static final String CONFID_FWARD_FLAGS = "confid_flags_fward_S";
    public static final String CLOUD_NADIR_FLAGS = "cloud_flags_nadir_S";
    public static final String CLOUD_FWARD_FLAGS = "cloud_flags_fward_S";

    // intermediate results for debugging, step 1
    public static final String STEP_1b1_RESULT_NAME = "1b1__water_vapour_column";
    public static final String STEP_1b2_RESULT1_NAME = "1b2__transmission_37";
    public static final String STEP_1b2_RESULT2_NAME = "1b2__transmission_16";
    public static final String STEP_1c_RESULT1_NAME = "1c__radiance_37";
    public static final String STEP_1c_RESULT2_NAME = "1c__radiance_thermal_part_37";
    public static final String STEP_1d_RESULT1_NAME = "1d__solar_part_37";
    public static final String STEP_1d_RESULT2_NAME = "1d__solar_part_37_aatsr_units";
    public static final String STEP_1e_RESULT_NAME = "1e__add_cloud_mask";

    // intermediate results for debugging, step 2
    public static final String RESULT_NUMBERWINDSPEEDS_NAME = "result_number_windspeeds";
    public static final String RESULT_WINDSPEED1_NAME = "result_windspeed1";
    public static final String RESULT_WINDSPEED2_NAME = "result_windspeed2";
    public static final String RESULT_RADIANCE1_NAME = "result_radiance1";
    public static final String RESULT_RADIANCE2_NAME = "result_radiance2";

    // final results
    public static final String RESULT_WINDSPEED_FINAL_NAME = "result_windspeed_wsss";
    public static final String RESULT_RADIANCE_FINAL_NAME = "result_radiance_rr89";

    @SourceProduct(alias = "l1bCollocate", description = "MERIS/AATSR collocation product.")
    private Product collocateProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    @Parameter(defaultValue = "false", label = "Water Vapour")
    private boolean writeWaterVapour;

    @Parameter(defaultValue = "false", label = "Transmission at 3.7um")
    private boolean writeTransmission37;

    @Parameter(defaultValue = "false", label = "Transmission at 1.6um")
    private boolean writeTransmission16;

    @Parameter(defaultValue = "false", label = "Thermal Part of Radiance at 3.7um")
    private boolean writeThermalPart37;

    @Parameter(defaultValue = "false", label = "Solar Part of Radiance at 3.7um")
    private boolean writeSolarPart37;

    @Parameter(defaultValue = "false", label = "Solar Part of Radiance at 3.7um (AATSR Units)")
    private boolean writeSolarPart37AatsrUnits;

    @Parameter(defaultValue = "false", label = "Number of Effective Windspeeds")
    private boolean writeNumberEffectiveWindspeeds;

    @Parameter(defaultValue = "false", label = "Effective Windspeed 1")
    private boolean writeEffectiveWindspeed1;

    @Parameter(defaultValue = "false", label = "Effective Windspeed 2")
    private boolean writeEffectiveWindspeed2;

    @Parameter(defaultValue = "false", label = "Radiance 1")
    private boolean writeRadiance1;

    @Parameter(defaultValue = "false", label = "Radiance 2")
    private boolean writeRadiance2;

    @Parameter(defaultValue = "false", label = "Effective Windspeed (Final Result)")
    private boolean writeEffectiveWindspeedFinal;

    @Parameter(defaultValue = "true", label = "Normalized Radiance (Final Result)")
    private boolean writeNormalizedRadianceFinal;

    private static final String INVALID_EXPRESSION = "l1_flags_M.INVALID";
    private Band invalidBand;

    /* AATSR L1 Cloud Flags (just the ones needed) */
    static final int AATSR_L1_CF_LAND = 0;
    static final int AATSR_L1_CF_CLOUDY = 1;
    static final int AATSR_L1_CF_SUNGLINT = 2;

    private FlintPreparation preparation;
    private FlintSolarPart37 solarPart37;
    private FlintSolarPart37WaterVapour solarPart37WaterVapour;
    private FlintGeometricalConversion geometricalConversion;

    private float solarIrradiance37;

    private Tile vaMerisTileComplete;
    private Tile vaAatsrNadirTileComplete;


    @Override
    public void initialize() throws OperatorException {

        preparation = new FlintPreparation();
        solarPart37 = new FlintSolarPart37();
        solarPart37WaterVapour = new FlintSolarPart37WaterVapour();
        geometricalConversion = new FlintGeometricalConversion();

        // todo: check if we need sth. like this!
//        collocateProduct.setPreferredTileSize(400, 400);

        try {
            solarPart37.loadFlintAuxData();
            solarPart37WaterVapour.loadFlintAuxData();
            geometricalConversion.loadFlintAuxData();
        } catch (Exception e) {
            throw new OperatorException("Failed to load flint auxdata:\n" + e.getMessage());
        }
        createTargetProduct();

        // get solar irradiance for day of year
        String startTime = collocateProduct.getMetadataRoot().getElement(
                "MPH").getAttribute("PRODUCT")
                .getData().getElemString().substring(14);     // e.g., 20030614

        final int dayOfYear = preparation.getDayOfYear(startTime);

        solarIrradiance37 = preparation.computeSolarIrradiance37(dayOfYear);

        // correction of azimuth discontinuity:
        // set up tiles for MERIS and AATSR which cover the whole scene...
        int sceneWidth = collocateProduct.getSceneRasterWidth();
        int sceneHeight = collocateProduct.getSceneRasterHeight();
        Rectangle rect = new Rectangle(0, 0, sceneWidth, sceneHeight);
        vaMerisTileComplete = getSourceTile(collocateProduct.getTiePointGrid("view_azimuth"), rect);
        vaAatsrNadirTileComplete = getSourceTile(collocateProduct.getBand("view_azimuth_nadir_S"), rect);

        // correct azimuths in these tiles for later usage...
        preparation.correctViewAzimuthLinear(vaMerisTileComplete, rect);
        preparation.correctViewAzimuthLinear(vaAatsrNadirTileComplete, rect);
    }

    //
    // This method creates the target product
    //
    private void createTargetProduct() {
        String productType = collocateProduct.getProductType();
        String productName = collocateProduct.getName();
        int sceneWidth = collocateProduct.getSceneRasterWidth();
        int sceneHeight = collocateProduct.getSceneRasterHeight();

        targetProduct = new Product(productName, productType, sceneWidth, sceneHeight);

        ProductUtils.copyTiePointGrids(collocateProduct, targetProduct);
        ProductUtils.copyGeoCoding(collocateProduct, targetProduct);
        ProductUtils.copyMetadata(collocateProduct, targetProduct);
//        setFlagBands();

        invalidBand = BandMathsHelper.createBooleanExpressionBand(INVALID_EXPRESSION, collocateProduct);

        setTargetBands();
    }

    private void setTargetBands() {
        // 'debug' bands: intermediate results part 1
        // todo: perhaps remove later
        if (writeWaterVapour) {
            Band resultStep1b1Band = targetProduct.addBand(STEP_1b1_RESULT_NAME, ProductData.TYPE_FLOAT32);
            resultStep1b1Band.setUnit("1/sr");
        }
        if (writeTransmission37) {
            targetProduct.addBand(STEP_1b2_RESULT1_NAME, ProductData.TYPE_FLOAT32);
        }
        if (writeTransmission16) {
            targetProduct.addBand(STEP_1b2_RESULT2_NAME, ProductData.TYPE_FLOAT32);
        }
        if (writeThermalPart37) {
            Band result2Step1cBand = targetProduct.addBand(STEP_1c_RESULT2_NAME, ProductData.TYPE_FLOAT32);
            result2Step1cBand.setUnit("1/sr");
        }
        if (writeSolarPart37) {
            Band result1Step1dBand = targetProduct.addBand(STEP_1d_RESULT1_NAME, ProductData.TYPE_FLOAT32);
            result1Step1dBand.setUnit("1/sr");
        }
        if (writeSolarPart37AatsrUnits) {
            Band result2Step1dBand = targetProduct.addBand(STEP_1d_RESULT2_NAME, ProductData.TYPE_FLOAT32);
            result2Step1dBand.setUnit("%");
        }

        // 'debug' bands: intermediate results part 2
        if (writeRadiance1) {
            Band resultRadiance1Band = targetProduct.addBand(RESULT_RADIANCE1_NAME, ProductData.TYPE_FLOAT32);
            resultRadiance1Band.setUnit("1/sr");
        }
        if (writeRadiance2) {
            Band resultRadiance2Band = targetProduct.addBand(RESULT_RADIANCE2_NAME, ProductData.TYPE_FLOAT32);
            resultRadiance2Band.setUnit("1/sr");
        }
        if (writeEffectiveWindspeed1) {
            Band resultWindspeed1Band = targetProduct.addBand(RESULT_WINDSPEED1_NAME, ProductData.TYPE_FLOAT32);
            resultWindspeed1Band.setUnit("m/s");
        }
        if (writeEffectiveWindspeed2) {
            Band resultWindspeed2Band = targetProduct.addBand(RESULT_WINDSPEED2_NAME, ProductData.TYPE_FLOAT32);
            resultWindspeed2Band.setUnit("m/s");
        }
        if (writeNumberEffectiveWindspeeds) {
            targetProduct.addBand(RESULT_NUMBERWINDSPEEDS_NAME, ProductData.TYPE_INT16);
        }

        // final result bands:
        if (writeEffectiveWindspeedFinal) {
            Band merisWindspeedFinalBand = targetProduct.addBand(RESULT_WINDSPEED_FINAL_NAME, ProductData.TYPE_FLOAT32);
            merisWindspeedFinalBand.setUnit("m/s");
        }
        if (writeNormalizedRadianceFinal) {
            Band merisRadianceFinalBand = targetProduct.addBand(RESULT_RADIANCE_FINAL_NAME, ProductData.TYPE_FLOAT32);
            merisRadianceFinalBand.setUnit("1/sr");
        }

        // debug output for view azimuth correction
//        Band vaAatsrBand = targetProduct.addBand("va_aatsr_corr", ProductData.TYPE_FLOAT32);
//        Band vaMerisBand = targetProduct.addBand("va_meris_corr", ProductData.TYPE_FLOAT32);
    }

    private void setFlagBands() {
        Band confidFlagNadirBand = targetProduct.addBand(CONFID_NADIR_FLAGS, ProductData.TYPE_INT16);
        Band confidFlagFwardBand = targetProduct.addBand(CONFID_FWARD_FLAGS, ProductData.TYPE_INT16);
        Band cloudFlagNadirBand = targetProduct.addBand(CLOUD_NADIR_FLAGS, ProductData.TYPE_INT16);
        Band cloudFlagFwardBand = targetProduct.addBand(CLOUD_FWARD_FLAGS, ProductData.TYPE_INT16);

        FlagCoding confidNadirFlagCoding = collocateProduct.getFlagCodingGroup().get(CONFID_NADIR_FLAGS);
        ProductUtils.copyFlagCoding(confidNadirFlagCoding, targetProduct);
        confidFlagNadirBand.setSampleCoding(confidNadirFlagCoding);

        FlagCoding confidFwardFlagCoding = collocateProduct.getFlagCodingGroup().get(CONFID_FWARD_FLAGS);
        ProductUtils.copyFlagCoding(confidFwardFlagCoding, targetProduct);
        confidFlagFwardBand.setSampleCoding(confidFwardFlagCoding);

        FlagCoding cloudNadirFlagCoding = collocateProduct.getFlagCodingGroup().get(CLOUD_NADIR_FLAGS);
        ProductUtils.copyFlagCoding(cloudNadirFlagCoding, targetProduct);
        cloudFlagNadirBand.setSampleCoding(cloudNadirFlagCoding);

        FlagCoding cloudFwardFlagCoding = collocateProduct.getFlagCodingGroup().get(CLOUD_FWARD_FLAGS);
        ProductUtils.copyFlagCoding(cloudFwardFlagCoding, targetProduct);
        cloudFlagFwardBand.setSampleCoding(cloudFwardFlagCoding);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();

        if (targetBand.isFlagBand()) {
            // no computations
            return;
        }

        pm.beginTask("Processing frame...", rectangle.height);

        try {
            Tile szMerisTile = getSourceTile(collocateProduct.getTiePointGrid("sun_zenith"), rectangle);
            Tile vzMerisTile = getSourceTile(collocateProduct.getTiePointGrid("view_zenith"), rectangle);
            Tile saMerisTile = getSourceTile(collocateProduct.getTiePointGrid("sun_azimuth"), rectangle);
            Tile zonalWindTile = getSourceTile(collocateProduct.getTiePointGrid("zonal_wind"), rectangle);
            Tile meridWindTile = getSourceTile(collocateProduct.getTiePointGrid("merid_wind"), rectangle);

            Tile seAatsrNadirTile = getSourceTile(collocateProduct.getBand("sun_elev_nadir_S"), rectangle);
            Tile veAatsrNadirTile = getSourceTile(collocateProduct.getBand("view_elev_nadir_S"), rectangle);
            Tile saAatsrNadirTile = getSourceTile(collocateProduct.getBand("sun_azimuth_nadir_S"), rectangle);
            Tile cfAatsrNadirTile = getSourceTile(collocateProduct.getBand("cloud_flags_nadir_S"), rectangle);

            Tile merisRad14Tile = getSourceTile(collocateProduct.getBand("radiance_14_M"), rectangle);
            Tile merisRad15Tile = getSourceTile(collocateProduct.getBand("radiance_15_M"), rectangle);
            Tile aatsrReflNadir1600Tile = getSourceTile(collocateProduct.getBand("reflec_nadir_1600_S"), rectangle);
            Tile aatsrBTNadir0370Tile = getSourceTile(collocateProduct.getBand("btemp_nadir_0370_S"), rectangle);
            Tile aatsrBTNadir1100Tile = getSourceTile(collocateProduct.getBand("btemp_nadir_1100_S"), rectangle);
            Tile aatsrBTNadir1200Tile = getSourceTile(collocateProduct.getBand("btemp_nadir_1200_S"), rectangle);

            Tile isInvalid = getSourceTile(invalidBand, rectangle);
            FlintGeometricalConversion conversion = geometricalConversion.clone();
            FlintSolarPart37WaterVapour waterVapour = solarPart37WaterVapour.clone();

            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    if (pm.isCanceled()) {
                        break;
                    }

                    final boolean cloudFlagNadirLand = cfAatsrNadirTile.getSampleBit(x, y, AATSR_L1_CF_LAND);
                    final boolean cloudFlagNadirCloudy = cfAatsrNadirTile.getSampleBit(x, y, AATSR_L1_CF_CLOUDY);
                    final boolean cloudFlagNadirSunglint = cfAatsrNadirTile.getSampleBit(x, y, AATSR_L1_CF_SUNGLINT);
                    final float aatsrViewElevation = veAatsrNadirTile.getSampleFloat(x, y);
                    final float aatsrSunElevation = seAatsrNadirTile.getSampleFloat(x, y);
                    final float aatsrBt37 = aatsrBTNadir0370Tile.getSampleFloat(x, y);
                    if (isInvalid.getSampleBoolean(x, y)
                        || !preparation.isUsefulPixel(cloudFlagNadirLand, cloudFlagNadirCloudy, cloudFlagNadirSunglint,
                                                      aatsrViewElevation, aatsrBt37)) {
                        targetTile.setSample(x, y, 0);
                    } else {

                        // 1. The solar part of 3.7
                        // 1.a. Thermal extrapolation of 11/12 to 3.7
                        final float aatsrBTThermalPart37 =
                                solarPart37.extrapolateTo37(aatsrBTNadir1100Tile.getSampleFloat(x, y),
                                                            aatsrBTNadir1200Tile.getSampleFloat(x, y));

                        // 1.b.1 Calculation of water vapour
                        final float zonalWind = zonalWindTile.getSampleFloat(x, y);
                        final float meridWind = meridWindTile.getSampleFloat(x, y);
                        float merisViewAzimuth = vaMerisTileComplete.getSampleFloat(x, y);
                        float merisSunAzimuth = saMerisTile.getSampleFloat(x, y);
                        float merisAzimuthDifference = preparation.removeAzimuthDifferenceAmbiguity(merisViewAzimuth,
                                                                                                    merisSunAzimuth);
                        final float merisViewZenith = vzMerisTile.getSampleFloat(x, y);
                        final float merisSunZenith = szMerisTile.getSampleFloat(x, y);
                        final float merisRad14 = merisRad14Tile.getSampleFloat(x, y);
                        final float merisRad15 = merisRad15Tile.getSampleFloat(x, y);

                        float waterVapourColumn = waterVapour.computeWaterVapour(zonalWind, meridWind,
                                                                                 merisAzimuthDifference,
                                                                                 merisViewZenith, merisSunZenith,
                                                                                 merisRad14, merisRad15);
                        if (targetBand.getName().equals(STEP_1b1_RESULT_NAME)) {
                            targetTile.setSample(x, y, waterVapourColumn);
                        }

                        // 1.b.2 Calculation of transmission
                        final float aatsrTrans37 = solarPart37.computeTransmission(37, waterVapourColumn,
                                                                                   90.0f - aatsrSunElevation,
                                                                                   90.0f - aatsrViewElevation);
                        final float aatsrTrans16 = solarPart37.computeTransmission(16, waterVapourColumn,
                                                                                   90.0f - aatsrSunElevation,
                                                                                   90.0f - aatsrViewElevation);
                        if (targetBand.getName().equals(STEP_1b2_RESULT1_NAME)) {
                            targetTile.setSample(x, y, aatsrTrans37);
                        }
                        if (targetBand.getName().equals(STEP_1b2_RESULT2_NAME)) {
                            targetTile.setSample(x, y, aatsrTrans16);
                        }

                        // 1.c Conversion of BT to normalized radiance
                        final float aatsrRad37 = solarPart37.convertBT2Radiance(aatsrBt37) / solarIrradiance37;
                        final float aatsrRadianceThermalPart37 = solarPart37.convertBT2Radiance(
                                aatsrBTThermalPart37) / solarIrradiance37;
                        if (targetBand.getName().equals(STEP_1c_RESULT1_NAME)) {
                            targetTile.setSample(x, y, aatsrRad37);
                        }
                        if (targetBand.getName().equals(STEP_1c_RESULT2_NAME)) {
                            targetTile.setSample(x, y, aatsrRadianceThermalPart37);
                        }

                        // 1.d Compute the solar part
                        final float aatsrSolarPart37 =
                                solarPart37.computeSolarPart(aatsrRad37, aatsrRadianceThermalPart37, aatsrTrans37);
                        final float aatsrSolarPart37a =
                                solarPart37.convertToAatsrUnits(aatsrSolarPart37, aatsrSunElevation);

                        // 1.e Simple additional cloud mask
                        final float aatsrRefl16 = aatsrReflNadir1600Tile.getSampleFloat(x, y);
                        final float aatsrRefl16T = aatsrRefl16 / aatsrTrans16;

//                        boolean cloud = solarPart37.computeAdditionalCloudMask(aatsrSolarPart37a, aatsrRefl16T);
                        boolean cloud = false; // perhaps activate if needed

                        if (targetBand.getName().equals(STEP_1b2_RESULT2_NAME)) {
                            targetTile.setSample(x, y, aatsrRefl16T / 0.79);
                        }
                        if (targetBand.getName().equals(STEP_1e_RESULT_NAME)) {
                            if (cloud) {
                                targetTile.setSample(x, y, 1);
                            } else {
                                targetTile.setSample(x, y, 0);
                            }
                        }

                        // output of part 1:
                        if (targetBand.getName().equals(STEP_1d_RESULT1_NAME)) {
                            if (!cloud) {
                                targetTile.setSample(x, y, aatsrSolarPart37);
                            } else {
                                targetTile.setSample(x, y, -1.0f);
                            }
                        }
                        if (targetBand.getName().equals(STEP_1d_RESULT2_NAME)) {
                            if (!cloud) {
                                targetTile.setSample(x, y, aatsrSolarPart37a);
                            } else {
                                targetTile.setSample(x, y, -1.0f);
                            }
                        }

                        if ("va_aatsr_corr".equals(targetBand.getName())) {
                            targetTile.setSample(x, y, vaAatsrNadirTileComplete.getSampleFloat(x, y));
                        }
                        if ("va_meris_corr".equals(targetBand.getName())) {
                            targetTile.setSample(x, y, vaMerisTileComplete.getSampleFloat(x, y));
                        }

                        // 2. The geometrical conversion
                        if (!cloud && targetBand.getName().startsWith("result_")) {
                            // 2.a AATSR - MERIS conversion
                            float aatsrViewAzimuth = vaAatsrNadirTileComplete.getSampleFloat(x, y);
                            float aatsrSunAzimuth = saAatsrNadirTile.getSampleFloat(x, y);

                            float aatsrAzimuthDifference = preparation.removeAzimuthDifferenceAmbiguity(
                                    aatsrViewAzimuth,
                                    aatsrSunAzimuth);


                            final float[][] merisNormalizedRadianceResultMatrix =
                                    conversion.convertAatsrRad37ToMerisRad(aatsrSolarPart37, merisSunZenith,
                                                                           merisViewZenith,
                                                                           180.0f - aatsrAzimuthDifference,
                                                                           180.0f - merisAzimuthDifference);

                            if (targetBand.getName().equals(RESULT_NUMBERWINDSPEEDS_NAME)) {
                                int numberWindspeeds = FlintGeometricalConversion.windspeedFound(
                                        merisNormalizedRadianceResultMatrix);
                                targetTile.setSample(x, y, numberWindspeeds);
                            }

                            // 2.b Ambiuguity reduction and final output
                            if (FlintGeometricalConversion.windspeedFound(merisNormalizedRadianceResultMatrix) > 0) {
                                final float[] finalResultWindspeedRadiance = FlintGeometricalConversion.getAmbiguityReducedRadiance
                                        (merisNormalizedRadianceResultMatrix, zonalWind, meridWind);
                                if (targetBand.getName().equals(RESULT_RADIANCE1_NAME)) {
                                    targetTile.setSample(x, y, merisNormalizedRadianceResultMatrix[0][1]);
                                }
                                if (targetBand.getName().equals(RESULT_WINDSPEED1_NAME)) {
                                    targetTile.setSample(x, y, merisNormalizedRadianceResultMatrix[0][0]);
                                }
                                if (targetBand.getName().equals(RESULT_RADIANCE2_NAME)) {
                                    targetTile.setSample(x, y, merisNormalizedRadianceResultMatrix[1][1]);
                                }
                                if (targetBand.getName().equals(RESULT_WINDSPEED2_NAME)) {
                                    targetTile.setSample(x, y, merisNormalizedRadianceResultMatrix[1][0]);
                                }

                                // these are the final results
                                if (targetBand.getName().equals(RESULT_WINDSPEED_FINAL_NAME)) {
                                    targetTile.setSample(x, y, finalResultWindspeedRadiance[0]);
                                }
                                if (targetBand.getName().equals(RESULT_RADIANCE_FINAL_NAME)) {
                                    targetTile.setSample(x, y, finalResultWindspeedRadiance[1]);
                                }
                            }
                        }
                    }
                }
                pm.worked(1);
            }
        } catch (Exception e) {
            throw new OperatorException("Failed to process Flint algorithm:\n" + e.getMessage(), e);
        } finally {
            pm.done();
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(FlintOp.class);
        }
    }
}
