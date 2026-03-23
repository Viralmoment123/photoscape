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
    // ===================== SECTIONS =====================

    @ConfigSection(
        name = "Controls",
        description = "Keybinds and camera control settings",
        position = 0
    )
    String controlsSection = "controls";

    @ConfigSection(
        name = "Camera",
        description = "Camera behavior settings",
        position = 1
    )
    String cameraSection = "camera";

    @ConfigSection(
        name = "Overlay",
        description = "Viewfinder and composition overlay settings",
        position = 2
    )
    String overlaySection = "overlay";

    @ConfigSection(
        name = "Screenshot",
        description = "Screenshot capture and output settings",
        position = 3
    )
    String screenshotSection = "screenshot";

    @ConfigSection(
        name = "Filters",
        description = "Post-processing filters and effects",
        position = 4
    )
    String filtersSection = "filters";

    // ===================== CONTROLS =====================

    @ConfigItem(
        keyName = "toggleKey",
        name = "Toggle PhotoScape Mode",
        description = "Keybind to enable/disable PhotoScape mode",
        section = controlsSection,
        position = 0
    )
    default Keybind toggleKey()
    {
        return new Keybind(KeyEvent.VK_F12, 0);
    }

    @ConfigItem(
        keyName = "captureKey",
        name = "Capture Screenshot",
        description = "Keybind to take a screenshot while in PhotoScape mode",
        section = controlsSection,
        position = 1
    )
    default Keybind captureKey()
    {
        return new Keybind(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "cameraSpeed",
        name = "Camera Rotation Speed",
        description = "How fast the camera rotates with arrow keys (1-20)",
        section = controlsSection,
        position = 2
    )
    @Range(min = 1, max = 20)
    default int cameraSpeed()
    {
        return 8;
    }

    // ===================== CAMERA =====================

    @ConfigItem(
        keyName = "unlockPitch",
        name = "Unlock Camera Pitch",
        description = "Remove the default camera pitch limits for more vertical angles",
        section = cameraSection,
        position = 0
    )
    default boolean unlockPitch()
    {
        return true;
    }

    @ConfigItem(
        keyName = "maxPitch",
        name = "Max Pitch (Look Down)",
        description = "Maximum downward camera angle (default limit is 383, max ~512)",
        section = cameraSection,
        position = 1
    )
    @Range(min = 384, max = 512)
    default int maxPitch()
    {
        return 512;
    }

    @ConfigItem(
        keyName = "minPitch",
        name = "Min Pitch (Look Level)",
        description = "Minimum camera angle / most level view (default limit is 128, min ~64)",
        section = cameraSection,
        position = 2
    )
    @Range(min = 64, max = 128)
    default int minPitch()
    {
        return 64;
    }

    @ConfigItem(
        keyName = "smoothCamera",
        name = "Smooth Camera",
        description = "Apply smoothing/interpolation to camera movement",
        section = cameraSection,
        position = 3
    )
    default boolean smoothCamera()
    {
        return true;
    }

    @ConfigItem(
        keyName = "hideUI",
        name = "Hide Game UI Elements",
        description = "Hide chat box, minimap, and other UI in PhotoScape mode (affects screenshot only)",
        section = cameraSection,
        position = 4
    )
    default boolean hideUI()
    {
        return false;
    }

    // ===================== OVERLAY =====================

    @ConfigItem(
        keyName = "compositionGuide",
        name = "Composition Guide",
        description = "Which composition overlay to display in the viewfinder",
        section = overlaySection,
        position = 0
    )
    default CompositionGuide compositionGuide()
    {
        return CompositionGuide.ALL;
    }

    @ConfigItem(
        keyName = "letterboxOpacity",
        name = "Letterbox Opacity",
        description = "Opacity of the dark bars outside the 4:3 frame (0-255)",
        section = overlaySection,
        position = 1
    )
    @Range(min = 0, max = 255)
    default int letterboxOpacity()
    {
        return 180;
    }

    @ConfigItem(
        keyName = "guideLineOpacity",
        name = "Guide Line Opacity",
        description = "Opacity of composition guide lines (0-255)",
        section = overlaySection,
        position = 2
    )
    @Range(min = 10, max = 255)
    default int guideLineOpacity()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "showModeIndicator",
        name = "Show Mode Indicator",
        description = "Show a 'PhotoScape' label and controls hint on screen",
        section = overlaySection,
        position = 3
    )
    default boolean showModeIndicator()
    {
        return true;
    }

    // ===================== SCREENSHOT =====================

    @ConfigItem(
        keyName = "screenshotFolder",
        name = "Screenshot Folder",
        description = "Folder to save screenshots (blank = RuneLite screenshots folder)",
        section = screenshotSection,
        position = 0
    )
    default String screenshotFolder()
    {
        return "";
    }

    @ConfigItem(
        keyName = "screenshotNotify",
        name = "Show Notification",
        description = "Show a notification when a screenshot is captured",
        section = screenshotSection,
        position = 1
    )
    default boolean screenshotNotify()
    {
        return true;
    }

    @ConfigItem(
        keyName = "includeOverlay",
        name = "Include Composition Overlay",
        description = "Include composition guides in the saved screenshot",
        section = screenshotSection,
        position = 2
    )
    default boolean includeOverlay()
    {
        return false;
    }

    // ===================== FILTERS =====================

    @ConfigItem(
        keyName = "photoFilter",
        name = "Photo Filter",
        description = "Post-processing filter to apply to captured screenshots",
        section = filtersSection,
        position = 0
    )
    default PhotoFilter photoFilter()
    {
        return PhotoFilter.NONE;
    }

    @ConfigItem(
        keyName = "filterStrength",
        name = "Filter Strength",
        description = "Intensity of the selected filter effect (0-100%)",
        section = filtersSection,
        position = 1
    )
    @Range(min = 0, max = 100)
    default int filterStrength()
    {
        return 75;
    }
}
