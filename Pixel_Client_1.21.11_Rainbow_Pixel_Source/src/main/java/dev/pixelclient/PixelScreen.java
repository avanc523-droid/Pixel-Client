package dev.pixelclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Original rainbow platform-game-inspired pixel UI, not Nintendo assets. */
public final class PixelScreen extends Screen {
    private static final String[] SECTIONS = { "SCANNER", "RADAR + HUD", "THEMES", "TUNING", "PROFILES" };
    private static final int WHITE = 0xFFF9FBFF;
    private static final int DIM = 0xFFB9C7EA;
    private static final int DARK = 0xFF15213E;
    private static final int SKY = 0xFF172B57;
    private int section = 0;
    private String query = "";
    private String status = "Local chunk analysis  //  no external connections";
    private EditBox searchBox;

    public PixelScreen() { super(Component.literal("Pixel Client")); }
    private int w() { return Math.min(width - 12, 640 * PixelClient.CONFIG.panelSize() / 100); }
    private int h() { return Math.min(height - 12, 380 * PixelClient.CONFIG.panelSize() / 100); }
    private int x() { return (width - w()) / 2; }
    private int y() { return (height - h()) / 2; }
    private int mainX() { return x() + Math.min(125, w() / 4) + 11; }
    private int mainW() { return x() + w() - 12 - mainX(); }

    @Override
    protected void init() {
        int x = x(), y = y(), w = w(), h = h();
        int side = mainX() - x - 9;
        int navStride = Math.min(38, Math.max(26, (h - 120) / 5));
        for (int i = 0; i < SECTIONS.length; i++) {
            final int idx = i;
            add(x + 11, y + 78 + i * navStride, side - 16, 30, SECTIONS[i], () -> {
                section = idx;
                query = "";
                rebuild();
            }, 0, section == i);
        }
        add(x + 11, y + h - 39, side - 16, 22, "CLOSE [X]", this::onClose, 0xFFFF688A, false);

        if (section <= 1) {
            int mx = mainX(), mw = mainW();
            searchBox = new EditBox(font, mx + 9, y + 76, Math.max(70, mw - 18), 18, Component.literal("Find a module"));
            searchBox.setMaxLength(50);
            searchBox.setValue(query);
            searchBox.setSuggestion("Search modules...");
            searchBox.setResponder(next -> {
                if (!query.equals(next)) {
                    query = next;
                    rebuild();
                    searchBox.setFocused(true);
                    setFocused(searchBox);
                }
            });
            addRenderableWidget(searchBox);
            buildModules();
        } else if (section == 2) buildTheme();
        else if (section == 3) buildTuning();
        else buildProfiles();
    }

    private void rebuild() { clearWidgets(); init(); }
    private PixelWidget add(int bx, int by, int bw, int bh, String txt, Runnable action, int swatch, boolean selected) {
        return addRenderableWidget(new PixelWidget(bx, by, Math.max(16, bw), Math.max(14, bh), txt, action, swatch, selected));
    }
    private void buildModules() {
        List<PixelConfig.Module> found = filtered();
        int mx = mainX(), w = mainW(), sy = y() + 104, bottom = y() + h() - 47;
        int gap = 9, cw = (w - gap - 2) / 2;
        int ch = Math.max(24, Math.min(53, (bottom - sy - gap * 3) / 4));
        for (int i = 0; i < Math.min(8, found.size()); i++) {
            PixelConfig.Module module = found.get(i);
            int bx = mx + (i % 2) * (cw + gap);
            int by = sy + (i / 2) * (ch + gap);
            add(bx + cw - 52, by + 8, 45, 23,
                PixelClient.CONFIG.enabled(module) ? "ON" : "OFF", () -> {
                    PixelClient.CONFIG.toggle(module);
                    status = module.title + (PixelClient.CONFIG.enabled(module) ? " enabled" : " disabled");
                    rebuild();
                }, PixelClient.CONFIG.enabled(module) ? 0xFF56EF91 : 0xFFFF7892,
                PixelClient.CONFIG.enabled(module));
        }
    }
    private List<PixelConfig.Module> filtered() {
        List<PixelConfig.Module> out = new ArrayList<>();
        for (PixelConfig.Module module : PixelConfig.Module.values()) {
            boolean group = section == 0 ? module.tab == 0 : module.tab != 0;
            if (group && (module.title.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                || module.explanation.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))) out.add(module);
        }
        return out;
    }
    private void buildTheme() {
        int mx = mainX(), mw = mainW(), yy = y() + 100;
        PixelConfig.Palette[] palettes = PixelConfig.Palette.values();
        int cw = (mw - 4 * 7) / 5;
        for (int i = 0; i < palettes.length; i++) {
            final PixelConfig.Palette p = palettes[i];
            int j = i;
            add(mx + (j % 5) * (cw + 7), yy + (j / 5) * 47, cw - 1, 35,
                p.title, () -> { PixelClient.CONFIG.setPalette(p); status = "Theme: " + p.title; rebuild(); },
                p == PixelConfig.Palette.RAINBOW ? PixelClient.CONFIG.rainbowAt(i * 5) : p.accent,
                PixelClient.CONFIG.palette() == p);
        }
        // Original rainbow theme + editable RGB sliders and glow switches.
        int row = yy + 122;
        for (int channel = 0; channel < 3; channel++) {
            final int ch = channel;
            int xx = mx + channel * (mw / 3);
            add(xx + 18, row + 22, 27, 23, "-", () -> { PixelClient.CONFIG.changeRgb(ch, -8); rebuild(); }, 0xFF79CBFF, false);
            add(xx + 95, row + 22, 27, 23, "+", () -> { PixelClient.CONFIG.changeRgb(ch, 8); rebuild(); }, 0xFF79CBFF, false);
        }
        add(mx + 11, row + 63, 126, 23, "PIXEL SPARKLES", () -> { PixelClient.CONFIG.togglePixelParticles(); rebuild(); }, 0xFFFFD65B, PixelClient.CONFIG.pixelParticles());
        add(mx + 152, row + 63, 101, 23, "GLOW EDGES", () -> { PixelClient.CONFIG.toggleGlow(); rebuild(); }, 0xFF6EF9B2, PixelClient.CONFIG.glow());
        add(mx + 266, row + 63, 119, 23, "RAINBOW MODE", () -> { PixelClient.CONFIG.setPalette(PixelConfig.Palette.RAINBOW); rebuild(); }, 0xFFFF91CF, PixelClient.CONFIG.palette() == PixelConfig.Palette.RAINBOW);
    }

    private void buildTuning() {
        int mx = mainX(), yy = y() + 95, mw = mainW();
        int tw = (mw - 14) / 2;
        tuningStepper(mx, yy, tw, "SCAN RADIUS", "" + PixelClient.CONFIG.scanRadius(),
            () -> PixelClient.CONFIG.changeRadius(-1), () -> PixelClient.CONFIG.changeRadius(1));
        tuningStepper(mx + tw + 14, yy, tw, "LEAD THRESHOLD", "" + PixelClient.CONFIG.baseThreshold(),
            () -> PixelClient.CONFIG.changeThreshold(-5), () -> PixelClient.CONFIG.changeThreshold(5));
        tuningStepper(mx, yy + 66, tw, "MENU SCALE", PixelClient.CONFIG.panelSize() + "%",
            () -> PixelClient.CONFIG.changePanelSize(-5), () -> PixelClient.CONFIG.changePanelSize(5));
        tuningStepper(mx + tw + 14, yy + 66, tw, "HUD SCALE", PixelClient.CONFIG.hudSize() + "%",
            () -> PixelClient.CONFIG.changeHudSize(-10), () -> PixelClient.CONFIG.changeHudSize(10));
        tuningStepper(mx, yy + 132, tw, "MENU OPACITY", PixelClient.CONFIG.opacity() + "%",
            () -> PixelClient.CONFIG.changeOpacity(-5), () -> PixelClient.CONFIG.changeOpacity(5));
        tuningStepper(mx + tw + 14, yy + 132, tw, "RAINBOW SPEED", "" + PixelClient.CONFIG.rainbowSpeed(),
            () -> PixelClient.CONFIG.changeRainbowSpeed(-1), () -> PixelClient.CONFIG.changeRainbowSpeed(1));
        add(mx, yy + 208, tw - 4, 25, "HUD: " + PixelConfig.cornerName(PixelClient.CONFIG.hudCorner()),
            () -> { PixelClient.CONFIG.nextHudCorner(); rebuild(); }, 0xFF75D7FF, false);
        add(mx + tw + 14, yy + 208, tw - 4, 25, "RADAR: " + PixelConfig.cornerName(PixelClient.CONFIG.radarCorner()),
            () -> { PixelClient.CONFIG.nextRadarCorner(); rebuild(); }, 0xFF75D7FF, false);
    }
    private void tuningStepper(int tx, int ty, int w, String title, String value, Runnable decrease, Runnable increase) {
        add(tx + 1, ty + 24, 34, 25, "-", () -> { decrease.run(); rebuild(); }, 0xFF68DFFF, false);
        add(tx + w - 37, ty + 24, 34, 25, "+", () -> { increase.run(); rebuild(); }, 0xFF68DFFF, false);
    }

    private void buildProfiles() {
        int mx = mainX(), yy = y() + 97;
        for (int slot = 1; slot <= 3; slot++) {
            final int i = slot;
            add(mx + 12, yy + (slot - 1) * 48 + 19, 99, 23, "SAVE SLOT " + slot,
                () -> { PixelClient.CONFIG.saveProfile(i); status = "Saved local profile " + i; rebuild(); }, 0xFF63EFA5, false);
            add(mx + 126, yy + (slot - 1) * 48 + 19, 99, 23, "LOAD SLOT " + slot,
                () -> { status = PixelClient.CONFIG.loadProfile(i) ? "Loaded profile " + i : "No saved data for slot " + i; rebuild(); }, 0xFF65C8FF, false);
        }
        int footer = yy + 166;
        add(mx + 12, footer, 143, 26, "EXPORT FINDINGS", () -> {
            try {
                Path path = PixelClient.ENGINE.exportCsv();
                status = "CSV saved: " + path.getFileName();
            } catch (Exception ex) { status = "CSV export failed, check log"; PixelClient.LOG.warn("CSV export failed", ex); }
        }, 0xFFFFD166, false);
        add(mx + 169, footer, 134, 26, "TRACK TOP LEAD", () -> {
            PixelClient.ENGINE.trackBest();
            status = PixelClient.ENGINE.tracked() == null ? "No qualifying leads yet" : "Tracking highest-scoring lead";
        }, 0xFF91CAFF, false);
        add(mx + 317, footer, 121, 26, "CLEAR WAYPOINT", () -> {
            PixelClient.ENGINE.clearTrack(); status = "Waypoint cleared";
        }, 0xFFFFA1A4, false);
    }

    private static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }
    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        rect(g, x, y, w, 2, color); rect(g, x, y + h - 2, w, 2, color);
        rect(g, x, y, 2, h, color); rect(g, x + w - 2, y, 2, h, color);
    }
    private static void cloud(GuiGraphics g, int x, int y) {
        rect(g, x + 8, y, 25, 6, 0xFFEFFAFF);
        rect(g, x, y + 6, 43, 8, 0xFFEFFAFF);
        rect(g, x + 8, y + 14, 29, 4, 0xFFB8E9FF);
    }
    private static void coin(GuiGraphics g, int x, int y) {
        rect(g, x + 4, y, 12, 3, 0xFFFFD85D);
        rect(g, x, y + 3, 20, 12, 0xFFFFD85D);
        rect(g, x + 4, y + 15, 12, 3, 0xFFCF8B30);
        rect(g, x + 9, y + 4, 3, 10, 0xFFFFFFFF);
        rect(g, x + 15, y + 4, 2, 10, 0xFFF8A934);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        int x=x(), y=y(), w=w(), h=h();
        int mainX=mainX(), mainW=mainW();
        PixelConfig c = PixelClient.CONFIG;
        int alpha = Math.max(130, c.opacity() * 255 / 100);
        rect(g, x + 5, y + 7, w, h, 0xA0000414);
        rect(g, x, y, w, h, (alpha << 24) | (SKY & 0xFFFFFF));
        if (c.glow()) {
            for (int i=3; i>=1; i--) outline(g, x-i, y-i, w+2*i, h+2*i,
                (0x30 + (4-i)*0x15) << 24 | (c.accent() & 0xFFFFFF));
        }
        outline(g, x, y, w, h, c.accent());
        rect(g, x + 4, y + 4, w - 8, 51, 0xFF143E70);
        rect(g, x + 6, y + 55, w - 12, 5, 0xFF0E1738);
        // Moving rainbow strip of pixel blocks; no external image or texture assets.
        int tiles = Math.max(1,(w-13)/16);
        for(int i=0;i<tiles;i++) rect(g,x+6+i*16,y+55,16,4,c.rainbowAt(i));
        rect(g, x+7, y+64, mainX-x-13, h-76, 0xFF0E1A3C);
        rect(g, mainX-5, y+64, 2, h-76, 0xFF405A8B);
        rect(g, mainX, y+64, mainW, h-76, 0xB61B3154);
        rect(g, x+5, y+h-19, w-10, 14, 0xFF6BCB57);
        for(int i=0;i<(w-12)/13;i++) rect(g,x+6+i*13,y+h-15+(i%3)*2,13,11,
            i%2==0 ? 0xFF8A543A : 0xFF9B633E);
        for(int i=0;i<(w-12)/13;i+=3) rect(g,x+7+i*13,y+h-9,5,3,0xFFBE7E50);
        cloud(g,x+w-116,y+16); coin(g,x+w-61,y+19);
        String logo = "P I X E L   C L I E N T";
        g.drawString(font, logo, x+19,y+15,WHITE);
        g.drawString(font, "RAINBOW EDITION  //  FABRIC 1.21.11",x+19,y+33,0xFFD5EAFF);
        g.drawString(font, "WORLD", x+23, y+66,0xFF8FE7FF);
        g.drawString(font, "TOOLS", x+23, Math.min(y+281,y+h-58),0xFFFFDA7C);

        if (c.pixelParticles()) {
            for(int i=0;i<12;i++) {
                int tx=x+14+(i*47)%(w-30), ty=y+7+(i*29)%45;
                rect(g,tx,ty,3,3,c.rainbowAt(i+4));
            }
        }
        if (section <= 1) drawModules(g);
        else if (section == 2) drawThemes(g);
        else if (section == 3) drawTuning(g);
        else drawProfiles(g);
        g.drawString(font, clip(status, w-30), x+15,y+h-31,0xFFFFFFFF);
        super.render(g,mouseX,mouseY,delta);
    }
    private String clip(String s,int maxWidth) {
        while(!s.isEmpty()&&font.width(s)>maxWidth)s=s.substring(0,s.length()-1);
        return s;
    }
    private void drawModules(GuiGraphics g) {
        int mx = mainX(), yy = y()+73, mw = mainW();
        g.drawString(font, section == 0 ? "BASE FINDER  >  MODULES" : "RADAR, ALERTS & OVERLAYS", mx+7, yy-2, WHITE);
        List<PixelConfig.Module> mods=filtered();
        int sy=y()+104, bot=y()+h()-47, gap=9, cw=(mw-gap-2)/2;
        int ch=Math.max(24,Math.min(53,(bot-sy-gap*3)/4));
        for(int i=0;i<Math.min(8,mods.size());i++){
            PixelConfig.Module m=mods.get(i);
            int xx=mx+(i%2)*(cw+gap), by=sy+(i/2)*(ch+gap);
            rect(g,xx,by,cw,ch,0xFF101C37);
            outline(g,xx,by,cw,ch,PixelClient.CONFIG.enabled(m)?0xFF4B8E85:0xFF4A587B);
            rect(g,xx+5,by+7,4,ch-14,PixelClient.CONFIG.enabled(m)?0xFF64F2A2:0xFFF88088);
            String title=clip(m.title,Math.max(40,cw-69));
            g.drawString(font,title,xx+15,by+9,WHITE);
            if(ch>=43)g.drawString(font,clip(m.explanation,cw-20),xx+15,by+29,DIM);
        }
        if(mods.isEmpty())g.drawString(font,"NO MATCHES - try another search",mx+18,sy+27,0xFFFFC76B);
        g.drawString(font,clip("SCANNED: "+PixelClient.ENGINE.scannedCount()+"   LEADS: "+PixelClient.ENGINE.leadCount()+"   ONLY SERVER-LOADED CHUNKS",mw-22),mx+5,y()+h()-46,0xFFFFE4A7);
    }
    private void drawThemes(GuiGraphics g) {
        int mx=mainX(), yy=y()+79, mw=mainW();
        g.drawString(font,"CHOOSE YOUR PIXEL WORLD",mx+9,yy,WHITE);
        g.drawString(font,"CUSTOM RGB MIXER",mx+9,yy+139,0xFFFDDEA3);
        int[] rgb={PixelClient.CONFIG.r(),PixelClient.CONFIG.g(),PixelClient.CONFIG.b()};
        String[] names={"RED","GREEN","BLUE"};
        for(int i=0;i<3;i++){
            int xx=mx+i*(mw/3);
            g.drawString(font,names[i]+"  "+rgb[i],xx+11,yy+152,WHITE);
            rect(g,xx+50,yy+184,34,12,i==0?0xFFFF648A:i==1?0xFF5BEC99:0xFF65BFFF);
        }
        g.drawString(font,"RAINBOW RUNS AUTOMATICALLY",mx+9,yy+266,0xFFFFDB83);
    }
    private void drawTuning(GuiGraphics g) {
        int mx=mainX(),yy=y()+79,mw=mainW(),tw=(mw-14)/2;
        g.drawString(font,"FINE-TUNE YOUR WORLD SCANNER",mx+9,yy,WHITE);
        String[] names={"SCAN RADIUS","LEAD THRESHOLD","MENU SCALE","HUD SCALE","MENU OPACITY","RAINBOW SPEED"};
        String[] values={""+PixelClient.CONFIG.scanRadius(),""+PixelClient.CONFIG.baseThreshold(),
            PixelClient.CONFIG.panelSize()+"%",PixelClient.CONFIG.hudSize()+"%",PixelClient.CONFIG.opacity()+"%", ""+PixelClient.CONFIG.rainbowSpeed()};
        for(int i=0;i<6;i++){
            int xx=mx+(i%2)*(tw+14), by=yy+16+(i/2)*66;
            rect(g,xx,by,tw,60,DARK);
            outline(g,xx,by,tw,60,0xFF425C92);
            g.drawString(font,names[i],xx+12,by+5,DIM);
            g.drawString(font,values[i],xx+tw/2-4,by+37,WHITE);
        }
        g.drawString(font,"HUD CORNERS AND RADAR POSITION",mx+9,yy+211,0xFFFFD978);
    }
    private void drawProfiles(GuiGraphics g) {
        int mx=mainX(),yy=y()+79;
        g.drawString(font,"SAVE CUSTOM PRESETS TO YOUR PC",mx+9,yy,WHITE);
        for(int i=1;i<=3;i++){
            int y=yy+19+(i-1)*48;
            rect(g,mx+5,y,mainW()-12,43,0xFF101C37);
            outline(g,mx+5,y,mainW()-12,43,0xFF4F5F9C);
            g.drawString(font,"SLOT "+i+"  "+(PixelClient.CONFIG.hasProfile(i)?"READY":"EMPTY"),mx+251,y+25,DIM);
        }
        g.drawString(font,"All presets stay in .minecraft/config/pixelclient",mx+8,yy+208,DIM);
        g.drawString(font,"No account, Discord or browser file access",mx+8,yy+222,DIM);
    }
    @Override
    public boolean isPauseScreen() { return false; }
}
