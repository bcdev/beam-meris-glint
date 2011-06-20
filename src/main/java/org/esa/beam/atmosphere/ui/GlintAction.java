package org.esa.beam.atmosphere.ui;

import org.esa.beam.atmosphere.operator.GlintCorrectionOperator;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * GLINT Action class
 *
 * @author Olaf Danne
 * @version $Revision: 2703 $ $Date: 2010-01-21 13:51:07 +0100 (Do, 21 Jan 2010) $
 */
public class GlintAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {
        final String version = GlintCorrectionOperator.GLINT_CORRECTION_VERSION;
        final String helpId = event.getCommand().getHelpId();
        final GlintDialog productDialog = new GlintDialog(
                "Meris.GlintCorrection", getAppContext(),
                "MERIS/AATSR Glint Correction (AGC/FLINT) - v" + version, helpId);
        productDialog.show();
    }

}
