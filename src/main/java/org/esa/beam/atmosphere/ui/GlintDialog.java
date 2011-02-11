package org.esa.beam.atmosphere.ui;

import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.PropertyDescriptor;
import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.ValueSet;
import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.PropertyPane;
import com.bc.ceres.swing.selection.AbstractSelectionChangeListener;
import com.bc.ceres.swing.selection.Selection;
import com.bc.ceres.swing.selection.SelectionChangeEvent;
import com.bc.ceres.swing.selection.SelectionChangeListener;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductFilter;
import org.esa.beam.framework.datamodel.ProductNodeEvent;
import org.esa.beam.framework.datamodel.ProductNodeListener;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.annotations.ParameterDescriptorFactory;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.internal.RasterDataNodeValues;
import org.esa.beam.framework.gpf.ui.SingleTargetProductDialog;
import org.esa.beam.framework.gpf.ui.SourceProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.AppContext;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.awt.Font;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * GLINT Dialog class
 *
 * @author Olaf Danne
 * @version $Revision: 2703 $ $Date: 2010-01-21 13:51:07 +0100 (Do, 21 Jan 2010) $
 */
public class GlintDialog extends SingleTargetProductDialog {
     private String operatorName;
    private List<SourceProductSelector> sourceProductSelectorList;
    private Map<Field, SourceProductSelector> sourceProductSelectorMap;
    private Map<String, Object> parameterMap;
    private JTabbedPane form;
    private String targetProductNameSuffix;
    private JCheckBox useFlintProductCheckBox;
    private PropertyContainer propertyContainer;
    private JPanel flintNetPanel;
    private PropertyDescriptor[] rasterDataNodeTypeProperties;
    private ProductChangedHandler productChangedHandler;

    public static GlintDialog createDefaultDialog(String operatorName, AppContext appContext) {
        return new GlintDialog(operatorName, appContext, operatorName, null);
    }

    public GlintDialog(String operatorName, AppContext appContext, String title, String helpID) {
        super(appContext, title, helpID);
        this.operatorName = operatorName;
        targetProductNameSuffix = "";

        final OperatorSpi operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi(operatorName);
        if (operatorSpi == null) {
            throw new IllegalArgumentException("operatorName");
        }

        sourceProductSelectorList = new ArrayList<SourceProductSelector>(3);
        sourceProductSelectorMap = new HashMap<Field, SourceProductSelector>(3);
        // Fetch source products
        initSourceProductSelectors(operatorSpi);
        if (!sourceProductSelectorList.isEmpty()) {
            setSourceProductSelectorLabels();
            setSourceProductSelectorToolTipTexts();
        }

        final TableLayout tableLayout = new TableLayout(1);
        tableLayout.setTableAnchor(TableLayout.Anchor.WEST);
        tableLayout.setTableWeightX(1.0);
        tableLayout.setTableFill(TableLayout.Fill.HORIZONTAL);
        tableLayout.setTablePadding(3, 3);

        JPanel ioParametersPanel = new JPanel(tableLayout);

        SourceProductSelector selectorMeris = sourceProductSelectorList.get(0);
        ioParametersPanel.add(selectorMeris.createDefaultPanel());
        
        ioParametersPanel.add(createAatsrProductUsagePanel());
        ioParametersPanel.add(tableLayout.createVerticalSpacer());

        resetFlintProductSelector();

        ioParametersPanel.add(getTargetProductSelector().createDefaultPanel());
        ioParametersPanel.add(tableLayout.createVerticalSpacer());

        this.form = new JTabbedPane();
        this.form.add("I/O Parameters", ioParametersPanel);

        ParameterDescriptorFactory parameterDescriptorFactory = new ParameterDescriptorFactory();
        parameterMap = new HashMap<String, Object>(17);
        propertyContainer = PropertyContainer.createMapBacked(parameterMap,
                                                                             operatorSpi.getOperatorClass(),
                                                                             parameterDescriptorFactory);
        propertyContainer.setDefaultValues();

        if (propertyContainer.getProperties().length > 0) {
            if (!sourceProductSelectorList.isEmpty()) {
                Property[] properties = propertyContainer.getProperties();
                List<PropertyDescriptor> rdnTypeProperties = new ArrayList<PropertyDescriptor>(properties.length);
                for (Property property : properties) {
                    PropertyDescriptor parameterDescriptor = property.getDescriptor();
                    if (parameterDescriptor.getAttribute(RasterDataNodeValues.ATTRIBUTE_NAME) != null) {
                        rdnTypeProperties.add(parameterDescriptor);
                    }
                }
                rasterDataNodeTypeProperties = rdnTypeProperties.toArray(new PropertyDescriptor[rdnTypeProperties.size()]);
            }

            PropertyPane parametersPane = new PropertyPane(propertyContainer);
            final JPanel paremetersPanel = parametersPane.createPanel();
            Component[] components = paremetersPanel.getComponents();

            for (int i = 0; i < components.length - 1; i++) {
                if (components[i] instanceof JCheckBox && ((JCheckBox) components[i]).getText().startsWith("Use FLINT value")) {
                    JCheckBox useFlintCheckBox = (JCheckBox) paremetersPanel.getComponents()[i];
                    useFlintCheckBox.setEnabled(false);
                }
                if (components[i] instanceof JLabel && ((JLabel) components[i]).getText().startsWith("FLINT net")) {
                    flintNetPanel = (JPanel) components[i + 1];
                    for (int j = 0; j < flintNetPanel.getComponents().length; j++) {
                        flintNetPanel.getComponents()[j].setEnabled(false);
                    }
                }
            }

            paremetersPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
            this.form.add("Processing Parameters", new JScrollPane(paremetersPanel));
        }
        if (!sourceProductSelectorList.isEmpty()) {
            productChangedHandler = new ProductChangedHandler();
            sourceProductSelectorList.get(0).addSelectionChangeListener(productChangedHandler);
            sourceProductSelectorList.get(1).addSelectionChangeListener(new SelectionChangeListener() {
                @Override
                public void selectionChanged(SelectionChangeEvent event) {
                    useFlintProductCheckBox.setSelected(true);
                }

                @Override
                public void selectionContextChanged(SelectionChangeEvent event) {
                }
            });
        }

    }

    private JPanel createAatsrProductUsagePanel() {
	    TableLayout layout = new TableLayout(1);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setTableWeightX(1);

        JPanel panel = new JPanel(layout);
        panel.setBorder(BorderFactory.createTitledBorder(null, "AATSR (FLINT) Product Usage",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Tahoma", 0, 11),
                new Color(0, 70, 213)));

        if (useFlintProductCheckBox == null) {
            useFlintProductCheckBox = new JCheckBox("Create FLINT product from AATSR L1b and use as AGC input");
            useFlintProductCheckBox.setSelected(false);
            panel.add(useFlintProductCheckBox);
        }

        SourceProductSelector selectorAatsr = sourceProductSelectorList.get(1);
        panel.add(selectorAatsr.createDefaultPanel());

        ActionListener useFlintProductListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetFlintProductSelector();
            }
        };
        useFlintProductCheckBox.addActionListener(useFlintProductListener);

        return panel;
	}

    private void initSourceProductSelectors(OperatorSpi operatorSpi) {
        final Field[] fields = operatorSpi.getOperatorClass().getDeclaredFields();
        for (Field field : fields) {
            final SourceProduct annot = field.getAnnotation(SourceProduct.class);
            if (annot != null) {
                final ProductFilter productFilter = new AnnotatedSourceProductFilter(annot);
                SourceProductSelector sourceProductSelector = new SourceProductSelector(getAppContext());
                sourceProductSelector.setProductFilter(productFilter);
                sourceProductSelectorList.add(sourceProductSelector);
                sourceProductSelectorMap.put(field, sourceProductSelector);
            }
        }
    }


    private void setSourceProductSelectorLabels() {
        for (Field field : sourceProductSelectorMap.keySet()) {
            final SourceProductSelector selector = sourceProductSelectorMap.get(field);
            String label = null;
            final SourceProduct annot = field.getAnnotation(SourceProduct.class);
            if (!annot.label().isEmpty()) {
                label = annot.label();
            }
            if (label == null && !annot.alias().isEmpty()) {
                label = annot.alias();
            }
            if (label == null) {
                String name = field.getName();
                if (!annot.alias().isEmpty()) {
                    name = annot.alias();
                }
                label = PropertyDescriptor.createDisplayName(name);
            }
            if (!label.endsWith(":")) {
                label += ":";
            }
            selector.getProductNameLabel().setText(label);
        }
    }

    private void setSourceProductSelectorToolTipTexts() {
        for (Field field : sourceProductSelectorMap.keySet()) {
            final SourceProductSelector selector = sourceProductSelectorMap.get(field);

            final SourceProduct annot = field.getAnnotation(SourceProduct.class);
            final String description = annot.description();
            if (!description.isEmpty()) {
                selector.getProductNameComboBox().setToolTipText(description);
            }
        }
    }

    private void resetFlintProductSelector() {
        for (Field field : sourceProductSelectorMap.keySet()) {
            final SourceProductSelector selector = sourceProductSelectorMap.get(field);
            if (selector != null) {
                final SourceProduct annot = field.getAnnotation(SourceProduct.class);
                if (annot.label() != null && annot.label().startsWith("AATSR")) {
                    selector.getProductFileChooserButton().setEnabled(useFlintProductCheckBox.isSelected());
                    selector.getProductNameComboBox().setEnabled(useFlintProductCheckBox.isSelected());
                    selector.releaseProducts();
                }
            }
        }
        if (propertyContainer != null)  {
            for (Property property : propertyContainer.getProperties()) {
                // set 'useFlint' parameter according to useFlintProductCheckBox:
                if (property.getDescriptor().getName().equals("useFlint")) {
                    try {
                        property.setValue(useFlintProductCheckBox.isSelected());
                        if (flintNetPanel != null) {
                            for (int j = 0; j < flintNetPanel.getComponents().length; j++) {
                                flintNetPanel.getComponents()[j].setEnabled(useFlintProductCheckBox.isSelected());
                            }
                        }
                    } catch (ValidationException e) {
                        throw new OperatorException(e.getMessage());
                    }
                }
            }
        }
        if (useFlintProductCheckBox.isSelected()) {
            String flintInfoMessage = "The FLINT processor is a beta version and will be further improved in the\n" +
                                      "frame of other projects. The current results should be interpreted with care.";
            showSuppressibleInformationDialog(flintInfoMessage, "");
        }  
    }


    @Override
    public int show() {
        initSourceProductSelectors();
        setContent(form);
        return super.show();
    }

    @Override
    public void hide() {
        productChangedHandler.releaseProduct();
        releaseSourceProductSelectors();
        super.hide();
    }

    @Override
    protected Product createTargetProduct() throws Exception {
        final HashMap<String, Product> sourceProducts = createSourceProductsMap();
        return GPF.createProduct(operatorName, parameterMap, sourceProducts);
    }

    private void initSourceProductSelectors() {
        for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
            sourceProductSelector.initProducts();
        }
    }

    private void releaseSourceProductSelectors() {
        for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
            sourceProductSelector.releaseProducts();
        }
    }

    private HashMap<String, Product> createSourceProductsMap() {
        final HashMap<String, Product> sourceProducts = new HashMap<String, Product>(8);
        for (Field field : sourceProductSelectorMap.keySet()) {
            final SourceProductSelector selector = sourceProductSelectorMap.get(field);
            String key = field.getName();
            final SourceProduct annot = field.getAnnotation(SourceProduct.class);
            if (!annot.alias().isEmpty()) {
                key = annot.alias();
            }
            sourceProducts.put(key, selector.getSelectedProduct());
        }
        return sourceProducts;
    }

    public String getTargetProductNameSuffix() {
        return targetProductNameSuffix;
    }

    public void setTargetProductNameSuffix(String suffix) {
        targetProductNameSuffix = suffix;
    }
    
    private static class AnnotatedSourceProductFilter implements ProductFilter {

        private final SourceProduct annot;

        private AnnotatedSourceProductFilter(SourceProduct annot) {
            this.annot = annot;
        }

        @Override
        public boolean accept(Product product) {

            if (!annot.type().isEmpty() && !product.getProductType().matches(annot.type())) {
                return false;
            }

            for (String bandName : annot.bands()) {
                if (!product.containsBand(bandName)) {
                    return false;
                }
            }

            return true;
        }
    }


    private class ProductChangedHandler extends AbstractSelectionChangeListener implements ProductNodeListener {

        private Product currentProduct;

        public void releaseProduct() {
            if (currentProduct != null) {
                currentProduct.removeProductNodeListener(this);
                currentProduct = null;
            }
        }

        @Override
        public void selectionChanged(SelectionChangeEvent event) {
            Selection selection = event.getSelection();
            if (selection != null) {
                final Product selectedProduct = (Product) selection.getSelectedValue();
                if (selectedProduct != currentProduct) {
                    if (currentProduct != null) {
                        currentProduct.removeProductNodeListener(this);
                    }
                    currentProduct = selectedProduct;
                    if (currentProduct != null) {
                        currentProduct.addProductNodeListener(this);
                    }
                    updateTargetProductname();
                    updateValueSets(currentProduct);
                }
            }
        }

        @Override
        public void nodeAdded(ProductNodeEvent event) {
            handleProductNodeEvent(event);
        }

        @Override
        public void nodeChanged(ProductNodeEvent event) {
            handleProductNodeEvent(event);
        }

        @Override
        public void nodeDataChanged(ProductNodeEvent event) {
            handleProductNodeEvent(event);
        }

        @Override
        public void nodeRemoved(ProductNodeEvent event) {
            handleProductNodeEvent(event);
        }

        private void updateTargetProductname() {
            String productName = "";
            if (currentProduct != null) {
                productName = currentProduct.getName();
            }
            final TargetProductSelectorModel targetProductSelectorModel = getTargetProductSelector().getModel();
            targetProductSelectorModel.setProductName(productName + getTargetProductNameSuffix());
        }

        private void handleProductNodeEvent(ProductNodeEvent event) {
            updateValueSets(currentProduct);
        }

        private void updateValueSets(Product product) {
            if (rasterDataNodeTypeProperties != null) {
                for (PropertyDescriptor propertyDescriptor : rasterDataNodeTypeProperties) {
                    updateValueSet(propertyDescriptor, product);
                }
            }
        }
    }

    private static void updateValueSet(PropertyDescriptor propertyDescriptor, Product product) {
        String[] values = new String[0];
        if (product != null) {
            Object object = propertyDescriptor.getAttribute(RasterDataNodeValues.ATTRIBUTE_NAME);
            if (object != null) {
                Class<? extends RasterDataNode> rasterDataNodeType = (Class<? extends RasterDataNode>) object;
                boolean includeEmptyValue = !propertyDescriptor.isNotNull() && !propertyDescriptor.getType().isArray();
                values = RasterDataNodeValues.getNames(product, rasterDataNodeType, includeEmptyValue);
            }
        }
        propertyDescriptor.setValueSet(new ValueSet(values));
    }
}
