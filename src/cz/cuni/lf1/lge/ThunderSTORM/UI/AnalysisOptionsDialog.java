package cz.cuni.lf1.lge.ThunderSTORM.UI;

import cz.cuni.lf1.lge.ThunderSTORM.IModule;
import cz.cuni.lf1.lge.ThunderSTORM.detectors.IDetector;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.IEstimator;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.PSF;
import cz.cuni.lf1.lge.ThunderSTORM.filters.IFilter;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import java.awt.Color;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;
import java.util.concurrent.Semaphore;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSeparator;
 
public class AnalysisOptionsDialog extends JDialog implements ActionListener {

    private CardsPanel filters, detectors, estimators;
    private JButton preview, ok, cancel;
    private ImagePlus imp;
    private boolean canceled;
    private Semaphore semaphore;    // ensures waiting for a dialog without the dialog being modal!
    private IFilter activeFilter;
    private IDetector activeDetector;
    private IEstimator activeEstimator;
    
    public AnalysisOptionsDialog(ImagePlus imp, String command, Vector<IModule> filters, int default_filter, Vector<IModule> detectors, int default_detector, Vector<IModule> estimators, int default_estimator) {
        super((JFrame)null, command);
        //
        this.canceled = false;
        //
        this.imp = imp;
        //
        this.filters = new CardsPanel(filters);
        this.detectors = new CardsPanel(detectors);
        this.estimators = new CardsPanel(estimators);
        //
        this.filters.setDefaultComboBoxItem(default_filter);
        this.detectors.setDefaultComboBoxItem(default_detector);
        this.estimators.setDefaultComboBoxItem(default_estimator);
        //
        this.preview = new JButton("Preview");
        this.ok = new JButton("Ok");
        this.cancel = new JButton("Cancel");
        //
        this.semaphore = new Semaphore(0);
        //
        // Outputs from this dialog
        this.activeFilter = null;
        this.activeDetector = null;
        this.activeEstimator = null;
    }
     
    public void addComponentsToPane() {
        Container pane = getContentPane();
        //
        pane.setLayout(new GridLayout(7,1));
        pane.add(filters.getPanel("Filters: "));
        pane.add(new JSeparator(JSeparator.HORIZONTAL));
        pane.add(detectors.getPanel("Detectors: "));
        pane.add(new JSeparator(JSeparator.HORIZONTAL));
        pane.add(estimators.getPanel("Estimators: "));
        pane.add(new JSeparator(JSeparator.HORIZONTAL));
        //
        preview.addActionListener(this);
        ok.addActionListener(this);
        cancel.addActionListener(this);
        //
        JPanel buttons = new JPanel();
        buttons.add(preview);
        buttons.add(Box.createHorizontalStrut(30));
        buttons.add(ok);
        buttons.add(cancel);
        pane.add(buttons);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        if(e.getActionCommand().equals("Cancel")) {
            dispose(true);
        } else if(e.getActionCommand().equals("Ok")) {
            activeFilter = (IFilter)filters.getActiveComboBoxItem();
            activeDetector = (IDetector)detectors.getActiveComboBoxItem();
            activeEstimator = (IEstimator)estimators.getActiveComboBoxItem();
            //
            ((IModule)activeFilter).readParameters();
            ((IModule)activeDetector).readParameters();
            ((IModule)activeEstimator).readParameters();
            //
            dispose(false);
        } else if(e.getActionCommand().equals("Preview")) {
            activeFilter = (IFilter)filters.getActiveComboBoxItem();
            activeDetector = (IDetector)detectors.getActiveComboBoxItem();
            activeEstimator = (IEstimator)estimators.getActiveComboBoxItem();
            //
            ((IModule)activeFilter).readParameters();
            ((IModule)activeDetector).readParameters();
            ((IModule)activeEstimator).readParameters();
            //
            FloatProcessor fp = (FloatProcessor)imp.getProcessor().convertToFloat();
            Vector<PSF> results = activeEstimator.estimateParameters(fp, activeDetector.detectMoleculeCandidates(activeFilter.filterImage(fp)));
            //
            double [] xCoord = new double[results.size()];
            double [] yCoord = new double[results.size()];
            for(int i = 0; i < results.size(); i++) {
                xCoord[i] = results.elementAt(i).xpos;
                yCoord[i] = results.elementAt(i).ypos;
            }
            //
            ImagePlus impPreview = new ImagePlus("ThunderSTORM preview for frame " + Integer.toString(imp.getSlice()), fp);
            RenderingOverlay.showPointsInImage(impPreview, xCoord, yCoord, Color.red, RenderingOverlay.MARKER_CROSS);
            impPreview.show();
        } else {
            throw new UnsupportedOperationException("Command '" + e.getActionCommand() + "' is not supported!");
        }
    }
    
    public void dispose(boolean cancel) {
        canceled = cancel;
        semaphore.release();
        dispose();
    }

    public boolean wasCanceled() {
        try {
            semaphore.acquire();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        return canceled;
    }
    
    public IFilter getFilter() {
        return activeFilter;
    }
    
    public IDetector getDetector() {
        return activeDetector;
    }
    
    public IEstimator getEstimator() {
        return activeEstimator;
    }

}