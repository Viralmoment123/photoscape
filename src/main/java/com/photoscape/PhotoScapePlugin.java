package com.photoscape;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
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
    description = "Freecam photography mode with 4:3 viewfinder, composition guides, and screenshot capture with filters",
    tags = {"camera", "screenshot", "photography", "photo", "freecam", "cinematic"}
)
public class PhotoScapePlugin extends Plugin
{
    private static final double ASPECT_4_3 = 4.0 / 3.0;

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private PhotoScapeConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private PhotoScapeOverlay overlay;
    @Inject private KeyManager keyManager;
    @Inject private DrawManager drawManager;

    @Getter private boolean photoScapeActive = false;
    @Getter @Setter private boolean hidingForCapture = false;

    @Getter private boolean captureFlashActive = false;
    @Getter private float captureFlashAlpha = 0f;
    private long captureFlashStart = 0;
    private static final long FLASH_DURATION_MS = 300;

    // Widget hiding state (hide-widgets approach)
    private boolean widgetsHidden = false;

    // Capture state: 0=idle, 1=hide requested, 2=waiting for frame
    private int captureState = 0;

    private int savedOrbState;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    // Watermark image loaded from resources
    private BufferedImage watermarkImage;

    // WASD + Q/E movement
    private boolean moveUp = false;
    private boolean moveDown = false;

    // ======== Hotkeys ========

    private final HotkeyListener toggleListener = new HotkeyListener(() -> config.toggleKey())
    {
        @Override public void hotkeyPressed() { togglePhotoScape(); }
    };

    private final HotkeyListener captureListener = new HotkeyListener(() -> config.captureKey())
    {
        @Override public void hotkeyPressed()
        {
            if (photoScapeActive && captureState == 0)
            {
                captureFlashActive = true;
                captureFlashStart = System.currentTimeMillis();
                captureFlashAlpha = 0f;
                hidingForCapture = true;
                captureState = 1;
            }
        }
    };

    // Q/E for vertical movement. WASD is handled natively by the game's orb mode.
    private final KeyListener movementKeyListener = new KeyListener()
    {
        @Override public void keyTyped(KeyEvent e) {}
        @Override public void keyPressed(KeyEvent e)
        {
            if (!photoScapeActive) return;
            if (e.getKeyCode() == KeyEvent.VK_E) { moveUp = true; e.consume(); }
            if (e.getKeyCode() == KeyEvent.VK_Q) { moveDown = true; e.consume(); }
        }
        @Override public void keyReleased(KeyEvent e)
        {
            if (!photoScapeActive) return;
            if (e.getKeyCode() == KeyEvent.VK_E) { moveUp = false; e.consume(); }
            if (e.getKeyCode() == KeyEvent.VK_Q) { moveDown = false; e.consume(); }
        }
    };

    // ======== Lifecycle ========

    @Override protected void startUp()
    {
        overlayManager.add(overlay);
        keyManager.registerKeyListener(toggleListener);
        keyManager.registerKeyListener(captureListener);
        keyManager.registerKeyListener(movementKeyListener);

        // Load watermark from resources
        try
        {
            watermarkImage = ImageIO.read(getClass().getResourceAsStream("/watermark.png"));
        }
        catch (Exception e)
        {
            log.warn("Could not load watermark.png from resources", e);
            watermarkImage = null;
        }
    }

    @Override protected void shutDown()
    {
        if (photoScapeActive) deactivate();
        overlayManager.remove(overlay);
        keyManager.unregisterKeyListener(toggleListener);
        keyManager.unregisterKeyListener(captureListener);
        keyManager.unregisterKeyListener(movementKeyListener);
    }

    @Provides
    PhotoScapeConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(PhotoScapeConfig.class);
    }

    // ======== Toggle ========

    private void togglePhotoScape()
    {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (photoScapeActive) deactivate(); else activate();
    }

    private void activate()
    {
        photoScapeActive = true;
        savedOrbState = client.getOculusOrbState();
        // Unlock extended pitch range first
        client.setCameraPitchRelaxerEnabled(true);
        // Then enable orb mode
        client.setOculusOrbState(1);
        client.setOculusOrbNormalSpeed(config.cameraSpeed());
    }

    private void deactivate()
    {
        photoScapeActive = false;
        client.setOculusOrbState(0);
        client.setOculusOrbNormalSpeed(12);
        client.setCameraPitchRelaxerEnabled(false);
        if (widgetsHidden) showWidgets();
        moveUp = false;
        moveDown = false;
        captureFlashActive=false;
        hidingForCapture=false;
        captureState=0;
    }

    // ======== Widget hiding (hide-widgets plugin approach) ========

    /**
     * Hide all children of the resizable viewport parent widget.
     * Skips content type 1337 which is the game viewport itself.
     * Works for both modern and classic resizable modes.
     */
    private void hideWidgets()
    {
        if (!client.isResized())
        {
            log.warn("Widget hiding only works in resizable mode");
            return;
        }

        clientThread.invokeLater(() ->
        {
            // Modern resizable
            Widget modernMinimap = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP);
            if (modernMinimap != null)
            {
                Widget parent = modernMinimap.getParent();
                if (parent != null) hideWidgetChildren(parent, true);
            }

            // Classic resizable
            Widget classicMinimap = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP);
            if (classicMinimap != null)
            {
                Widget parent = classicMinimap.getParent();
                if (parent != null) hideWidgetChildren(parent, true);
            }

            widgetsHidden = true;
        });
    }

    private void showWidgets()
    {
        clientThread.invokeLater(() ->
        {
            Widget modernMinimap = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP);
            if (modernMinimap != null)
            {
                Widget parent = modernMinimap.getParent();
                if (parent != null) hideWidgetChildren(parent, false);
            }

            Widget classicMinimap = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP);
            if (classicMinimap != null)
            {
                Widget parent = classicMinimap.getParent();
                if (parent != null) hideWidgetChildren(parent, false);
            }

            widgetsHidden = false;
        });
    }

    private void hideWidgetChildren(Widget root, boolean hide)
    {
        Widget[] dynamic = root.getDynamicChildren();
        Widget[] nested = root.getNestedChildren();
        Widget[] staticC = root.getStaticChildren();

        Widget[] all = new Widget[dynamic.length + nested.length + staticC.length];
        System.arraycopy(dynamic, 0, all, 0, dynamic.length);
        System.arraycopy(nested, 0, all, dynamic.length, nested.length);
        System.arraycopy(staticC, 0, all, dynamic.length + nested.length, staticC.length);

        for (Widget w : all)
        {
            if (w != null && w.getContentType() != 1337)
            {
                w.setHidden(hide);
            }
        }
    }

    /**
     * The game re-shows widgets on TOPLEVEL_REDRAW and script 903.
     * If we're in capture mode with widgets hidden, re-hide them.
     */
    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (widgetsHidden && (event.getScriptId() == ScriptID.TOPLEVEL_REDRAW || event.getScriptId() == 903))
        {
            // Re-hide immediately
            Widget modernMinimap = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP);
            if (modernMinimap != null)
            {
                Widget parent = modernMinimap.getParent();
                if (parent != null) hideWidgetChildren(parent, true);
            }

            Widget classicMinimap = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP);
            if (classicMinimap != null)
            {
                Widget parent = classicMinimap.getParent();
                if (parent != null) hideWidgetChildren(parent, true);
            }
        }
    }

    // ======== Capture state machine ========

    @Subscribe
    public void onBeforeRender(BeforeRender event)
    {
        if (captureState == 1)
        {
            // Hide widgets right before render
            if (client.isResized())
            {
                // Inline hide (not invokeLater — we need it NOW before this frame renders)
                Widget modernMinimap = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP);
                if (modernMinimap != null)
                {
                    Widget parent = modernMinimap.getParent();
                    if (parent != null) hideWidgetChildren(parent, true);
                }
                Widget classicMinimap = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP);
                if (classicMinimap != null)
                {
                    Widget parent = classicMinimap.getParent();
                    if (parent != null) hideWidgetChildren(parent, true);
                }
                widgetsHidden = true;
            }

            // Register frame listener for this frame
            drawManager.requestNextFrameListener(image ->
            {
                // Restore widgets
                clientThread.invokeLater(() ->
                {
                    showWidgets();
                    hidingForCapture = false;
                    captureState = 0;
                });
                processScreenshot(image);
            });

            captureState = 2;
        }
    }

    // ======== Camera tick ========

    @Subscribe
    public void onClientTick(ClientTick tick)
    {
        if (!photoScapeActive || client.getGameState() != GameState.LOGGED_IN) return;
        if (captureState != 0) return;

        // Keep orb mode and speed active every tick
        client.setOculusOrbState(1);
        client.setOculusOrbNormalSpeed(config.cameraSpeed());

        // Q/E vertical movement
        if (moveUp || moveDown)
        {
            double vSpeed = config.heightSpeed() * 8.0;
            double dy = 0;
            if (moveUp) dy += vSpeed;
            if (moveDown) dy -= vSpeed;
            client.setCameraFocalPointY(client.getCameraFocalPointY() + dy);
        }

        // Flash animation
        if (captureFlashActive)
        {
            long el = System.currentTimeMillis() - captureFlashStart;
            if (el >= FLASH_DURATION_MS) { captureFlashActive=false; captureFlashAlpha=0f; }
            else { float p=(float)el/FLASH_DURATION_MS;
                captureFlashAlpha = p<0.15f ? p/0.15f*0.6f : 0.6f*(1f-(p-0.15f)/0.85f); }
        }
    }

    // ======== Screenshot ========

    private void processScreenshot(Image image)
    {
        BufferedImage ss = toBufferedImage(image);
        if (ss == null) return;

        int iw=ss.getWidth(), ih=ss.getHeight();
        double ia=(double)iw/ih;
        int cw, ch;
        if (ia > ASPECT_4_3) { ch=ih; cw=(int)(ih*ASPECT_4_3); }
        else { cw=iw; ch=(int)(iw/ASPECT_4_3); }
        int cx=Math.max(0,(iw-cw)/2), cy=Math.max(0,(ih-ch)/2);
        cw=Math.min(cw,iw-cx); ch=Math.min(ch,ih-cy);
        if (cw<=0||ch<=0) return;

        BufferedImage out = new BufferedImage(cw,ch,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(ss.getSubimage(cx,cy,cw,ch), 0, 0, null);
        g.dispose();

        out = applyFilter(out, config.photoFilter(), config.filterStrength());

        // Stamp watermark at top-left, no padding, scaled to 20% of image width
        if (config.hypercamWatermark() && watermarkImage != null)
        {
            int wmTargetW = (int)(out.getWidth() * 0.2);
            double scale = (double) wmTargetW / watermarkImage.getWidth();
            int wmTargetH = (int)(watermarkImage.getHeight() * scale);

            Graphics2D wg = out.createGraphics();
            wg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            wg.drawImage(watermarkImage, 0, 0, wmTargetW, wmTargetH, null);
            wg.dispose();
        }

        saveScreenshot(out);
    }

    private BufferedImage toBufferedImage(Image img)
    {
        if (img instanceof BufferedImage) return (BufferedImage)img;
        BufferedImage b = new BufferedImage(img.getWidth(null),img.getHeight(null),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=b.createGraphics(); g.drawImage(img,0,0,null); g.dispose();
        return b;
    }

    private void saveScreenshot(BufferedImage image)
    {
        File folder;
        String p = config.screenshotFolder();
        if (p!=null&&!p.trim().isEmpty()) folder=new File(p.trim());
        else folder=new File(System.getProperty("user.home")+File.separator+".runelite",
            "screenshots"+File.separator+"photoscape");
        if (!folder.exists()&&!folder.mkdirs()) { log.error("No folder: {}",folder); return; }

        String ts=dateFormat.format(new Date());
        PhotoFilter f=config.photoFilter();
        String sfx=(f!=PhotoFilter.NONE)?"_"+f.name().toLowerCase():"";
        File file=new File(folder,"photoscape_"+ts+sfx+".png");
        try { ImageIO.write(image,"png",file); log.info("Saved: {}",file); }
        catch (IOException e) { log.error("Save failed",e); }
    }

    // ======== Filters ========

    private BufferedImage applyFilter(BufferedImage image, PhotoFilter filter, int pct)
    {
        if (filter==PhotoFilter.NONE) return image;
        float s=pct/100f;
        switch(filter) {
            case SEPIA: return applySepia(image,s);
            case BLACK_AND_WHITE: return applyBW(image,s);
            case VIGNETTE: return applyVignette(image,s);
            case FILM_GRAIN: return applyGrain(image,s);
            case VINTAGE: image=applySepia(image,s*.7f);image=applyVignette(image,s*.8f);image=applyGrain(image,s*.4f);return image;
            default: return image;
        }
    }

    private BufferedImage applySepia(BufferedImage img, float s) {
        for(int y=0;y<img.getHeight();y++) for(int x=0;x<img.getWidth();x++){
            int rgb=img.getRGB(x,y);int r=(rgb>>16)&0xFF,g=(rgb>>8)&0xFF,b=rgb&0xFF;
            int sr=Math.min(255,(int)(r*.393+g*.769+b*.189)),sg=Math.min(255,(int)(r*.349+g*.686+b*.168)),sb=Math.min(255,(int)(r*.272+g*.534+b*.131));
            img.setRGB(x,y,((int)(r+(sr-r)*s)<<16)|((int)(g+(sg-g)*s)<<8)|(int)(b+(sb-b)*s));
        } return img;
    }
    private BufferedImage applyBW(BufferedImage img, float s) {
        for(int y=0;y<img.getHeight();y++) for(int x=0;x<img.getWidth();x++){
            int rgb=img.getRGB(x,y);int r=(rgb>>16)&0xFF,g=(rgb>>8)&0xFF,b=rgb&0xFF;int gray=(int)(r*.299+g*.587+b*.114);
            img.setRGB(x,y,((int)(r+(gray-r)*s)<<16)|((int)(g+(gray-g)*s)<<8)|(int)(b+(gray-b)*s));
        } return img;
    }
    private BufferedImage applyVignette(BufferedImage img, float s) {
        int w=img.getWidth(),h=img.getHeight();float cx=w/2f,cy=h/2f;
        for(int y=0;y<h;y++) for(int x=0;x<w;x++){
            float dx=(x-cx)/cx,dy=(y-cy)/cy;float v=Math.max(0f,Math.min(1f,1f-(dx*dx+dy*dy)*s*0.5f));
            int rgb=img.getRGB(x,y);img.setRGB(x,y,((int)(((rgb>>16)&0xFF)*v)<<16)|((int)(((rgb>>8)&0xFF)*v)<<8)|(int)((rgb&0xFF)*v));
        } return img;
    }
    private BufferedImage applyGrain(BufferedImage img, float s) {
        Random rng=new Random();int amt=(int)(40*s);
        for(int y=0;y<img.getHeight();y++) for(int x=0;x<img.getWidth();x++){
            int n=rng.nextInt(amt*2+1)-amt;int rgb=img.getRGB(x,y);
            img.setRGB(x,y,(Math.max(0,Math.min(255,((rgb>>16)&0xFF)+n))<<16)|(Math.max(0,Math.min(255,((rgb>>8)&0xFF)+n))<<8)|Math.max(0,Math.min(255,(rgb&0xFF)+n)));
        } return img;
    }

    public String getToggleKeyText() { return config.toggleKey().toString(); }
    public String getCaptureKeyText() { return config.captureKey().toString(); }
}
