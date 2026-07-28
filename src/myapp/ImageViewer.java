package myapp;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.util.List;

/**
 * @file ImageViewer.java
 * @brief Swing-based GUI application for viewing multi-series microscopy images using Bio-Formats.
 *
 * Provides controls for opening bio-imaging files, navigating series and focal planes/frames,
 * and applying dynamic image transformations such as brightness offset adjustment and zoom scaling.
 *
 * @author myapp
 */
public class ImageViewer extends JFrame {

    // =========================================================================
    // UI CONTROLS & DISPLAY COMPONENTS
    // =========================================================================

    /** Display container for rendering the active processed image frame. */
    private final JLabel pictureBox;

    /** Slider control for switching between image series/scenes. */
    private final JSlider seriesTrackbar;

    /** Slider control for navigating time frames or Z-stack focal planes. */
    private final JSlider frameTrackbar;

    /** Slider control for adjusting visual image scale (percentage). */
    private final JSlider zoomTrackbar;

    /** Slider control for adjusting pixel brightness offset. */
    private final JSlider brightnessTrackbar;

    /** Label displaying active series index and total count. */
    private final JLabel seriesLabel;

    /** Label displaying active frame index and total frame count. */
    private final JLabel frameLabel;

    /** Label displaying current zoom percentage value. */
    private final JLabel zoomLabel;

    /** Label displaying current brightness adjustment offset value. */
    private final JLabel brightnessLabel;

    /** Status bar label indicating current file metadata and application status. */
    private final JLabel statusLabel;

    // =========================================================================
    // READER & IMAGE STATE CACHE
    // =========================================================================

    /** Custom wrapper managing Bio-Formats file decoding and plane extraction. */
    private final BioFormatsImageReader imageReader;

    /** Unmodified, raw image frame cached in memory prior to applying zoom or brightness edits. */
    private BufferedImage currentRawFrame;

    /** Tracks the last opened file directory across file chooser dialog calls. */
    private File lastSelectedDirectory = null;

    /**
     * @brief Constructs and initializes the ImageViewer main window.
     *
     * Sets up UI layouts, instantiates image controls and sliders, connects event listeners,
     * and configures main window frame properties.
     */
    public ImageViewer() {
        super("Bio-Formats Image Viewer");

        imageReader = new BioFormatsImageReader();

        // 1. Picture Box
        pictureBox = new JLabel("No image loaded", SwingConstants.CENTER);
        JScrollPane scrollPane = new JScrollPane(pictureBox);

        // 2. Series Slider
        seriesLabel = new JLabel("Series (0/0):");
        seriesTrackbar = createSlider(0, 0, 0, 1, 1);
        seriesTrackbar.addChangeListener(e -> {
            if (!seriesTrackbar.getValueIsAdjusting() && seriesTrackbar.isEnabled()) {
                onSeriesChanged(seriesTrackbar.getValue());
            }
        });

        // 3. Frame Slider
        frameLabel = new JLabel("Frame (0/0):");
        frameTrackbar = createSlider(0, 0, 0, 10, 1);
        frameTrackbar.addChangeListener(e -> {
            if (!frameTrackbar.getValueIsAdjusting() && frameTrackbar.isEnabled()) {
                loadFrameData(frameTrackbar.getValue());
            }
        });

        // 4. Zoom Slider (25% to 400%, Default: 100%)
        zoomLabel = new JLabel("Zoom: 100%");
        zoomTrackbar = createSlider(25, 400, 100, 50, 25);
        zoomTrackbar.addChangeListener(e -> {
            zoomLabel.setText(String.format("Zoom: %d%%", zoomTrackbar.getValue()));
            if (!zoomTrackbar.getValueIsAdjusting() && currentRawFrame != null) {
                renderProcessedImage();
            }
        });

        // 5. Brightness Slider (-100 to +100, Default: 0)
        brightnessLabel = new JLabel("Brightness: 0");
        brightnessTrackbar = createSlider(-100, 100, 0, 50, 10);
        brightnessTrackbar.addChangeListener(e -> {
            brightnessLabel.setText(String.format("Brightness: %+d", brightnessTrackbar.getValue()));
            if (!brightnessTrackbar.getValueIsAdjusting() && currentRawFrame != null) {
                renderProcessedImage();
            }
        });

        // File Open Button
        JButton openButton = new JButton("Open Image File...");
        openButton.addActionListener(e -> openFileChooser());

        // Status Label
        statusLabel = new JLabel(" Ready");

        // Layout Assembly
        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonRow.add(openButton);

        controlsPanel.add(buttonRow);
        controlsPanel.add(createControlRow(seriesLabel, seriesTrackbar));
        controlsPanel.add(createControlRow(frameLabel, frameTrackbar));
        controlsPanel.add(createControlRow(zoomLabel, zoomTrackbar));
        controlsPanel.add(createControlRow(brightnessLabel, brightnessTrackbar));

        setLayout(new BorderLayout(5, 5));
        add(scrollPane, BorderLayout.CENTER);
        add(controlsPanel, BorderLayout.SOUTH);
        add(statusLabel, BorderLayout.NORTH);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);
    }

    // =========================================================================
    // UI BUILDER HELPERS
    // =========================================================================

    /**
     * @brief Helper factory method to construct standardized JSlider components.
     *
     * @param min Minimum slider value.
     * @param max Maximum slider value.
     * @param value Initial value setting.
     * @param majorTick Spacing interval between major ticks.
     * @param minorTick Spacing interval between minor ticks.
     * @return Configured horizontal {@link JSlider} instance.
     */
    private JSlider createSlider(int min, int max, int value, int majorTick, int minorTick) {
        JSlider slider = new JSlider(JSlider.HORIZONTAL, min, max, value);
        slider.setEnabled(false);
        slider.setMajorTickSpacing(majorTick);
        slider.setMinorTickSpacing(minorTick);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        return slider;
    }

    /**
     * @brief Creates a horizontal panel linking a label control to its corresponding slider.
     *
     * @param label Pre-configured descriptive label component.
     * @param slider Pre-configured slider control component.
     * @return Formatted {@link JPanel} row layout.
     */
    private JPanel createControlRow(JLabel label, JSlider slider) {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        label.setPreferredSize(new Dimension(110, 20));
        panel.add(label, BorderLayout.WEST);
        panel.add(slider, BorderLayout.CENTER);
        return panel;
    }

    // =========================================================================
    // EVENT HANDLERS & FILE I/O
    // =========================================================================

    /**
     * @brief Displays the file chooser dialog filtered to Bio-Formats compatible formats.
     *
     * Automatically loads file extension filters provided by the Bio-Formats reader
     * and restores the user's previously selected directory context.
     */
    private void openFileChooser() {
        JFileChooser fileChooser;

        // Restore last opened directory if it exists
        if (lastSelectedDirectory != null && lastSelectedDirectory.exists()) {
            fileChooser = new JFileChooser(lastSelectedDirectory);
        } else {
            fileChooser = new JFileChooser();
        }

        fileChooser.setDialogTitle("Select Bio-Formats Supported Image");

        List<String> extensions = imageReader.getSupportedFileTypes();
        if (!extensions.isEmpty()) {
            String[] extArray = extensions.toArray(new String[0]);
            fileChooser.setFileFilter(new FileNameExtensionFilter(
                    "Bio-Formats Images (" + extArray.length + " types)", extArray));
        }

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            
            // Save directory (or parent folder if user selected a file)
            if (selectedFile.isDirectory()) {
                lastSelectedDirectory = selectedFile;
            } else {
                lastSelectedDirectory = selectedFile.getParentFile();
            }

            openImageFile(selectedFile.getAbsolutePath());
        }
    }

    /**
     * @brief Opens the designated image file using Bio-Formats and initializes UI states.
     *
     * Configures series and frame trackbar ranges, enables relevant controls,
     * and triggers loading of the initial frame (series 0, frame 0).
     *
     * @param filePath Absolute path of the target image file.
     */
    private void openImageFile(String filePath) {
        try {
            imageReader.openImage(filePath, 0, true, true);

            int seriesCount = imageReader.getSeriesCount();
            seriesTrackbar.setMinimum(0);
            seriesTrackbar.setMaximum(Math.max(0, seriesCount - 1));
            seriesTrackbar.setValue(0);
            seriesTrackbar.setEnabled(seriesCount > 1);
            seriesLabel.setText(String.format("Series (1/%d):", seriesCount));

            updateFrameTrackbarLimits();
            enableImageControls(true);

            loadFrameData(0);

            statusLabel.setText(String.format(" File: %s | Series: %d", 
                    new File(filePath).getName(), seriesCount));

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to open image:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /**
     * @brief Event handler triggered when user changes the active series slider.
     *
     * Updates the underlying reader's active series, resets frame boundary limits,
     * and loads the first frame of the newly selected series.
     *
     * @param seriesIndex Zero-based series index selected by user.
     */
    private void onSeriesChanged(int seriesIndex) {
        try {
            imageReader.setSeries(seriesIndex);
            seriesLabel.setText(String.format("Series (%d/%d):", seriesIndex + 1, imageReader.getSeriesCount()));
            updateFrameTrackbarLimits();
            loadFrameData(0);
        } catch (Exception ex) {
            statusLabel.setText(" Error switching to series " + seriesIndex);
            ex.printStackTrace();
        }
    }

    /**
     * @brief Recalculates and updates min/max limits on the frame slider for the active series.
     */
    private void updateFrameTrackbarLimits() {
        int totalFrames = imageReader.getFrameCount();
        frameTrackbar.setValue(0);
        frameTrackbar.setMinimum(0);
        frameTrackbar.setMaximum(Math.max(0, totalFrames - 1));
        frameTrackbar.setEnabled(totalFrames > 1);
        frameLabel.setText(String.format("Frame (1/%d):", Math.max(1, totalFrames)));
    }

    /**
     * @brief Enables or disables zoom and brightness sliders based on image load status.
     *
     * @param enable {@code true} to enable controls; {@code false} to disable.
     */
    private void enableImageControls(boolean enable) {
        zoomTrackbar.setEnabled(enable);
        brightnessTrackbar.setEnabled(enable);
    }

    /**
     * @brief Loads raw frame pixel data into memory cache and initiates image rendering.
     *
     * Updates UI frame indicators and application status bar text upon successful extraction.
     *
     * @param frameIndex Zero-based index of the target plane/frame to retrieve.
     */
    private void loadFrameData(int frameIndex) {
        try {
            currentRawFrame = imageReader.getFrame(frameIndex);
            renderProcessedImage();

            int currentSeries = seriesTrackbar.getValue() + 1;
            int totalSeries = imageReader.getSeriesCount();
            int totalFrames = imageReader.getFrameCount();

            frameLabel.setText(String.format("Frame (%d/%d):", frameIndex + 1, totalFrames));
            statusLabel.setText(String.format(" Series %d/%d | Frame %d/%d loaded",
                    currentSeries, totalSeries, frameIndex + 1, totalFrames));
        } catch (Exception ex) {
            statusLabel.setText(" Error reading frame " + frameIndex);
            ex.printStackTrace();
        }
    }

    /**
     * @brief Applies active Zoom scaling & Brightness offset transformations onto cached raw frame.
     *
     * Performs a non-destructive transformation using {@link RescaleOp} to adjust pixel brightness
     * and smooth interpolation scaling for zoom, rendering the result inside {@link #pictureBox}.
     */
    private void renderProcessedImage() {
        if (currentRawFrame == null) return;

        // 1. Apply Brightness offset via RescaleOp
        float brightnessOffset = brightnessTrackbar.getValue();
        RescaleOp rescale = new RescaleOp(1.0f, brightnessOffset, null);
        
        // Create a copy to prevent mutating raw frame cache
        BufferedImage processedImg = new BufferedImage(
                currentRawFrame.getWidth(), currentRawFrame.getHeight(), currentRawFrame.getType() == 0 ? 
                BufferedImage.TYPE_INT_ARGB : currentRawFrame.getType());
        
        rescale.filter(currentRawFrame, processedImg);

        // 2. Apply Zoom Scaling
        int zoomPercent = zoomTrackbar.getValue();
        int newWidth = Math.max(1, (processedImg.getWidth() * zoomPercent) / 100);
        int newHeight = Math.max(1, (processedImg.getHeight() * zoomPercent) / 100);

        Image scaledImg = processedImg.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);

        // 3. Render to Picture Box
        pictureBox.setText("");
        pictureBox.setIcon(new ImageIcon(scaledImg));
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    /**
     * @brief Releases reader resources and closes application window frame.
     *
     * Overrides {@link JFrame#dispose()} to ensure {@link BioFormatsImageReader#close()}
     * is explicitly executed on window closing.
     */
    @Override
    public void dispose() {
        imageReader.close();
        super.dispose();
    }

}
