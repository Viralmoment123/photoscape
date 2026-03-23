package com.photoscape;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;

public class PhotoScapeOverlay extends Overlay
{
    private static final double TARGET_RATIO = 4.0 / 3.0;
    private static final float GOLDEN_RATIO = 1.618033988749895f;

    private final Client client;
    private final PhotoScapeConfig config;
    private final PhotoScapePlugin plugin;

    // Cached crop rectangle (used by screenshot capture)
    private Rectangle cropRect = new Rectangle();

    @Inject
    public PhotoScapeOverlay(Client client, PhotoScapeConfig config, PhotoScapePlugin plugin)
    {
        this.client = client;
        this.config = config;
        this.plugin = plugin;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(OverlayPriority.HIGH);
    }

    public Rectangle getCropRect()
    {
        return cropRect;
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (!plugin.isPhotoScapeActive())
        {
            return null;
        }

        int viewWidth = client.getCanvasWidth();
        int viewHeight = client.getCanvasHeight();

        if (viewWidth <= 0 || viewHeight <= 0)
        {
            return null;
        }

        // Calculate the largest 4:3 rectangle centered in the viewport
        double currentRatio = (double) viewWidth / viewHeight;

        int cropWidth, cropHeight;
        if (currentRatio > TARGET_RATIO)
        {
            // Viewport is wider than 4:3 -- crop the sides
            cropHeight = viewHeight;
            cropWidth = (int) (viewHeight * TARGET_RATIO);
        }
        else
        {
            // Viewport is taller than 4:3 -- crop top/bottom
            cropWidth = viewWidth;
            cropHeight = (int) (viewWidth / TARGET_RATIO);
        }

        int cropX = (viewWidth - cropWidth) / 2;
        int cropY = (viewHeight - cropHeight) / 2;

        cropRect = new Rectangle(cropX, cropY, cropWidth, cropHeight);

        // Save original clip and composite
        Shape originalClip = g.getClip();
        Composite originalComposite = g.getComposite();

        // ===== Draw letterbox (dark bars outside 4:3 area) =====
        int alpha = config.letterboxOpacity();
        if (alpha > 0)
        {
            g.setColor(new Color(0, 0, 0, alpha));

            if (currentRatio > TARGET_RATIO)
            {
                // Dark bars on left and right
                g.fillRect(0, 0, cropX, viewHeight);
                g.fillRect(cropX + cropWidth, 0, viewWidth - cropX - cropWidth, viewHeight);
            }
            else
            {
                // Dark bars on top and bottom
                g.fillRect(0, 0, viewWidth, cropY);
                g.fillRect(0, cropY + cropHeight, viewWidth, viewHeight - cropY - cropHeight);
            }
        }

        // ===== Draw 4:3 frame border =====
        g.setColor(new Color(255, 255, 255, 120));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(cropX, cropY, cropWidth, cropHeight);

        // ===== Draw composition guides =====
        CompositionGuide guide = config.compositionGuide();
        int guideAlpha = config.guideLineOpacity();

        if (guide == CompositionGuide.RULE_OF_THIRDS || guide == CompositionGuide.ALL)
        {
            drawRuleOfThirds(g, cropX, cropY, cropWidth, cropHeight, guideAlpha);
        }

        if (guide == CompositionGuide.GOLDEN_RATIO || guide == CompositionGuide.ALL)
        {
            drawGoldenRatio(g, cropX, cropY, cropWidth, cropHeight, guideAlpha);
        }

        if (guide == CompositionGuide.CENTER_CROSSHAIR || guide == CompositionGuide.ALL)
        {
            drawCenterCrosshair(g, cropX, cropY, cropWidth, cropHeight, guideAlpha);
        }

        // ===== Draw mode indicator =====
        if (config.showModeIndicator())
        {
            drawModeIndicator(g, cropX, cropY, cropWidth, cropHeight);
        }

        // ===== Draw capture flash if active =====
        if (plugin.isCaptureFlashActive())
        {
            float flashAlpha = plugin.getCaptureFlashAlpha();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha));
            g.setColor(Color.WHITE);
            g.fillRect(cropX, cropY, cropWidth, cropHeight);
        }

        // Restore
        g.setClip(originalClip);
        g.setComposite(originalComposite);

        return null;
    }

    private void drawRuleOfThirds(Graphics2D g, int x, int y, int w, int h, int alpha)
    {
        g.setColor(new Color(255, 255, 255, alpha));
        g.setStroke(new BasicStroke(1f));

        // Vertical lines at 1/3 and 2/3
        int x1 = x + w / 3;
        int x2 = x + 2 * w / 3;
        g.drawLine(x1, y, x1, y + h);
        g.drawLine(x2, y, x2, y + h);

        // Horizontal lines at 1/3 and 2/3
        int y1 = y + h / 3;
        int y2 = y + 2 * h / 3;
        g.drawLine(x, y1, x + w, y1);
        g.drawLine(x, y2, x + w, y2);

        // Small dots at the 4 intersection "power points"
        int dotSize = 6;
        g.setColor(new Color(255, 255, 255, Math.min(alpha + 40, 255)));
        g.fillOval(x1 - dotSize / 2, y1 - dotSize / 2, dotSize, dotSize);
        g.fillOval(x2 - dotSize / 2, y1 - dotSize / 2, dotSize, dotSize);
        g.fillOval(x1 - dotSize / 2, y2 - dotSize / 2, dotSize, dotSize);
        g.fillOval(x2 - dotSize / 2, y2 - dotSize / 2, dotSize, dotSize);
    }

    private void drawGoldenRatio(Graphics2D g, int x, int y, int w, int h, int alpha)
    {
        g.setColor(new Color(255, 215, 0, alpha)); // Gold colored lines
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6, 4}, 0));

        // Golden ratio lines (phi divisions)
        float phiSmall = 1.0f / GOLDEN_RATIO;
        float phiLarge = 1.0f - phiSmall;

        int gx1 = x + (int) (w * phiSmall);
        int gx2 = x + (int) (w * phiLarge);
        g.drawLine(gx1, y, gx1, y + h);
        g.drawLine(gx2, y, gx2, y + h);

        int gy1 = y + (int) (h * phiSmall);
        int gy2 = y + (int) (h * phiLarge);
        g.drawLine(x, gy1, x + w, gy1);
        g.drawLine(x, gy2, x + w, gy2);

        // Draw a golden spiral approximation
        drawGoldenSpiral(g, x, y, w, h, alpha);
    }

    private void drawGoldenSpiral(Graphics2D g, int x, int y, int w, int h, int alpha)
    {
        g.setColor(new Color(255, 215, 0, (int) (alpha * 0.6)));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Approximate golden spiral using quarter-circle arcs in shrinking golden rectangles
        double rectX = x;
        double rectY = y;
        double rectW = w;
        double rectH = h;

        for (int i = 0; i < 8; i++)
        {
            double arcX, arcY, arcW, arcH;
            int startAngle;

            switch (i % 4)
            {
                case 0:
                    arcW = rectW * 2;
                    arcH = rectH * 2;
                    arcX = rectX + rectW - arcW;
                    arcY = rectY;
                    startAngle = 0;
                    double newH0 = rectH - rectW / GOLDEN_RATIO;
                    rectY = rectY + (rectH - newH0);
                    rectH = newH0;
                    break;
                case 1:
                    arcW = rectW * 2;
                    arcH = rectH * 2;
                    arcX = rectX;
                    arcY = rectY;
                    startAngle = 90;
                    double newW1 = rectW - rectH / GOLDEN_RATIO;
                    rectW = newW1;
                    break;
                case 2:
                    arcW = rectW * 2;
                    arcH = rectH * 2;
                    arcX = rectX;
                    arcY = rectY + rectH - arcH;
                    startAngle = 180;
                    double newH2 = rectH - rectW / GOLDEN_RATIO;
                    rectH = newH2;
                    break;
                default:
                    arcW = rectW * 2;
                    arcH = rectH * 2;
                    arcX = rectX + rectW - arcW;
                    arcY = rectY + rectH - arcH;
                    startAngle = 270;
                    double newW3 = rectW - rectH / GOLDEN_RATIO;
                    rectX = rectX + (rectW - newW3);
                    rectW = newW3;
                    break;
            }

            if (arcW > 2 && arcH > 2)
            {
                g.drawArc((int) arcX, (int) arcY, (int) arcW, (int) arcH, startAngle, 90);
            }
        }
    }

    private void drawCenterCrosshair(Graphics2D g, int x, int y, int w, int h, int alpha)
    {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int size = Math.min(w, h) / 30;

        g.setColor(new Color(255, 80, 80, alpha));
        g.setStroke(new BasicStroke(1.5f));

        // Crosshair lines
        g.drawLine(cx - size, cy, cx + size, cy);
        g.drawLine(cx, cy - size, cx, cy + size);

        // Small circle at center
        int circleR = size / 2;
        g.drawOval(cx - circleR, cy - circleR, circleR * 2, circleR * 2);
    }

    private void drawModeIndicator(Graphics2D g, int cropX, int cropY, int cropW, int cropH)
    {
        // "PHOTOSCAPE" label top-left inside the 4:3 frame
        g.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();

        String title = "\u25CF PHOTOSCAPE";
        int textX = cropX + 12;
        int textY = cropY + 24;

        // Background pill
        int padding = 6;
        int textW = fm.stringWidth(title);
        int textH = fm.getHeight();
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(textX - padding, textY - textH + 2, textW + padding * 2, textH + padding, 8, 8);

        // Red recording dot + white text
        g.setColor(new Color(255, 60, 60));
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString("\u25CF", textX, textY);
        g.setColor(new Color(255, 255, 255, 220));
        g.drawString(" PHOTOSCAPE", textX + fm.charWidth('\u25CF'), textY);

        // Controls hint at the bottom
        g.setFont(new Font("Arial", Font.PLAIN, 11));
        fm = g.getFontMetrics();
        String controls = "Arrows: Rotate  |  Scroll: Zoom  |  "
            + getKeyName(plugin.getCaptureKeyText()) + ": Capture  |  "
            + getKeyName(plugin.getToggleKeyText()) + ": Exit";

        int controlsW = fm.stringWidth(controls);
        int controlsX = cropX + (cropW - controlsW) / 2;
        int controlsY = cropY + cropH - 12;

        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(controlsX - padding, controlsY - fm.getAscent() - 2, controlsW + padding * 2, fm.getHeight() + 4, 8, 8);

        g.setColor(new Color(200, 200, 200, 200));
        g.drawString(controls, controlsX, controlsY);

        // Filter indicator if a filter is active
        PhotoFilter filter = config.photoFilter();
        if (filter != PhotoFilter.NONE)
        {
            g.setFont(new Font("Arial", Font.ITALIC, 11));
            fm = g.getFontMetrics();
            String filterText = "Filter: " + filter.toString();
            int filterW = fm.stringWidth(filterText);
            int filterX = cropX + cropW - filterW - 12;
            int filterY = cropY + 24;

            g.setColor(new Color(0, 0, 0, 150));
            g.fillRoundRect(filterX - padding, filterY - fm.getAscent() - 2, filterW + padding * 2, fm.getHeight() + 4, 8, 8);

            g.setColor(new Color(180, 180, 255, 200));
            g.drawString(filterText, filterX, filterY);
        }
    }

    private String getKeyName(String keyText)
    {
        return keyText != null ? keyText : "?";
    }
}
