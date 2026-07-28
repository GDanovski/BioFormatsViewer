package myapp;

import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.gui.AWTImageTools;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.in.MetadataOptions;

import loci.formats.gui.BufferedImageReader;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @file BioFormatsImageReader.java
 * @brief Wrapper class for reading multi-series and multi-frame microscopy files using Bio-Formats.
 *
 * Provides high-level methods to load bio-imaging formats, navigate series/scenes, 
 * and extract image frames as standard Java {@link BufferedImage} instances.
 * Implements {@link AutoCloseable} for safe memory cleanup.
 *
 * @author myapp
 */
public class BioFormatsImageReader implements AutoCloseable {

    /** Underlying Bio-Formats reader instance. */
    private final ImageReader reader;

    /** Path to the currently loaded image file. */
    private String currentFilePath;

    /** Flag tracking whether an image dataset is actively loaded in the reader. */
    private boolean isFileLoaded;

    /**
     * @brief Constructs a new BioFormatsImageReader instance.
     * 
     * Initializes the internal {@link ImageReader} and sets the initial file status to unloaded.
     */
    public BioFormatsImageReader() {
        this.reader = new ImageReader();
        this.isFileLoaded = false;
    }

    // =========================================================================
    // 1. OPEN IMAGE METHODS (Basic & Parameterized Options)
    // =========================================================================

    /**
     * @brief Opens an image file using default reader settings.
     * 
     * Sets series index to 0, enables automatic file grouping, and disables metadata filtering.
     *
     * @param filePath Absolute or relative path to the image file (.czi, .lif, .nd2, .tif, etc.).
     * @throws IOException If an I/O error occurs while reading the file.
     * @throws FormatException If the file format is unsupported or corrupt.
     */
    public void openImage(String filePath) throws IOException, FormatException {
        openImage(filePath, 0, true, false);
    }

    /**
     * @brief Overloaded method to open an image dataset with custom options.
     *
     * Closes any previously opened dataset before loading the new file.
     *
     * @param filePath Absolute or relative path to the image file (.czi, .lif, .nd2, .tif, etc.).
     * @param seriesIndex Target series/scene index to select after opening (default: 0).
     * @param groupFiles Set to {@code true} to group multi-file datasets automatically; {@code false} otherwise.
     * @param filterMetadata Set to {@code true} to filter out large/unprintable metadata tags; {@code false} to retain all.
     * @throws IOException If an I/O error occurs while reading the file.
     * @throws FormatException If the file format is unsupported or corrupt.
     */
    public void openImage(String filePath, int seriesIndex, boolean groupFiles, boolean filterMetadata) 
            throws IOException, FormatException {
        
        if (isFileLoaded) {
            close();
        }

        this.currentFilePath = filePath;

        // Configure Bio-Formats Reader Options BEFORE calling setId()
        this.reader.setGroupFiles(groupFiles);
        this.reader.setMetadataFiltered(filterMetadata);

        // Open the file dataset
        this.reader.setId(filePath);
        this.isFileLoaded = true;

        // Set the active series index after loading metadata
        setSeries(seriesIndex);
    }

    // =========================================================================
    // 2. SERIES & RESOLUTION MANAGEMENT
    // =========================================================================

    /**
     * @brief Switches the active series (scene/channel stack) in a multi-series dataset.
     *
     * @param seriesIndex Zero-based index of the target series.
     * @throws IllegalStateException If no image file has been loaded.
     * @throws IndexOutOfBoundsException If {@code seriesIndex} is less than 0 or greater/equal to total series count.
     */
    public void setSeries(int seriesIndex) {
        checkFileLoaded();
        if (seriesIndex < 0 || seriesIndex >= reader.getSeriesCount()) {
            throw new IndexOutOfBoundsException("Series index " + seriesIndex + " is out of bounds.");
        }
        reader.setSeries(seriesIndex);
    }

    /**
     * @brief Gets the total number of distinct series/scenes available in the loaded dataset.
     *
     * @return Number of available series in the dataset.
     * @throws IllegalStateException If no image file has been loaded.
     */
    public int getSeriesCount() {
        checkFileLoaded();
        return reader.getSeriesCount();
    }

    // =========================================================================
    // 3. FRAME COUNTS & PICTUREBOX EXTRACTION
    // =========================================================================

    /**
     * @brief Gets the total frame/plane count for the currently active series.
     *
     * @return Number of frames/planes in the active series, or {@code 0} if no file is loaded.
     */
    public int getFrameCount() {
        if (!isFileLoaded) {
            return 0;
        }
        return reader.getImageCount();
    }

    /**
     * @brief Reads a specific frame/plane and converts it to a {@link BufferedImage}.
     *
     * Suitable for rendering directly into Swing UI components like a PictureBox or {@code JLabel}.
     *
     * @param frameIndex Zero-based index of the frame/plane to retrieve.
     * @return The requested frame as a {@link BufferedImage}.
     * @throws IllegalStateException If no image file has been loaded.
     * @throws IndexOutOfBoundsException If {@code frameIndex} is out of valid bounds.
     * @throws IOException If an I/O error occurs during frame extraction.
     * @throws FormatException If frame decoding fails due to format errors.
     */
    public BufferedImage getFrame(int frameIndex) throws IOException, FormatException {
        checkFileLoaded();
        if (frameIndex < 0 || frameIndex >= getFrameCount()) {
            throw new IndexOutOfBoundsException("Frame index " + frameIndex + " is out of bounds.");
        }

        // Wrap the standard reader in a BufferedImageReader
        BufferedImageReader biReader = BufferedImageReader.makeBufferedImageReader(reader);
        
        // Obtains the requested frame as a java.awt.image.BufferedImage
        return biReader.openImage(frameIndex);
    }

    // =========================================================================
    // 4. FORMAT SUPPORT LIST
    // =========================================================================

    /**
     * @brief Retrieves all valid file extension suffixes supported by Bio-Formats.
     * 
     * Iterates through all available format readers and aggregates their extensions,
     * filtering out empty strings and {@code null} values to prevent UI component crashes.
     *
     * @return A {@link List} of valid extension strings (e.g., "czi", "lif", "tif").
     */
    public List<String> getSupportedFileTypes() {
        List<String> extensions = new ArrayList<>();
        IFormatReader[] readers = reader.getReaders();

        for (IFormatReader formatReader : readers) {
            String[] suffixes = formatReader.getSuffixes();
            if (suffixes != null) {
                for (String suffix : suffixes) {
                    // Only add non-null, non-empty extensions
                    if (suffix != null && !suffix.trim().isEmpty()) {
                        extensions.add(suffix.trim());
                    }
                }
            }
        }
        return extensions;
    }

    // =========================================================================
    // HELPER & CLEANUP METHODS
    // =========================================================================

    /**
     * @brief Gets the path of the currently loaded image file.
     *
     * @return The current file path string, or {@code null} if no file is currently loaded.
     */
    public String getCurrentFilePath() {
        return currentFilePath;
    }

    /**
     * @brief Internal helper method to verify that a file is loaded before operation execution.
     *
     * @throws IllegalStateException If {@code isFileLoaded} is {@code false}.
     */
    private void checkFileLoaded() {
        if (!isFileLoaded) {
            throw new IllegalStateException("No image loaded. Call openImage() first.");
        }
    }

    /**
     * @brief Closes the active image dataset and releases associated reader resources.
     *
     * Satisfies the {@link AutoCloseable} interface, enabling usage inside try-with-resources blocks.
     */
    @Override
    public void close() {
        if (isFileLoaded) {
            try {
                reader.close();
            } catch (IOException e) {
                System.err.println("Error closing reader: " + e.getMessage());
            } finally {
                isFileLoaded = false;
                currentFilePath = null;
            }
        }
    }
}
