package myapp;

import javax.swing.SwingUtilities;

/**
 * @file Main.java
 * @brief Application entry point for launching the Bio-Formats Image Viewer.
 *
 * Handles thread-safe initialization of the Swing UI components on the 
 * Event Dispatch Thread (EDT).
 *
 * @author myapp
 */
public class Main {

    /**
     * @brief Main method serving as the application entry point.
     *
     * Schedules the construction and visualization of the {@link ImageViewer} window
     * on the Swing Event Dispatch Thread using {@link SwingUtilities#invokeLater(Runnable)}.
     *
     * @param args Command-line arguments passed to the application (unused).
     */
    public static void main(String[] args) {
        // Run UI on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            new ImageViewer().setVisible(true);
        });
    }
}
