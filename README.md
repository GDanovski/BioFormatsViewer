# BioFormatsViewer

A lightweight, desktop-based Java GUI application for opening, exploring, and viewing multi-series life-sciences microscopy images, Z-stacks, time-lapses, and standard image formats using the **OME Bio-Formats** library and **Java Swing**.

---

## Key Features

- **Broad Format Support**: Opens over 160 proprietary and standard image formats including `.czi`, `.lif`, `.nd2`, `.ome.tif`, `.svs`, `.jpg`, `.png`, and more.
- **Multi-Series Navigation**: Dedicated slider trackbar to switch between multiple series, scenes, or positions contained within a single file.
- **Frame & Z-Stack Slider**: Navigate through time-series frames and Z-stack focal planes easily in real-time.
- **Dynamic Image Controls**:
  - **Zoom**: Scale view size from 25% to 400%.
  - **Brightness Adjustment**: Real-time pixel intensity offset adjustment (-100 to +100).
- **Intelligent File Chooser**: Remembers your previously opened directory across application sessions and automatically filters supported file extensions.
