package cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.ui;

import cz.cuni.lf1.lge.ThunderSTORM.calibration.CylindricalLensCalibration;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.EllipticGaussianWAnglePSF;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.PSFModel;
import cz.cuni.lf1.lge.ThunderSTORM.util.GridBagHelper;
import cz.cuni.lf1.lge.ThunderSTORM.util.Range;
import cz.cuni.lf1.lge.ThunderSTORM.util.RangeValidatorFactory;
import cz.cuni.lf1.lge.thunderstorm.util.macroui.ParameterKey;
import cz.cuni.lf1.lge.thunderstorm.util.macroui.validators.StringValidatorFactory;
import java.awt.GridBagLayout;
import java.io.FileNotFoundException;
import java.io.FileReader;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.yaml.snakeyaml.Yaml;

public class EllipticGaussianWAngleUI extends IPsfUI {

    private final String name = "Eliptical Gaussian (3D astigmatism)";
    private final transient ParameterKey.String CALIBRATION = parameters.createStringField("calibration", StringValidatorFactory.fileExists(), Defaults.CALIBRATION);
    private final transient ParameterKey.String Z_RANGE = parameters.createStringField("z_range", RangeValidatorFactory.fromTo(), Defaults.Z_RANGE);
    
    private CylindricalLensCalibration calibration;
    
    @Override
    public String getName() {
        return name;
    }

    @Override
    public JPanel getOptionsPanel() {
        JTextField calibrationTextField = new JTextField("", 20);
        JTextField zRangeTextField = new JTextField("", 20);
        parameters.registerComponent(CALIBRATION, calibrationTextField);
        parameters.registerComponent(Z_RANGE, zRangeTextField);
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(new JLabel("Calibration file:"), GridBagHelper.leftCol());
        panel.add(calibrationTextField, GridBagHelper.rightCol());
        panel.add(new JLabel("Z-range (from:to) [nm]:"), GridBagHelper.leftCol());
        panel.add(zRangeTextField, GridBagHelper.rightCol());
        
        parameters.loadPrefs();
        return panel;
    }

    @Override
    public PSFModel getImplementation() {
        return new EllipticGaussianWAnglePSF(1.6, 0);
    }

    @Override
    public double getAngle() {
        if(calibration == null) {
            calibration = loadCalibration(CALIBRATION.getValue());
        }
        return Math.toRadians(calibration.getAngle());
    }

    @Override
    public Range getZRange() {
        return Range.parseFromTo(Z_RANGE.getValue());
    }

    @Override
    public double getSigma1(double z) {
        if(calibration == null) {
            calibration = loadCalibration(CALIBRATION.getValue());
        }
        return calibration.getSigma1(z);
    }

    @Override
    public double getSigma2(double z) {
        if(calibration == null) {
            calibration = loadCalibration(CALIBRATION.getValue());
        }
        return calibration.getSigma2(z);
    }

    static class Defaults {
        public static final String CALIBRATION = "";
        public static final String Z_RANGE = "-300:+300";
    }
    
    private CylindricalLensCalibration loadCalibration(String calibrationFilePath) {
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(new FileReader(calibrationFilePath));
            return (CylindricalLensCalibration) loaded;
        } catch(FileNotFoundException ex) {
            throw new RuntimeException("Could not read calibration file.", ex);
        } catch(ClassCastException ex) {
            throw new RuntimeException("Could not parse calibration file.", ex);
        }
    }
    
}
