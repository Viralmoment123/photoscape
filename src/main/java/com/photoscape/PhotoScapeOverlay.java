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
    private static final double ASPECT_4_3 = 4.0 / 3.0;

    private final Client client;
    private final PhotoScapeConfig config;
    private final PhotoScapePlugin plugin;

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
        return new Rectangle(cropRect);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        // Don't render if inactive
        if (!plugin.isPhotoScapeActive()) return null;

        // Don't render during screenshot capture — this gives us a clean frame
        if (plugin.isHidingForCapture()) return null;

        int canvasW = client.getCanvasWidth();
        int canvasH = client.getCanvasHeight();
        if (canvasW <= 0 || canvasH <= 0) return null;

        // Largest centered 4:3 rect
        double canvasAspect = (double) canvasW / canvasH;
        int frameW, frameH;

        if (canvasAspect > ASPECT_4_3)
        {
            frameH = canvasH;
            frameW = (int) (canvasH * ASPECT_4_3);
        }
        else
        {
            frameW = canvasW;
            frameH = (int) (canvasW / ASPECT_4_3);
        }

        int frameX = (canvasW - frameW) / 2;
        int frameY = (canvasH - frameH) / 2;

        cropRect = new Rectangle(frameX, frameY, frameW, frameH);

        Composite origComposite = g.getComposite();

        // ===== Letterbox =====
        int lbAlpha = config.letterboxOpacity();
        if (lbAlpha > 0)
        {
            g.setColor(new Color(0, 0, 0, lbAlpha));

            // Top
            if (frameY > 0)
                g.fillRect(0, 0, canvasW, frameY);
            // Bottom
            if (frameY + frameH < canvasH)
                g.fillRect(0, frameY + frameH, canvasW, canvasH - frameY - frameH);
            // Left
            if (frameX > 0)
                g.fillRect(0, frameY, frameX, frameH);
            // Right
            if (frameX + frameW < canvasW)
                g.fillRect(frameX + frameW, frameY, canvasW - frameX - frameW, frameH);
        }

        // ===== Frame border =====
        g.setColor(new Color(255, 255, 255, 120));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(frameX, frameY, frameW, frameH);

        // ===== Composition guides =====
        CompositionGuide guide = config.compositionGuide();
        int ga = config.guideLineOpacity();

        if (guide == CompositionGuide.RULE_OF_THIRDS || guide == CompositionGuide.ALL)
            drawThirds(g, frameX, frameY, frameW, frameH, ga);
        if (guide == CompositionGuide.CENTER_CROSSHAIR || guide == CompositionGuide.ALL)
            drawCrosshair(g, frameX, frameY, frameW, frameH, ga);

        // ===== HUD =====
        if (config.showModeIndicator())
            drawHUD(g, frameX, frameY, frameW, frameH);

        // ===== Flash =====
        if (plugin.isCaptureFlashActive())
        {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, plugin.getCaptureFlashAlpha()));
            g.setColor(Color.WHITE);
            g.fillRect(frameX, frameY, frameW, frameH);
        }

        g.setComposite(origComposite);
        return null;
    }

    // ---- Rule of thirds ----
    private void drawThirds(Graphics2D g, int x, int y, int w, int h, int alpha)
    {
        g.setColor(new Color(255, 255, 255, alpha));
        g.setStroke(new BasicStroke(1f));

        int x1 = x + w/3, x2 = x + 2*w/3;
        int y1 = y + h/3, y2 = y + 2*h/3;

        g.drawLine(x1, y, x1, y+h);
        g.drawLine(x2, y, x2, y+h);
        g.drawLine(x, y1, x+w, y1);
        g.drawLine(x, y2, x+w, y2);

        int dot = 6;
        g.setColor(new Color(255, 255, 255, Math.min(alpha+40, 255)));
        g.fillOval(x1-dot/2, y1-dot/2, dot, dot);
        g.fillOval(x2-dot/2, y1-dot/2, dot, dot);
        g.fillOval(x1-dot/2, y2-dot/2, dot, dot);
        g.fillOval(x2-dot/2, y2-dot/2, dot, dot);
    }

    // ---- Center crosshair ----
    private void drawCrosshair(Graphics2D g, int x, int y, int w, int h, int alpha)
    {
        int cx = x+w/2, cy = y+h/2, sz = Math.min(w, h)/30;
        g.setColor(new Color(255, 80, 80, alpha));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(cx-sz, cy, cx+sz, cy);
        g.drawLine(cx, cy-sz, cx, cy+sz);
        g.drawOval(cx-sz/2, cy-sz/2, sz, sz);
    }

    // ---- HUD ----
    private void drawHUD(Graphics2D g, int fx, int fy, int fw, int fh)
    {
        int pad = 6;

        g.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        String title = "\u25CF PHOTOSCAPE";
        int tx = fx+12, ty = fy+24;

        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(tx-pad, ty-fm.getAscent()-2, fm.stringWidth(title)+pad*2, fm.getHeight()+4, 8, 8);
        g.setColor(new Color(255, 60, 60));
        g.drawString("\u25CF", tx, ty);
        g.setColor(new Color(255, 255, 255, 220));
        g.drawString(" PHOTOSCAPE", tx + fm.charWidth('\u25CF'), ty);

        g.setFont(new Font("Arial", Font.PLAIN, 11));
        fm = g.getFontMetrics();
        String controls = "WASD: Move  |  Q/E: Up/Down  |  Arrows: Rotate  |  Middle Mouse: Rotate  |  "
            + plugin.getCaptureKeyText() + ": Capture  |  "
            + plugin.getToggleKeyText() + ": Exit";

        int cw = fm.stringWidth(controls);
        int cx = fx + (fw-cw)/2;
        int cy = fy + fh - 12;

        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(cx-pad, cy-fm.getAscent()-2, cw+pad*2, fm.getHeight()+4, 8, 8);
        g.setColor(new Color(200, 200, 200, 200));
        g.drawString(controls, cx, cy);

        PhotoFilter filter = config.photoFilter();
        if (filter != PhotoFilter.NONE)
        {
            g.setFont(new Font("Arial", Font.ITALIC, 11));
            fm = g.getFontMetrics();
            String ft = "Filter: " + filter;
            int ftw = fm.stringWidth(ft);
            int ftx = fx+fw-ftw-12, fty = fy+24;

            g.setColor(new Color(0, 0, 0, 150));
            g.fillRoundRect(ftx-pad, fty-fm.getAscent()-2, ftw+pad*2, fm.getHeight()+4, 8, 8);
            g.setColor(new Color(180, 180, 255, 200));
            g.drawString(ft, ftx, fty);
        }
    }
}
