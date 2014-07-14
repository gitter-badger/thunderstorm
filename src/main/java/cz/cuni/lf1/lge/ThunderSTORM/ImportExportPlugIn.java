package cz.cuni.lf1.lge.ThunderSTORM;

import cz.cuni.lf1.lge.ThunderSTORM.ImportExport.IImportExport;
import cz.cuni.lf1.lge.ThunderSTORM.results.IJResultsTable;
import cz.cuni.lf1.lge.ThunderSTORM.UI.GUI;
import cz.cuni.lf1.lge.ThunderSTORM.UI.MacroParser;
import cz.cuni.lf1.lge.ThunderSTORM.results.GenericTable;
import cz.cuni.lf1.lge.ThunderSTORM.results.IJGroundTruthTable;
import cz.cuni.lf1.lge.ThunderSTORM.util.GridBagHelper;
import cz.cuni.lf1.lge.ThunderSTORM.util.PluginCommands;
import cz.cuni.lf1.lge.thunderstorm.util.macroui.DialogStub;
import cz.cuni.lf1.lge.thunderstorm.util.macroui.ParameterKey;
import cz.cuni.lf1.lge.thunderstorm.util.macroui.ParameterTracker;
import cz.cuni.lf1.lge.thunderstorm.util.macroui.validators.IntegerValidatorFactory;
import cz.cuni.lf1.lge.thunderstorm.util.macroui.validators.StringValidatorFactory;
import ij.IJ;
import ij.WindowManager;
import ij.plugin.PlugIn;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

public class ImportExportPlugIn implements PlugIn {

    public static final String IMPORT = "import";
    public static final String EXPORT = "export";
    private List<IImportExport> modules = null;
    private String[] moduleNames = null;
    private String[] moduleExtensions = null;

    private String path = null;

    public ImportExportPlugIn() {
        super();
        this.path = null;
    }

    public ImportExportPlugIn(String path) {
        super();
        this.path = path;
    }

    @Override
    public void run(String command) {
        GUI.setLookAndFeel();
        //Valid command strings:
        //"import;results"
        //"export;results"
        //"import;ground-truth"
        //"export;ground-truth"
        String[] commands = command.split(";");
        if(commands.length != 2) {
            throw new IllegalArgumentException("Malformed argument for Import/Export plug-in!");
        }
        //
        try {
            //get table
            GenericTable table;
            boolean groundTruth = IJGroundTruthTable.IDENTIFIER.equals(commands[1]);
            if(groundTruth) {
                table = IJGroundTruthTable.getGroundTruthTable();
            } else {
                table = IJResultsTable.getResultsTable();
            }

            setupModules();

            if(EXPORT.equals(commands[0])) {
                runExport(table, groundTruth);
            } else if(IMPORT.equals(commands[0])) {
                runImport(table, groundTruth);
            }
        } catch(Exception ex) {
            IJ.handleException(ex);
        }
    }

    private void runImport(GenericTable table, boolean groundTruth) {
        ImportDialog dialog = new ImportDialog(IJ.getInstance(), groundTruth);
        if(MacroParser.isRanFromMacro()) {
            dialog.getParams().readMacroOptions();
        } else {
            if(dialog.showAndGetResult() != JOptionPane.OK_OPTION) {
                return;
            }
        }

        path = dialog.getFilePath();
        boolean resetFirst = !dialog.append.getValue();
        int startingFrame = dialog.startingFrame.getValue();
        IImportExport importer = getModuleByName(dialog.getFileFormat());
        table.forceHide();
        if(resetFirst) table.reset();
        if(groundTruth) {
            callImporter(importer, table, path, startingFrame);
        } else {    // IJResultsTable
            IJResultsTable ijrt = (IJResultsTable) table;
            try {
                ijrt.setAnalyzedImage(WindowManager.getImage(dialog.rawImageStack.getValue()));
            } catch(ArrayIndexOutOfBoundsException ex) {
                if(resetFirst) {
                    ijrt.setAnalyzedImage(null);
                }
            }
            callImporter(importer, table, path, startingFrame);
            AnalysisPlugIn.setDefaultColumnsWidth(ijrt);
            ijrt.setLivePreview(dialog.showPreview.getValue());
            ijrt.showPreview();
        }
        table.forceShow();
    }

    private void runExport(GenericTable table, boolean groundTruth) {
        String[] colNames = (String[]) table.getColumnNames().toArray(new String[0]);

        ExportDialog dialog = new ExportDialog(IJ.getInstance(), groundTruth, colNames);
        if(MacroParser.isRanFromMacro()) {
            dialog.getParams().readMacroOptions();
        } else {
            if(dialog.showAndGetResult() != JOptionPane.OK_OPTION) {
                return;
            }
        }

        List<String> columns = new ArrayList<String>();
        for(int i = 0; i < colNames.length; i++) {
            if(dialog.exportColumns[i].getValue()) {
                columns.add(colNames[i]);
            }
        }
        path = dialog.getFilePath();
        //save protocol
        if(!groundTruth && dialog.saveProtocol.getValue()) {
            IJResultsTable ijrt = (IJResultsTable) table;
            if(ijrt.getMeasurementProtocol() != null) {
                ijrt.getMeasurementProtocol().export(getProtocolFilePath(path));
            }
        }

        //export
        IImportExport exporter = getModuleByName(dialog.getFileFormat());
        callExporter(exporter, table, path, columns);
    }

    private void setupModules() {
        modules = ModuleLoader.getModules(IImportExport.class);
        moduleNames = new String[modules.size()];
        moduleExtensions = new String[modules.size()];
        for(int i = 0; i < moduleNames.length; i++) {
            moduleNames[i] = modules.get(i).getName();
            moduleExtensions[i] = modules.get(i).getSuffix();
        }
    }

    private String getProtocolFilePath(String fpath) {
        int dotpos = fpath.lastIndexOf('.');
        if(dotpos < 0) {
            return fpath + "-protocol.txt";
        } else {
            return fpath.substring(0, dotpos) + "-protocol.txt";
        }
    }

    private void callExporter(IImportExport exporter, GenericTable table, String fpath, List<String> columns) {
        IJ.showStatus("ThunderSTORM is exporting your results...");
        IJ.showProgress(0.0);
        try {
            exporter.exportToFile(fpath, table, columns);
            IJ.showStatus("ThunderSTORM has exported your results.");
        } catch(IOException ex) {
            IJ.showStatus("");
            IJ.showMessage("Exception", ex.getMessage());
        } catch(Exception ex) {
            IJ.showStatus("");
            IJ.handleException(ex);
        }
        IJ.showProgress(1.0);
    }

    private void callImporter(IImportExport importer, GenericTable table, String fpath, int startingFrame) {
        IJ.showStatus("ThunderSTORM is importing your file...");
        IJ.showProgress(0.0);
        try {
            table.setOriginalState();
            importer.importFromFile(fpath, table, startingFrame);
            table.convertAllColumnsToAnalogUnits();
            IJ.showStatus("ThunderSTORM has imported your file.");
        } catch(IOException ex) {
            IJ.showStatus("");
            IJ.showMessage("Exception", ex.getMessage());
        } catch(Exception ex) {
            IJ.showStatus("");
            IJ.handleException(ex);
        }
        IJ.showProgress(1.0);
    }

    public IImportExport getModuleByName(String name) {
        for(int i = 0; i < moduleNames.length; i++) {
            if(moduleNames[i].equals(name)) {
                return modules.get(i);
            }
        }
        throw new RuntimeException("No module found for name " + name + ".");
    }

    //---------------GUI-----------------------
    class ImportDialog extends DialogStub {

        ParameterKey.String resFileFormat;
        ParameterKey.String resFilePath;
        ParameterKey.String gtFileFormat;
        ParameterKey.String gtFilePath;
        ParameterKey.Integer startingFrame;
        ParameterKey.Boolean showPreview;
        ParameterKey.Boolean append;
        ParameterKey.String rawImageStack;

        private boolean groundTruth;

        public ImportDialog(Window owner, boolean groundTruthTable) {
            super(new ParameterTracker("thunderstorm.io"), owner, "Import" + (groundTruthTable ? " ground-truth" : ""));
            assert moduleNames != null && moduleNames.length > 0;
            if (groundTruthTable) {
                gtFileFormat = params.createStringField("gtFileFormat", StringValidatorFactory.isMember(moduleNames), moduleNames[0]);
                gtFilePath = params.createStringField("gtFilePath", StringValidatorFactory.fileExists(), "");
            } else {
                resFileFormat = params.createStringField("resFileFormat", StringValidatorFactory.isMember(moduleNames), moduleNames[0]);
                resFilePath = params.createStringField("resFilePath", StringValidatorFactory.fileExists(), "");
            }
            startingFrame = params.createIntField("startingFrame", IntegerValidatorFactory.positiveNonZero(), 1);
            append = params.createBooleanField("append", null, false);
            groundTruth = groundTruthTable;
            if(!groundTruth) {
                showPreview = params.createBooleanField("livePreview", null, true);
                rawImageStack = params.createStringField("rawImageStack", StringValidatorFactory.openImages(true), "");
            }
        }

        ParameterTracker getParams() {
            return params;
        }

        @Override
        protected void layoutComponents() {
            JTextField startingFrameTextField = new JTextField(20);

            JPanel cameraPanel = new JPanel(new BorderLayout());
            cameraPanel.setBorder(new TitledBorder("Camera"));
            JButton cameraSetup = new JButton("Camera setup");
            cameraSetup.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    MacroParser.runNestedWithRecording(PluginCommands.CAMERA_SETUP, null);
                }
            });
            cameraPanel.add(cameraSetup);
            add(cameraPanel, GridBagHelper.twoCols());

            JPanel filePanel = new JPanel(new GridBagLayout());
            filePanel.setBorder(new TitledBorder("Input File"));
            JComboBox<String> fileFormatCBox = new JComboBox<String>(moduleNames);
            JTextField filePathTextField = new JTextField(20);
            filePathTextField.getDocument().addDocumentListener(createDocListener(filePathTextField, fileFormatCBox));
            fileFormatCBox.addItemListener(createItemListener(filePathTextField, fileFormatCBox));
            JButton browseButton = createBrowseButton(filePathTextField, true, new FileNameExtensionFilter(createFilterString(moduleExtensions), moduleExtensions));
            if (groundTruth) {
                gtFileFormat.registerComponent(fileFormatCBox);
                gtFilePath.registerComponent(filePathTextField);
            } else {
                resFileFormat.registerComponent(fileFormatCBox);
                resFilePath.registerComponent(filePathTextField);
            }
            JPanel filePathPanel = new JPanel(new BorderLayout());
            filePathPanel.add(filePathTextField);
            filePathPanel.add(browseButton, BorderLayout.EAST);
            filePathPanel.setPreferredSize(startingFrameTextField.getPreferredSize());
            filePanel.add(new JLabel("File format:"), GridBagHelper.leftCol());
            filePanel.add(fileFormatCBox, GridBagHelper.rightCol());
            filePanel.add(new JLabel("File path:"), GridBagHelper.leftCol());
            filePanel.add(filePathPanel, GridBagHelper.rightCol());
            add(filePanel, GridBagHelper.twoCols());

            JPanel concatenationPanel = new JPanel(new GridBagLayout());
            concatenationPanel.setBorder(new TitledBorder("Results concatenation"));
            JCheckBox appendCheckBox = new JCheckBox();
            startingFrame.registerComponent(startingFrameTextField);
            append.registerComponent(appendCheckBox);
            concatenationPanel.add(new JLabel("Append to current table:"), GridBagHelper.leftCol());
            concatenationPanel.add(appendCheckBox, GridBagHelper.rightCol());
            concatenationPanel.add(new JLabel("Starting frame number:"), GridBagHelper.leftCol());
            concatenationPanel.add(startingFrameTextField, GridBagHelper.rightCol());
            add(concatenationPanel, GridBagHelper.twoCols());

            if(!groundTruth) {
                JCheckBox showPreviewCheckBox = new JCheckBox();
                JComboBox<String> rawImageComboBox = createOpenImagesComboBox(true);
                rawImageComboBox.setPreferredSize(startingFrameTextField.getPreferredSize());
                showPreview.registerComponent(showPreviewCheckBox);
                rawImageStack.registerComponent(rawImageComboBox);
                JPanel previewPanel = new JPanel(new GridBagLayout());
                previewPanel.setBorder(new TitledBorder("Visualization"));
                previewPanel.add(new JLabel("Live preview:"), GridBagHelper.leftCol());
                previewPanel.add(showPreviewCheckBox, GridBagHelper.rightCol());
                previewPanel.add(new JLabel("Raw image sequence for overlay:"), GridBagHelper.leftCol());
                previewPanel.add(rawImageComboBox, GridBagHelper.rightCol());
                add(previewPanel, GridBagHelper.twoCols());
            }

            add(Box.createVerticalStrut(10), GridBagHelper.twoCols());
            add(createButtonsPanel(), GridBagHelper.twoCols());
            params.loadPrefs();
            if(path != null && !path.isEmpty()) {
                if (groundTruth) {
                    gtFilePath.setValue(path);
                } else {
                    resFilePath.setValue(path);
                }
            }

            getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            pack();
            setLocationRelativeTo(null);
            setModal(true);
        }

        public String getFilePath() {
            return (groundTruth ? gtFilePath.getValue() : resFilePath.getValue());
        }

        public String getFileFormat() {
            return (groundTruth ? gtFileFormat.getValue() : resFileFormat.getValue());
        }
    }

    class ExportDialog extends DialogStub {

        ParameterKey.String gtFileFormat;
        ParameterKey.String gtFilePath;
        ParameterKey.String resFileFormat;
        ParameterKey.String resFilePath;
        ParameterKey.Boolean[] exportColumns;
        ParameterKey.Boolean saveProtocol;

        private boolean groundTruth;
        private String[] columnHeaders;

        public ExportDialog(Window owner, boolean groundTruth, String[] columnHeaders) {
            super(new ParameterTracker("thunderstorm.io"), owner, "Export" + (groundTruth ? " ground-truth" : ""));
            assert moduleNames != null && moduleNames.length > 0;
            this.columnHeaders = columnHeaders;
            this.groundTruth = groundTruth;
            if (groundTruth) {
                gtFileFormat = params.createStringField("gtFileFormat", StringValidatorFactory.isMember(moduleNames), moduleNames[0]);
                gtFilePath = params.createStringField("gtFilePath", null, "");
            } else {
                resFileFormat = params.createStringField("resFileFormat", StringValidatorFactory.isMember(moduleNames), moduleNames[0]);
                resFilePath = params.createStringField("resFilePath", null, "");
            }
            exportColumns = new ParameterKey.Boolean[columnHeaders.length];
            for(int i = 0; i < columnHeaders.length; i++) {
                exportColumns[i] = params.createBooleanField(columnHeaders[i], null, i != 0);
            }
            if(!groundTruth) {
                saveProtocol = params.createBooleanField("saveProtocol", null, true);
            }
        }

        ParameterTracker getParams() {
            return params;
        }

        @Override
        protected void layoutComponents() {
            JPanel filePanel = new JPanel(new GridBagLayout());
            filePanel.setBorder(new TitledBorder("Output File"));
            JComboBox<String> fileFormatCBox = new JComboBox<String>(moduleNames);
            JTextField filePathTextField = new JTextField(20);
            filePathTextField.getDocument().addDocumentListener(createDocListener(filePathTextField, fileFormatCBox));
            fileFormatCBox.addItemListener(createItemListener(filePathTextField, fileFormatCBox));
            JButton browseButton = createBrowseButton(filePathTextField, true, new FileNameExtensionFilter(createFilterString(moduleExtensions), moduleExtensions));
            if (groundTruth) {
                gtFileFormat.registerComponent(fileFormatCBox);
                gtFilePath.registerComponent(filePathTextField);
            } else {
                resFileFormat.registerComponent(fileFormatCBox);
                resFilePath.registerComponent(filePathTextField);
            }
            JPanel filePathPanel = new JPanel(new BorderLayout());
            filePathPanel.setPreferredSize(filePathTextField.getPreferredSize());
            filePathPanel.add(filePathTextField);
            filePathPanel.add(browseButton, BorderLayout.EAST);
            filePanel.add(new JLabel("File format:"), GridBagHelper.leftCol());
            filePanel.add(fileFormatCBox, GridBagHelper.rightCol());
            filePanel.add(new JLabel("File path:"), GridBagHelper.leftCol());
            filePanel.add(filePathPanel, GridBagHelper.rightCol());
            add(filePanel, GridBagHelper.twoCols());

            if(!groundTruth) {
                JPanel protocolPanel = new JPanel(new GridBagLayout());
                protocolPanel.setBorder(new TitledBorder("Protocol"));
                protocolPanel.add(Box.createHorizontalStrut(filePathTextField.getPreferredSize().width / 2), GridBagHelper.leftCol());
                protocolPanel.add(Box.createHorizontalStrut(filePathTextField.getPreferredSize().width), GridBagHelper.rightCol());
                JCheckBox saveProtocolCheckBox = new JCheckBox();
                saveProtocol.registerComponent(saveProtocolCheckBox);
                protocolPanel.add(new JLabel("Save measurement protocol:"), GridBagHelper.leftCol());
                protocolPanel.add(saveProtocolCheckBox, GridBagHelper.rightCol());
                add(protocolPanel, GridBagHelper.twoCols());
            }

            JPanel columnsPanel = new JPanel(new GridBagLayout());
            columnsPanel.setBorder(new TitledBorder("Columns to export"));
            columnsPanel.add(Box.createHorizontalStrut(0), GridBagHelper.leftCol());
            columnsPanel.add(Box.createHorizontalStrut(filePathTextField.getPreferredSize().width), GridBagHelper.rightCol());
            for(int i = 0; i < columnHeaders.length; i++) {
                columnsPanel.add(new JLabel(columnHeaders[i]), GridBagHelper.leftCol());
                JCheckBox colCheckBox = new JCheckBox();
                exportColumns[i].registerComponent(colCheckBox);
                columnsPanel.add(colCheckBox, GridBagHelper.rightCol());
            }
            add(columnsPanel, GridBagHelper.twoCols());

            add(Box.createVerticalStrut(10), GridBagHelper.twoCols());
            add(createButtonsPanel(), GridBagHelper.twoCols());
            params.loadPrefs();

            getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            pack();
            setLocationRelativeTo(null);
            setModal(true);
        }

        public String getFilePath() {
            return (groundTruth ? gtFilePath.getValue() : resFilePath.getValue());
        }

        public String getFileFormat() {
            return (groundTruth ? gtFileFormat.getValue() : resFileFormat.getValue());
        }
    }

    private String createFilterString(String[] moduleExtensions) {
        StringBuilder sb = new StringBuilder();
        sb.append("Known formats(");
        for(String ext : moduleExtensions) {
            sb.append(ext);
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(")");
        return sb.toString();
    }

    private ItemListener createItemListener(final JTextField filePathTextField, final JComboBox<String> fileFormatCBox) {
        return new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                final String fp = filePathTextField.getText();
                if(!fp.isEmpty()) {
                    if(fp.endsWith("\\") || fp.endsWith("/")) {
                        filePathTextField.setText(fp + "results." + moduleExtensions[fileFormatCBox.getSelectedIndex()]);
                    } else {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                int dotpos = fp.lastIndexOf('.');
                                if(dotpos > 0) {
                                    filePathTextField.setText(fp.substring(0, dotpos + 1) + moduleExtensions[fileFormatCBox.getSelectedIndex()]);
                                }
                            }
                        });
                    }
                }
            }
        };
    }

    private DocumentListener createDocListener(final JTextField filePathTextField, final JComboBox<String> fileFormatCBox) {
        return new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                handle();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                handle();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }

            private void handle() {
                String fname = new File(filePathTextField.getText()).getName().trim();
                if(fname.isEmpty()) {
                    return;
                }
                int dotpos = fname.lastIndexOf('.');
                if(dotpos >= 0) {
                    String type = fname.substring(dotpos + 1).trim();
                    for(int i = 0; i < moduleExtensions.length; i++) {
                        if(type.equals(moduleExtensions[i])) {
                            //found correct suffix, adjust type combobox and return
                            fileFormatCBox.setSelectedIndex(i);
                            return;
                        }
                    }
                } else {
                    //no suffix found
                    if(!filePathTextField.isFocusOwner()) {
                        //user is not writting text at the moment
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                String selectedTypeSuffix = moduleExtensions[fileFormatCBox.getSelectedIndex()];
                                filePathTextField.setText(filePathTextField.getText() + "." + selectedTypeSuffix);
                            }
                        });
                    }
                }
            }
        };
    }
}