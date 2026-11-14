package dev.pixelclient;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Properties;

/** The only persistent settings are local .properties. No network activity. */
public final class PixelConfig {
    public enum Module {
        CHUNK_SCANNER("Chunk scanner", "Scan currently loaded chunks", true, 0),
        SUS_CHUNKS("Sus chunks", "Rank chunks by combined evidence", true, 0),
        STORAGE("Stash finder", "Find chests, barrels, shulkers", true, 0),
        BUILDS("Build detector", "Find crafted and utility blocks", true, 0),
        SPAWNERS("Spawner finder", "Mark dungeon/trial spawners", true, 0),
        PORTALS("Portal finder", "Flag obsidian and portals", true, 0),
        TUNNELS("Tunnel hints", "Flag regular man-made corridors", true, 0),
        TRAILS("Trail hints", "Find torches, ladders and rails", true, 0),
        RADAR("2D chunk radar", "Top-down chunk signal minimap", true, 1),
        STORAGE_MARKERS("Storage pinpoint", "Show exact loaded container locations", true, 1),
        NETHER_COORDS("Nether conversion", "Display X/Z scaled by eight", true, 1),
        LEAD_LIST("Lead list HUD", "Show coordinates and distance", true, 1),
        ALERTS("Chat alerts", "Notify about strong new leads", true, 1),
        HIGH_SENSITIVITY("High sensitivity", "Show weaker leads, more false alarms", false, 1),
        AUTO_LOG("Auto CSV log", "Append strong finds to a local CSV", false, 2);

        public final String title;
        public final String explanation;
        public final boolean defaultEnabled;
        public final int tab;
        Module(String title, String explanation, boolean defaultEnabled, int tab) {
            this.title = title;
            this.explanation = explanation;
            this.defaultEnabled = defaultEnabled;
            this.tab = tab;
        }
    }

    public enum Palette {
        RAINBOW("Rainbow", 0xFFFF6B95),
        VIOLET("Iris", 0xFFA487FF),
        SKY("Arctic", 0xFF58D8FB),
        EMERALD("Viridian", 0xFF54E7AD),
        ROSE("Rose", 0xFFFF84B9),
        AMBER("Solar", 0xFFFCC16A),
        COBALT("Cobalt", 0xFF7395FF),
        RED("Crimson", 0xFFFF777A),
        MONO("Monochrome", 0xFFE5EBF3),
        CUSTOM("Custom RGB", 0xFFA487FF);

        public final String title;
        public final int accent;
        Palette(String title, int accent) { this.title = title; this.accent = accent; }
    }

    private final EnumMap<Module, Boolean> toggles = new EnumMap<>(Module.class);
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("pixelclient.properties");
    private final Path profiles = FabricLoader.getInstance().getConfigDir().resolve("pixelclient/profiles");

    private Palette palette = Palette.RAINBOW;
    private int rgbR = 164, rgbG = 135, rgbB = 255;
    private int opacity = 94;
    private int panelSize = 100;
    private int hudSize = 100;
    private int hudCorner = 0;
    private int radarCorner = 1;
    private int baseThreshold = 48;
    private int radius = 8;
    private boolean glow = true;
    private boolean compact = false;
    private int rainbowSpeed = 7;
    private boolean pixelParticles = true;

    public PixelConfig() {
        for (Module m : Module.values()) toggles.put(m, m.defaultEnabled);
        load();
    }

    public boolean enabled(Module m) { return toggles.getOrDefault(m, m.defaultEnabled); }
    public void toggle(Module m) { toggles.put(m, !enabled(m)); save(); }
    public int threshold() { return enabled(Module.HIGH_SENSITIVITY) ? 22 : baseThreshold; }
    public int scanRadius() { return radius; }
    public Palette palette() { return palette; }
    public int accent() {
        if (palette == Palette.RAINBOW) return rainbowAt(0);
        return palette == Palette.CUSTOM ? (0xFF000000 | (rgbR << 16) | (rgbG << 8) | rgbB) : palette.accent;
    }
    public int rainbowAt(int shift) {
        float hue = (float) ((System.currentTimeMillis() / (2400.0 / rainbowSpeed) + shift * 28.0) % 360.0) / 360f;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.78f, 1.0f) & 0xFFFFFF);
    }
    public int rainbowSpeed() { return rainbowSpeed; }
    public boolean pixelParticles() { return pixelParticles; }
    public void changeRainbowSpeed(int delta) { rainbowSpeed = clamp(rainbowSpeed + delta, 1, 15); save(); }
    public void togglePixelParticles() { pixelParticles = !pixelParticles; save(); }
    public int r() { return rgbR; }
    public int g() { return rgbG; }
    public int b() { return rgbB; }
    public int opacity() { return opacity; }
    public int panelSize() { return panelSize; }
    public int hudSize() { return hudSize; }
    public int hudCorner() { return hudCorner; }
    public int radarCorner() { return radarCorner; }
    public int baseThreshold() { return baseThreshold; }
    public boolean glow() { return glow; }
    public boolean compact() { return compact; }

    public void setPalette(Palette p) { palette = p; save(); }
    public void changeRgb(int channel, int delta) {
        palette = Palette.CUSTOM;
        if (channel == 0) rgbR = clamp(rgbR + delta, 0, 255);
        if (channel == 1) rgbG = clamp(rgbG + delta, 0, 255);
        if (channel == 2) rgbB = clamp(rgbB + delta, 0, 255);
        save();
    }
    public void changeOpacity(int delta) { opacity = clamp(opacity + delta, 55, 100); save(); }
    public void changePanelSize(int delta) { panelSize = clamp(panelSize + delta, 75, 120); save(); }
    public void changeHudSize(int delta) { hudSize = clamp(hudSize + delta, 65, 150); save(); }
    public void changeThreshold(int delta) { baseThreshold = clamp(baseThreshold + delta, 15, 200); save(); }
    public void changeRadius(int delta) { radius = clamp(radius + delta, 3, 8); save(); }
    public void nextHudCorner() { hudCorner = (hudCorner + 1) % 4; save(); }
    public void nextRadarCorner() { radarCorner = (radarCorner + 1) % 4; save(); }
    public void toggleGlow() { glow = !glow; save(); }
    public void toggleCompact() { compact = !compact; save(); }
    public static String cornerName(int corner) {
        return switch (corner) { case 0 -> "Top left"; case 1 -> "Top right";
            case 2 -> "Bottom left"; default -> "Bottom right"; };
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int number(Properties p, String key, int fallback, int lo, int hi) {
        try { return clamp(Integer.parseInt(p.getProperty(key, "")), lo, hi); }
        catch (NumberFormatException e) { return fallback; }
    }

    private void fromProperties(Properties p) {
        for (Module m : Module.values()) {
            String value = p.getProperty(m.name());
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))
                toggles.put(m, Boolean.parseBoolean(value));
        }
        try { palette = Palette.valueOf(p.getProperty("theme", "RAINBOW")); }
        catch (IllegalArgumentException e) { palette = Palette.RAINBOW; }
        rgbR = number(p, "rgb.r", 164, 0, 255);
        rgbG = number(p, "rgb.g", 135, 0, 255);
        rgbB = number(p, "rgb.b", 255, 0, 255);
        opacity = number(p, "opacity", 94, 55, 100);
        panelSize = number(p, "panel.size", 100, 75, 120);
        hudSize = number(p, "hud.size", 100, 65, 150);
        hudCorner = number(p, "hud.corner", 0, 0, 3);
        radarCorner = number(p, "radar.corner", 1, 0, 3);
        baseThreshold = number(p, "threshold", 48, 15, 200);
        radius = number(p, "scan.radius", 8, 3, 8);
        glow = Boolean.parseBoolean(p.getProperty("glow", "true"));
        compact = Boolean.parseBoolean(p.getProperty("compact", "false"));
        rainbowSpeed = number(p, "rainbow.speed", 7, 1, 15);
        pixelParticles = Boolean.parseBoolean(p.getProperty("pixel.particles", "true"));
    }
    private Properties toProperties() {
        Properties p = new Properties();
        for (Module m : Module.values()) p.setProperty(m.name(), Boolean.toString(enabled(m)));
        p.setProperty("theme", palette.name());
        p.setProperty("rgb.r", "" + rgbR);
        p.setProperty("rgb.g", "" + rgbG);
        p.setProperty("rgb.b", "" + rgbB);
        p.setProperty("opacity", "" + opacity);
        p.setProperty("panel.size", "" + panelSize);
        p.setProperty("hud.size", "" + hudSize);
        p.setProperty("hud.corner", "" + hudCorner);
        p.setProperty("radar.corner", "" + radarCorner);
        p.setProperty("threshold", "" + baseThreshold);
        p.setProperty("scan.radius", "" + radius);
        p.setProperty("glow", "" + glow);
        p.setProperty("compact", "" + compact);
        return p;
    }
    private void load() {
        if (!Files.isRegularFile(path)) return;
        try (InputStream in = Files.newInputStream(path)) {
            Properties p = new Properties(); p.load(in); fromProperties(p);
        } catch (Exception e) { PixelClient.LOG.warn("Cannot read Pixel Client settings", e); }
    }
    public void save() { write(path); }
    public void saveProfile(int slot) { write(profilePath(slot)); }
    public boolean loadProfile(int slot) {
        Path target = profilePath(slot);
        if (!Files.isRegularFile(target)) return false;
        try (InputStream in = Files.newInputStream(target)) {
            Properties p = new Properties(); p.load(in); fromProperties(p); save(); return true;
        } catch (Exception e) { PixelClient.LOG.warn("Cannot load Pixel Client profile", e); return false; }
    }
    private Path profilePath(int slot) {
        if (slot < 1 || slot > 3) throw new IllegalArgumentException("Invalid profile slot");
        return profiles.resolve("slot-" + slot + ".properties");
    }
    public boolean hasProfile(int slot) { return Files.isRegularFile(profilePath(slot)); }
    private void write(Path target) {
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(target)) {
                toProperties().store(out, "Pixel Client settings - local files only");
            }
        } catch (Exception e) { PixelClient.LOG.warn("Cannot save Pixel Client settings", e); }
    }
}
