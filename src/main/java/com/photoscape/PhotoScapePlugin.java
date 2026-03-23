package com.photoscape;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

@Slf4j
@PluginDescriptor(
    name = "PhotoScape",
    description = "A cinematic photography mode with 4:3 viewfinder, composition guides, and screenshot capture with filters",
    tags = {"camera", "screenshot", "photography", "photo", "freecam", "cinematic"}
)
public class PhotoScapePlugin extends Plugin
{
    private static final int YAW_MAX = 2048;
    private static final int DEFAULT_PITCH_MIN = 128;
    private static final int DEFAULT_PITCH_MAX = 383;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private PhotoScapeConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private PhotoScapeOverlay overlay;

    @Inject
    private KeyManager keyManager;

    @Inject
    private DrawManager drawManager;

    @Getter
    private boolean photoScapeActive = false;

    // Camera control state
    private int targetYaw;
    private int targetPitch;
    private boolean rotatingLeft = false;
    private boolean rotatingRight = false;
    private boolean pitchingUp = false;
    private boolean pitchingDown = false;

    // Capture flash animation
    @Getter
    private boolean captureFlashActive = false;
    @Getter
    private float captureFlashAlpha = 0f;
    private long captureFlashStart = 0;
    private static final long FLASH_DURATION_MS = 300;

    // Screenshot pending flag
    private boolean screenshotPending = false;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    // ======== Hotkey Listeners ========

    private final HotkeyListener toggleListener = new HotkeyListener(() -> config.toggleKey())
    {
        @Override
        public void hotkeyPressed()
        {
            togglePhotoScapeMode();
        }
    };

    private final HotkeyListener captureListener = new HotkeyListener(() -> config.captureKey())
    {
        @Override
        public void hotkeyPressed()
        {
            if (photoScapeActive)
            {
                captureScreenshot();
            }
        }
    };

    // Camera control key listener for arrow keys
    private final KeyListener cameraKeyListener = new KeyListener()
    {
        @Override
        public void keyTyped(KeyEvent e)
        {
        }

        @Override
        public void keyPressed(KeyEvent e)
        {
            if (!photoScapeActive)
            {
                return;
            }

            switch (e.getKeyCode())
            {
                case KeyEvent.VK_LEFT:
                    rotatingLeft = true;
                    e.consume();
                    break;
                case KeyEvent.VK_RIGHT:
                    rotatingRight = true;
                    e.consume();
                    break;
                case KeyEvent.VK_UP:
                    pitchingUp = true;
                    e.consume();
                    break;
                case KeyEvent.VK_DOWN:
                    pitchingDown = true;
                    e.consume();
                    break;
            }
        }

        @Override
        public void keyReleased(KeyEvent e)
        {
            if (!photoScapeActive)
            {
                return;
            }

            switch (e.getKeyCode())
            {
                case KeyEvent.VK_LEFT:
                    rotatingLeft = false;
                    e.consume();
                    break;
                case KeyEvent.VK_RIGHT:
                    rotatingRight = false;
                    e.consume();
                    break;
                case KeyEvent.VK_UP:
                    pitchingUp = false;
                    e.consume();
                    break;
                case KeyEvent.VK_DOWN:
                    pitchingDown = false;
                    e.consume();
                    break;
            }
        }
    };

    // ======== Plugin Lifecycle ========

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        keyManager.registerKeyListener(toggleListener);
        keyManager.registerKeyListener(captureListener);
        keyManager.registerKeyListener(cameraKeyListener);
        log.info("PhotoScape plugin started");
    }

    @Override
    protected void shutDown()
    {
        if (photoScapeActive)
        {
            deactivatePhotoScapeMode();
        }
        overlayManager.remove(overlay);
        keyManager.unregisterKeyListener(toggleListener);
        keyManager.unregisterKeyListener(captureListener);
        keyManager.unregisterKeyListener(cameraKeyListener);
        log.info("PhotoScape plugin stopped");
    }

    @Provides
    PhotoScapeConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(PhotoScapeConfig.class);
    }

    // ======== Toggle Logic ========

    private void togglePhotoScapeMode()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        if (photoScapeActive)
        {
            deactivatePhotoScapeMode();
        }
        else
        {
            activatePhotoScapeMode();
        }
    }

    private void activatePhotoScapeMode()
    {
        photoScapeActive = true;
        targetYaw = client.getCameraYaw();
        targetPitch = client.getCameraPitch();
        log.info("PhotoScape mode activated");
    }

    private void deactivatePhotoScapeMode()
    {
        photoScapeActive = false;
        rotatingLeft = false;
        rotatingRight = false;
        pitchingUp = false;
        pitchingDown = false;
        captureFlashActive = false;
        log.info("PhotoScape mode deactivated");
    }

    // ======== Camera Tick ========

    @Subscribe
    public void onClientTick(ClientTick tick)
    {
        if (!photoScapeActive || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        int speed = config.cameraSpeed();
        int yawStep = speed * 8;
        int pitchStep = speed * 3;

        // Update target yaw
        if (rotatingLeft)
        {
            targetYaw = (targetYaw - yawStep + YAW_MAX) % YAW_MAX;
        }
        if (rotatingRight)
        {
            targetYaw = (targetYaw + yawStep) % YAW_MAX;
        }

        // Update target pitch with extended limits
        int minPitch = config.unlockPitch() ? config.minPitch() : DEFAULT_PITCH_MIN;
        int maxPitch = config.unlockPitch() ? config.maxPitch() : DEFAULT_PITCH_MAX;

        if (pitchingUp)
        {
            targetPitch = Math.max(minPitch, targetPitch - pitchStep);
        }
        if (pitchingDown)
        {
            targetPitch = Math.min(maxPitch, targetPitch + pitchStep);
        }

        // Clamp pitch
        targetPitch = Math.max(minPitch, Math.min(maxPitch, targetPitch));

        // Apply camera targets
        if (config.smoothCamera())
        {
            int currentYaw = client.getCameraYaw();
            int currentPitch = client.getCameraPitch();

            int yawDiff = targetYaw - currentYaw;
            if (yawDiff > YAW_MAX / 2) yawDiff -= YAW_MAX;
            if (yawDiff < -YAW_MAX / 2) yawDiff += YAW_MAX;

            int smoothYaw = (currentYaw + (int)(yawDiff * 0.3) + YAW_MAX) % YAW_MAX;
            int smoothPitch = currentPitch + (int)((targetPitch - currentPitch) * 0.3);

            client.setCameraYawTarget(smoothYaw);
            client.setCameraPitchTarget(smoothPitch);
        }
        else
        {
            client.setCameraYawTarget(targetYaw);
            client.setCameraPitchTarget(targetPitch);
        }

        // Update capture flash animation
        if (captureFlashActive)
        {
            long elapsed = System.currentTimeMillis() - captureFlashStart;
            if (elapsed >= FLASH_DURATION_MS)
            {
                captureFlashActive = false;
                captureFlashAlpha = 0f;
            }
            else
            {
                float progress = (float) elapsed / FLASH_DURATION_MS;
                if (progress < 0.15f)
                {
                    captureFlashAlpha = progress / 0.15f * 0.6f;
                }
                else
                {
                    captureFlashAlpha = 0.6f * (1f - (progress - 0.15f) / 0.85f);
                }
            }
        }
    }

    /**
     * Intercept the pitch clamping script to allow extended pitch range.
     */
    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        if (!photoScapeActive || !config.unlockPitch())
        {
            return;
        }

        if ("cameraPitchTarget".equals(event.getEventName()))
        {
            int[] intStack = client.getIntStack();
            int intStackSize = client.getIntStackSize();

            int pitchTarget = intStack[intStackSize - 1];
            int minPitch = config.minPitch();
            int maxPitch = config.maxPitch();

            pitchTarget = Math.max(minPitch, Math.min(maxPitch, pitchTarget));
            intStack[intStackSize - 1] = pitchTarget;
        }
    }

    // ======== Screenshot Capture ========

    private void captureScreenshot()
    {
        if (screenshotPending)
        {
            return;
        }

        screenshotPending = true;

        // Start capture flash
        captureFlashActive = true;
        captureFlashStart = System.currentTimeMillis();
        captureFlashAlpha = 0f;

        drawManager.requestNextFrameListener(image ->
        {
            screenshotPending = false;

            BufferedImage screenshot = toBufferedImage(image);
            if (screenshot == null)
            {
                log.error("Failed to capture screenshot: could not convert image");
                return;
            }

            // Crop to the 4:3 area
            Rectangle crop = overlay.getCropRect();
            if (crop.width <= 0 || crop.height <= 0
                || crop.x < 0 || crop.y < 0
                || crop.x + crop.width > screenshot.getWidth()
                || crop.y + crop.height > screenshot.getHeight())
            {
                log.error("Invalid crop rectangle: {}", crop);
                return;
            }

            BufferedImage cropped = screenshot.getSubimage(crop.x, crop.y, crop.width, crop.height);

            // Make a writable copy (getSubimage shares the raster)
            BufferedImage output = new BufferedImage(cropped.getWidth(), cropped.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = output.createGraphics();
            g.drawImage(cropped, 0, 0, null);
            g.dispose();

            // Apply filter
            output = applyFilter(output, config.photoFilter(), config.filterStrength());

            // Save to disk
            saveScreenshot(output);
        });
    }

    private BufferedImage toBufferedImage(Image image)
    {
        if (image instanceof BufferedImage)
        {
            return (BufferedImage) image;
        }

        BufferedImage buffered = new BufferedImage(
            image.getWidth(null),
            image.getHeight(null),
            BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g = buffered.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return buffered;
    }

    private void saveScreenshot(BufferedImage image)
    {
        File folder;
        String customPath = config.screenshotFolder();
        if (customPath != null && !customPath.trim().isEmpty())
        {
            folder = new File(customPath.trim());
        }
        else
        {
            String runeliteDir = System.getProperty("user.home") + File.separator + ".runelite";
            folder = new File(runeliteDir, "screenshots" + File.separator + "photoscape");
        }

        if (!folder.exists() && !folder.mkdirs())
        {
            log.error("Failed to create screenshot folder: {}", folder.getAbsolutePath());
            return;
        }

        String timestamp = dateFormat.format(new Date());
        PhotoFilter filter = config.photoFilter();
        String filterSuffix = (filter != PhotoFilter.NONE) ? "_" + filter.name().toLowerCase() : "";
        String filename = "photoscape_" + timestamp + filterSuffix + ".png";

        File outputFile = new File(folder, filename);

        try
        {
            ImageIO.write(image, "png", outputFile);
            log.info("Screenshot saved: {}", outputFile.getAbsolutePath());
        }
        catch (IOException e)
        {
            log.error("Failed to save screenshot", e);
        }
    }

    // ======== Photo Filters ========

    private BufferedImage applyFilter(BufferedImage image, PhotoFilter filter, int strengthPercent)
    {
        if (filter == PhotoFilter.NONE)
        {
            return image;
        }

        float strength = strengthPercent / 100f;

        switch (filter)
        {
            case SEPIA:
                return applySepia(image, strength);
            case BLACK_AND_WHITE:
                return applyBlackAndWhite(image, strength);
            case VIGNETTE:
                return applyVignette(image, strength);
            case FILM_GRAIN:
                return applyFilmGrain(image, strength);
            case VINTAGE:
                image = applySepia(image, strength * 0.7f);
                image = applyVignette(image, strength * 0.8f);
                image = applyFilmGrain(image, strength * 0.4f);
                return image;
            default:
                return image;
        }
    }

    private BufferedImage applySepia(BufferedImage image, float strength)
    {
        int w = image.getWidth();
        int h = image.getHeight();

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int sepiaR = Math.min(255, (int) (r * 0.393 + g * 0.769 + b * 0.189));
                int sepiaG = Math.min(255, (int) (r * 0.349 + g * 0.686 + b * 0.168));
                int sepiaB = Math.min(255, (int) (r * 0.272 + g * 0.534 + b * 0.131));

                int finalR = (int) (r + (sepiaR - r) * strength);
                int finalG = (int) (g + (sepiaG - g) * strength);
                int finalB = (int) (b + (sepiaB - b) * strength);

                image.setRGB(x, y, (finalR << 16) | (finalG << 8) | finalB);
            }
        }
        return image;
    }

    private BufferedImage applyBlackAndWhite(BufferedImage image, float strength)
    {
        int w = image.getWidth();
        int h = image.getHeight();

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int gray = (int) (r * 0.299 + g * 0.587 + b * 0.114);

                int finalR = (int) (r + (gray - r) * strength);
                int finalG = (int) (g + (gray - g) * strength);
                int finalB = (int) (b + (gray - b) * strength);

                image.setRGB(x, y, (finalR << 16) | (finalG << 8) | finalB);
            }
        }
        return image;
    }

    private BufferedImage applyVignette(BufferedImage image, float strength)
    {
        int w = image.getWidth();
        int h = image.getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                float dx = (x - cx) / cx;
                float dy = (y - cy) / cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                float vignette = 1f - (dist * dist * strength * 0.5f);
                vignette = Math.max(0f, Math.min(1f, vignette));

                int rgb = image.getRGB(x, y);
                int r = (int) (((rgb >> 16) & 0xFF) * vignette);
                int g = (int) (((rgb >> 8) & 0xFF) * vignette);
                int b = (int) ((rgb & 0xFF) * vignette);

                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    private BufferedImage applyFilmGrain(BufferedImage image, float strength)
    {
        int w = image.getWidth();
        int h = image.getHeight();
        Random random = new Random();
        int grainAmount = (int) (40 * strength);

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int noise = random.nextInt(grainAmount * 2 + 1) - grainAmount;

                int rgb = image.getRGB(x, y);
                int r = Math.max(0, Math.min(255, ((rgb >> 16) & 0xFF) + noise));
                int g = Math.max(0, Math.min(255, ((rgb >> 8) & 0xFF) + noise));
                int b = Math.max(0, Math.min(255, (rgb & 0xFF) + noise));

                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    // ======== Utility accessors for the overlay ========

    public String getToggleKeyText()
    {
        return config.toggleKey().toString();
    }

    public String getCaptureKeyText()
    {
        return config.captureKey().toString();
    }
}
