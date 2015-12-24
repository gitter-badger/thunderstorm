package cz.cuni.lf1.lge.ThunderSTORM;

import cz.cuni.lf1.lge.ThunderSTORM.UI.AstigmatismCalibrationDialog;
import cz.cuni.lf1.lge.ThunderSTORM.calibration.*;
import cz.cuni.lf1.lge.ThunderSTORM.detectors.ui.IDetectorUI;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.ui.AstigmatismCalibrationEstimatorUI;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.ui.IEstimatorUI;
import cz.cuni.lf1.lge.ThunderSTORM.filters.ui.IFilterUI;
import cz.cuni.lf1.lge.ThunderSTORM.thresholding.Thresholder;
import cz.cuni.lf1.lge.ThunderSTORM.UI.GUI;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Plot;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.swing.JOptionPane;

import ij.gui.Roi;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

public class CylindricalLensCalibrationPlugin implements PlugIn {

    DefocusFunction defocusModel;
    IFilterUI selectedFilterUI;
    IDetectorUI selectedDetectorUI;
    AstigmatismCalibrationEstimatorUI calibrationEstimatorUI;
    String savePath;
    double stageStep;
    double zRangeLimit;//in nm
    ImagePlus imp;
    Roi roi;

    @Override
    public void run(String arg) {
        GUI.setLookAndFeel();
        //
        imp = IJ.getImage();
        if(imp == null) {
            IJ.error("No image open.");
            return;
        }
        if(imp.getImageStackSize() < 2) {
            IJ.error("Requires a stack.");
            return;
        }
        try {
            //load modules
            calibrationEstimatorUI = new AstigmatismCalibrationEstimatorUI();
            List<IFilterUI> filters = ModuleLoader.getUIModules(IFilterUI.class);
            List<IDetectorUI> detectors = ModuleLoader.getUIModules(IDetectorUI.class);
            List<IEstimatorUI> estimators = Arrays.asList(new IEstimatorUI[]{calibrationEstimatorUI}); // only one estimator can be used
            List<DefocusFunction> defocusFunctions = ModuleLoader.getUIModules(DefocusFunction.class);
            Thresholder.loadFilters(filters);

            // get user options
            try {
                GUI.setLookAndFeel();
            } catch(Exception e) {
                IJ.handleException(e);
            }
            AstigmatismCalibrationDialog dialog;
            dialog = new AstigmatismCalibrationDialog(imp, filters, detectors, estimators, defocusFunctions);
            if(dialog.showAndGetResult() != JOptionPane.OK_OPTION) {
                return;
            }
            selectedFilterUI = dialog.getActiveFilterUI();
            selectedDetectorUI = dialog.getActiveDetectorUI();
            savePath = dialog.getSavePath();
            stageStep = dialog.getStageStep();
            zRangeLimit = dialog.getZRangeLimit();
            defocusModel = dialog.getActiveDefocusFunction();

            roi = imp.getRoi() != null ? imp.getRoi() : new Roi(0, 0, imp.getWidth(), imp.getHeight());

            //perform the calibration
            final AstigmaticCalibrationProcess process = new AstigmaticCalibrationProcess(selectedFilterUI, selectedDetectorUI, calibrationEstimatorUI, defocusModel, stageStep, zRangeLimit, imp, roi);

            process.estimateAngle();
            IJ.log("angle = " + process.getAngle());

            try {
                process.fitQuadraticPolynomials();
                IJ.log("s1 = " + process.getPolynomS1Final().toString());
                IJ.log("s2 = " + process.getPolynomS2Final().toString());
            } catch(NoMoleculesFittedException ex) {
                //if no beads were succesfully fitted, draw localizations anyway
                process.drawOverlay();
                IJ.handleException(ex);
                return;
            }
            process.drawOverlay();
            drawSigmaPlots(process.getAllPolynomsS1(), process.getAllPolynomsS2(),
                    process.getPolynomS1Final(), process.getPolynomS2Final(),
                    process.getAllFrames(), process.getAllSigma1s(), process.getAllSigma2s());

            try {
                process.getCalibration(defocusModel).saveToFile(savePath);
            } catch(IOException ex) {
                showAnotherLocationDialog(ex, process);
            }
        } catch(Exception ex) {
            IJ.handleException(ex);
        }
    }

    private void showAnotherLocationDialog(IOException ex, final AstigmaticCalibrationProcess process) {
        final JDialog dialog = new JDialog(IJ.getInstance(), "Error");
        dialog.getContentPane().setLayout(new BorderLayout(0, 10));
        dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dialog.add(new JLabel("Could not save calibration file. " + ex.getMessage(), SwingConstants.CENTER));
        JPanel buttonsPane = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton ok = new JButton("OK");
        dialog.getRootPane().setDefaultButton(ok);
        ok.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        JButton newLocation = new JButton("Save to other path");
        newLocation.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser jfc = new JFileChooser(IJ.getDirectory("image"));
                jfc.showSaveDialog(null);
                File f = jfc.getSelectedFile();
                if(f != null) {
                    try {
                        process.getCalibration(defocusModel).saveToFile(f.getAbsolutePath());
                    } catch(IOException ex) {
                        showAnotherLocationDialog(ex, process);
                    }
                }
                dialog.dispose();
            }
        });
        buttonsPane.add(newLocation);
        buttonsPane.add(ok);
        dialog.getContentPane().add(buttonsPane, BorderLayout.SOUTH);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.pack();
        ok.requestFocusInWindow();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private void drawSigmaPlots(List<DefocusFunction> sigma1Quadratics, List<DefocusFunction> sigma2Quadratics,
            DefocusFunction sigma1param, DefocusFunction sigma2param,
            double[] allFrames, double[] allSigma1s, double[] allSigma2s) {

        Plot plot = new Plot("Sigma", "z [nm]", "sigma [px]", (float[]) null, (float[]) null);
        plot.setSize(1024, 768);
        //range
        plot.setLimits(-2*zRangeLimit, +2*zRangeLimit, 0, stageStep);
        double[] xVals = new double[(int)(2*zRangeLimit/stageStep) * 2 + 1];
        for(int val = -2*(int)zRangeLimit, i = 0; val <= +2*(int)zRangeLimit; val += stageStep, i++) {
            xVals[i] = val;
        }
        plot.draw();
        //add points
        plot.setColor(new Color(255, 200, 200));
        plot.addPoints(allFrames, allSigma1s, Plot.CROSS);
        plot.setColor(new Color(200, 200, 255));
        plot.addPoints(allFrames, allSigma2s, Plot.CROSS);

        //add polynomials
        for(int i = 0; i < sigma1Quadratics.size(); i++) {
            double[] sigma1Vals = new double[xVals.length];
            double[] sigma2Vals = new double[xVals.length];
            for(int j = 0; j < sigma1Vals.length; j++) {
                sigma1Vals[j] = sigma1Quadratics.get(i).value(xVals[j]);
                sigma2Vals[j] = sigma2Quadratics.get(i).value(xVals[j]);
            }
            plot.setColor(new Color(255, 230, 230));
            plot.addPoints(xVals, sigma1Vals, Plot.LINE);
            plot.setColor(new Color(230, 230, 255));
            plot.addPoints(xVals, sigma2Vals, Plot.LINE);
        }

        //add final fitted curves
        double[] sigma1ValsAll = new double[xVals.length];
        double[] sigma2ValsAll = new double[xVals.length];
        for(int j = 0; j < sigma1ValsAll.length; j++) {
            sigma1ValsAll[j] = sigma1param.value(xVals[j]);
            sigma2ValsAll[j] = sigma2param.value(xVals[j]);
        }
        plot.setColor(new Color(255, 0, 0));
        plot.addPoints(xVals, sigma1ValsAll, Plot.LINE);
        plot.setColor(new Color(0, 0, 255));
        plot.addPoints(xVals, sigma2ValsAll, Plot.LINE);

        //legend
        plot.setColor(Color.red);
        plot.addLabel(0.1, 0.8, "sigma1");
        plot.setColor(Color.blue);
        plot.addLabel(0.1, 0.9, "sigma2");
        plot.show();
    }

    private void showHistoImages(List<DefocusFunction> sigma1Quadratics, List<DefocusFunction> sigma2Quadratics) {
        FloatProcessor a1 = new FloatProcessor(1, sigma1Quadratics.size());
        FloatProcessor a2 = new FloatProcessor(1, sigma2Quadratics.size());
        FloatProcessor b1 = new FloatProcessor(1, sigma2Quadratics.size());
        FloatProcessor b2 = new FloatProcessor(1, sigma2Quadratics.size());
        FloatProcessor cdif = new FloatProcessor(1, sigma2Quadratics.size());

        for(int i = 0; i < sigma1Quadratics.size(); i++) {
            a1.setf(i, (float) sigma1Quadratics.get(i).getA());
            b1.setf(i, (float) sigma1Quadratics.get(i).getB());
            a2.setf(i, (float) sigma2Quadratics.get(i).getA());
            b2.setf(i, (float) sigma2Quadratics.get(i).getB());
            cdif.setf(i, (float) (sigma2Quadratics.get(i).getC() - sigma1Quadratics.get(i).getC()));
        }
        new ImagePlus("a1", a1).show();
        new ImagePlus("a2", a2).show();
        new ImagePlus("b1", b1).show();
        new ImagePlus("b2", b2).show();
        new ImagePlus("cdif", cdif).show();
    }

    private void dumpShiftedPoints(double[] allFrames, double[] allSigma1s, double[] allSigma2s) {
        try {
            FileWriter fw = new FileWriter("d:\\dump.txt");
            fw.append("allFrames:\n");
            fw.append(Arrays.toString(allFrames));
            fw.append("\nallSigma1:\n");
            fw.append(Arrays.toString(allSigma1s));
            fw.append("\nallSigma2:\n");
            fw.append(Arrays.toString(allSigma2s));
            fw.close();
        } catch(Exception ex) {
            IJ.handleException(ex);
        }
    }
}
