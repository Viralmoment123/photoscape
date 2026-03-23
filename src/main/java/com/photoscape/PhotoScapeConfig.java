package com.photoscape;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

@ConfigGroup("photoscape")
public interface PhotoScapeConfig extends Config
{
    @ConfigSection(name = "Controls", description = "Keybinds and camera speed", position = 0)
    String controlsSection = "controls";

    @ConfigSection(name = "Filters", description = "Post-processing filters and watermark", position = 1)
    String filtersSection = "filters";

    @ConfigSection(name = "Overlay", description = "Viewfinder settings", position = 2)
    String overlaySection = "overlay";

    @ConfigSection(name = "Screenshot", description = "Screenshot output", position = 3)
    String screenshotSection = "screenshot";

    // ===================== CONTROLS =====================

    @ConfigItem(keyName = "toggleKey", name = "Toggle PhotoScape", description = "Keybind to toggle PhotoScape mode",
        section = controlsSection, position = 0)
    default Keybind toggleKey()
    {
        return new Keybind(KeyEvent.VK_F12, 0);
    }

    @ConfigItem(keyName = "captureKey", name = "Capture Screenshot", description = "Take a screenshot",
        section = controlsSection, position = 1)
    default Keybind captureKey()
    {
        return new Keybind(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK);
    }

    @ConfigItem(keyName = "cameraSpeed", name = "Camera Move Speed", description = "Arrow key movement speed (1-64)",
        section = controlsSection, position = 2)
    @Range(min = 1, max = 64)
    default int cameraSpeed()
    {
        return 10;
    }

    @ConfigItem(keyName = "heightSpeed", name = "Vertical Speed", description = "Q/E up/down speed (1-20)",
        section = controlsSection, position = 3)
    @Range(min = 1, max = 20)
    default int heightSpeed()
    {
        return 1;
    }

    // ===================== FILTERS =====================

    @ConfigItem(keyName = "photoFilter", name = "Photo Filter", description = "Filter applied to screenshots",
        section = filtersSection, position = 0)
    default PhotoFilter photoFilter()
    {
        return PhotoFilter.NONE;
    }

    @ConfigItem(keyName = "filterStrength", name = "Filter Strength", description = "Filter intensity (0-100%)",
        section = filtersSection, position = 1)
    @Range(min = 0, max = 100)
    default int filterStrength()
    {
        return 75;
    }

    @ConfigItem(keyName = "hypercamWatermark", name = "Hypercam Watermark", description = "Overlay a watermark on saved screenshots",
        section = filtersSection, position = 2)
    default boolean hypercamWatermark()
    {
        return true;
    }

    // ===================== OVERLAY =====================

    @ConfigItem(keyName = "compositionGuide", name = "Composition Guide", description = "Overlay guide type",
        section = overlaySection, position = 0)
    default CompositionGuide compositionGuide()
    {
        return CompositionGuide.ALL;
    }

    @ConfigItem(keyName = "letterboxOpacity", name = "Letterbox Opacity", description = "Darkness outside 4:3 frame (0-255)",
        section = overlaySection, position = 1)
    @Range(min = 0, max = 255)
    default int letterboxOpacity()
    {
        return 180;
    }

    @ConfigItem(keyName = "guideLineOpacity", name = "Guide Line Opacity", description = "Composition line opacity (10-255)",
        section = overlaySection, position = 2)
    @Range(min = 10, max = 255)
    default int guideLineOpacity()
    {
        return 100;
    }

    @ConfigItem(keyName = "showModeIndicator", name = "Show Mode Indicator", description = "Show HUD label and controls",
        section = overlaySection, position = 3)
    default boolean showModeIndicator()
    {
        return true;
    }

    // ===================== SCREENSHOT =====================

    @ConfigItem(keyName = "screenshotFolder", name = "Screenshot Folder",
        description = "Save location. Default: .runelite/screenshots/photoscape in your user folder",
        section = screenshotSection, position = 0)
    default String screenshotFolder()
    {
        return System.getProperty("user.home") + java.io.File.separator + ".runelite"
            + java.io.File.separator + "screenshots" + java.io.File.separator + "photoscape";
    }

    @ConfigItem(keyName = "screenshotNotify", name = "Show Notification", description = "Notify on capture",
        section = screenshotSection, position = 1)
    default boolean screenshotNotify()
    {
        return true;
    }
}
