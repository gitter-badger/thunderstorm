package cz.cuni.lf1.lge.ThunderSTORM.estimators.ui;

import cz.cuni.lf1.lge.ThunderSTORM.calibration.DefocusFunction;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.*;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.EllipticGaussianPSF;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.EllipticGaussianWAnglePSF;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.PSFModel;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.PSFModel.Params;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.SymmetricGaussianPSF;

import javax.swing.*;

public class BiplaneCalibrationEstimatorUI extends SymmetricGaussianEstimatorUI {

    private final String name = "Symmetric Gaussian";
    private DefocusFunction defocus = null;

    public BiplaneCalibrationEstimatorUI() {
        super();
        crowdedField = new CrowdedFieldEstimatorUI() {
            @Override
            OneLocationFitter getLSQImplementation(PSFModel psf, double sigma) {
                return null;
            }

            @Override
            OneLocationFitter getMLEImplementation(PSFModel psf, double sigma) {
                return null;
            }

            @Override
            public void resetToDefaults() {
            }

            @Override
            public void readMacroOptions(String options) {
            }

            @Override
            public void recordOptions() {
            }

            @Override
            public void readParameters() {
            }

            @Override
            public JPanel getOptionsPanel(JPanel panel) {
                return panel;
            }

            @Override
            public boolean isEnabled() {
                return false;
            }
        };
    }

    @Override
    public String getName() {
        return name;
    }

    public void setDefocusModel(DefocusFunction defocus) {
        this.defocus = defocus;
    }

    public int getFitradius() {
        return parameters.getInt(FITRAD);
    }

    @Override
    public IEstimator getImplementation() {
        String method = METHOD.getValue();
        double sigma = SIGMA.getValue();
        int fitradius = FITRAD.getValue();
        PSFModel psf = new SymmetricGaussianPSF(sigma);
        if(LSQ.equals(method) || WLSQ.equals(method)) {
            LSQFitter fitter = new LSQFitter(psf, WLSQ.equals(method), Params.BACKGROUND);
            return new MultipleLocationsImageFitting(fitradius, fitter);
        }
        if(MLE.equals(method)) {
            MLEFitter fitter = new MLEFitter(psf, Params.BACKGROUND);
            return new MultipleLocationsImageFitting(fitradius, fitter);
        }
        throw new IllegalArgumentException("Unknown fitting method: " + method);
    }
}
