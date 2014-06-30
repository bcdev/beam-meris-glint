package org.esa.beam.atmosphere.operator;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.junit.Test;

import static org.junit.Assert.*;

public class GlintCorrectionOperatorTest {

    @Test
    public void testIsProductMerisFullResolution_FromGlobalAttributes() throws Exception {
        Product product = new Product("dummy", "type", 2, 2);
        MetadataElement globalAttributes = new MetadataElement("Global_Attributes");
        product.getMetadataRoot().addElement(globalAttributes);

        MetadataAttribute productTypeAttribute = new MetadataAttribute("product_type", ProductData.createInstance("MER_RRG_L1P"), false);
        globalAttributes.addAttribute(productTypeAttribute);
        assertFalse(GlintCorrectionOperator.isProductMerisFullResolution(product));

        globalAttributes.removeAttribute(productTypeAttribute);

        productTypeAttribute = new MetadataAttribute("product_type", ProductData.createInstance("MER_FSG"), false);
        globalAttributes.addAttribute(productTypeAttribute);
        assertTrue(GlintCorrectionOperator.isProductMerisFullResolution(product));
    }

    @Test
    public void testIsProductMerisFullResolution_FromProductType() throws Exception {
        Product product = new Product("dummy", "type", 2, 2);
        product.setProductType("MER_RRG_L1P");
        assertFalse(GlintCorrectionOperator.isProductMerisFullResolution(product));

        product.setProductType("MER_FSG");
        assertTrue(GlintCorrectionOperator.isProductMerisFullResolution(product));
    }

}