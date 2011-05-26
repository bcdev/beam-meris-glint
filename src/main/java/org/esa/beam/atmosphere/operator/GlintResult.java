package org.esa.beam.atmosphere.operator;

/**
 * Class representing a result from the AGC Glint correction.
 *
 * @author Marco Peters, Olaf Danne
 * @version $Revision: 2703 $ $Date: 2010-01-21 13:51:07 +0100 (Do, 21 Jan 2010) $
 */
public class GlintResult {

    private double[] tosaReflec;
    private double[] reflec;
    private double reflecError;
    private double[] normReflec;
    private double normReflecError;
    private double[] path;
    private double[] trans;
    private double angstrom;
    private double tau550;
    private double tau778;
    private double tau865;
    private double glintRatio;
    private double btsm;
    private double atot;
    private int flag;

    public GlintResult() {
        tosaReflec = new double[12];
        reflec = new double[12];
        reflecError = 0;
        normReflec = new double[12];
        normReflecError = 0;
        path = new double[12];
        trans = new double[12];
        angstrom = 0;
        tau550 = 0;
        tau778 = 0;
        tau865 = 0;
        glintRatio = 0;
        btsm = 0;
        atot = 0;
        flag = 0;
    }

    public void setTosaReflec(double[] tosaReflec) {
        this.tosaReflec = tosaReflec;
    }

    public double[] getTosaReflec() {
        return tosaReflec;
    }

    public void setReflec(double[] reflec) {
        this.reflec = reflec;
    }

    public double[] getReflec() {
        return reflec;
    }

    public void setReflecError(double error) {
        reflecError = error;
    }

    public double getReflecError() {
        return reflecError;
    }

    public void setNormReflec(double[] normReflec) {
        this.normReflec = normReflec;
    }

    public double[] getNormReflec() {
        return normReflec;
    }

    public void setNormReflecError(double error) {
        normReflecError = error;
    }

    public double getNormReflecError() {
        return normReflecError;
    }


    public void setPath(double[] path) {
        this.path = path;
    }

    public double[] getPath() {
        return path;
    }

    public void setTrans(double[] trans) {
        this.trans = trans;
    }

    public double[] getTrans() {
        return trans;
    }

    public void setAngstrom(double angstrom) {
        this.angstrom = angstrom;
    }

    public double getAngstrom() {
        return angstrom;
    }

    public void setTau550(double tau550) {
        this.tau550 = tau550;
    }

    public double getTau550() {
        return tau550;
    }

    public void setTau778(double tau778) {
        this.tau778 = tau778;
    }

    public double getTau778() {
        return tau778;
    }

    public void setTau865(double tau865) {
        this.tau865 = tau865;
    }

    public double getTau865() {
        return tau865;
    }

    public void setGlintRatio(double glintRatio) {
        this.glintRatio = glintRatio;
    }

    public double getGlintRatio() {
        return glintRatio;
    }

    public void setBtsm(double btsm) {
        this.btsm = btsm;
    }

    public double getBtsm() {
        return btsm;
    }

    public void setAtot(double atot) {
        this.atot = atot;
    }

    public double getAtot() {
        return atot;
    }

    public void raiseFlag(int flag) {
        this.flag |= flag;
    }

    public int getFlag() {
        return flag;
    }

}
