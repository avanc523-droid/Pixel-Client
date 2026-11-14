package dev.pixelclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Mario-inspired blocky HUD, with configurable size and corner positions. */
public final class PixelHud {
    private PixelHud() { }
    private static final int WHITE = 0xFFF8F8FF;
    private static final int SUBTLE = 0xFFC2DAEA;
    private static final int GOLD = 0xFFFFE28D;

    private static void box(GuiGraphics g,int x,int y,int w,int h,int color) {g.fill(x,y,x+w,y+h,color);}
    private static int edgeX(int corner,int screenWidth,int panelWidth) {
        return (corner%2==0) ? 9 : Math.max(9,screenWidth - 9 - panelWidth);
    }
    private static int edgeY(int corner,int screenHeight,int panelHeight) {
        return (corner/2==0) ? 9 : Math.max(9,screenHeight - 9 - panelHeight);
    }
    public static void draw(GuiGraphics g, Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        PixelConfig c=PixelClient.CONFIG;
        if (!c.enabled(PixelConfig.Module.CHUNK_SCANNER)) return;
        ScannerEngine engine=PixelClient.ENGINE;
        int px=mc.player.blockPosition().getX(), pz=mc.player.blockPosition().getZ();
        float scale=c.hudSize()/100f;
        if(c.enabled(PixelConfig.Module.RADAR)) {
            int rw=Math.round(157*scale), rh=Math.round(172*scale);
            int x=edgeX(c.radarCorner(),g.guiWidth(),rw), y=edgeY(c.radarCorner(),g.guiHeight(),rh);
            g.pose().pushMatrix(); g.pose().translate(x,y); g.pose().scale(scale,scale);
            radar(g,mc,engine,px,pz,c); g.pose().popMatrix();
        }
        if(c.enabled(PixelConfig.Module.LEAD_LIST)) {
            int h=66+4*13;
            int sw=Math.round(252*scale), sh=Math.round((h+38)*scale);
            int x=edgeX(c.hudCorner(),g.guiWidth(),sw), y=edgeY(c.hudCorner(),g.guiHeight(),sh);
            g.pose().pushMatrix(); g.pose().translate(x,y); g.pose().scale(scale,scale);
            leadHud(g,mc,engine,px,pz,c); g.pose().popMatrix();
        }
    }
    private static void topBar(GuiGraphics g,int w,String title,PixelConfig c) {
        box(g,0,0,w,2,c.accent());
        for(int i=0;i<(w-6)/12;i++)box(g,3+i*12,3,12,3,c.rainbowAt(i));
        g.drawString(Minecraft.getInstance().font,title,9,10,WHITE);
    }
    private static void radar(GuiGraphics g,Minecraft mc,ScannerEngine eng,int px,int pz,PixelConfig c) {
        box(g,2,2,155,172,0xD20B1A37);
        box(g,0,0,155,172,0xCF15284D);
        topBar(g,155,"PIXEL // CHUNK RADAR",c);
        int cx=77,cy=89,pCx=px>>4,pCz=pz>>4;
        box(g,cx-59,cy-59,119,119,0xA1213962);
        for(int i=-7;i<=7;i++) {
            box(g,cx+i*8,cy-59,1,119,0x704E7690);
            box(g,cx-59,cy+i*8,119,1,0x704E7690);
        }
        if(c.enabled(PixelConfig.Module.STORAGE_MARKERS)) {
            for(ScannerEngine.Marker m:eng.nearestStorage(90,px,pz)) {
                int dx=(m.x()>>4)-pCx,dz=(m.z()>>4)-pCz;
                if(Math.abs(dx)<=7 && Math.abs(dz)<=7)box(g,cx+dx*8-1,cy+dz*8-1,3,3,0xFF6AE7FF);
            }
        }
        for(ScannerEngine.Lead lead:eng.allLeads()) {
            if(!lead.dimension.equals(eng.dimension()))continue;
            int dx=lead.chunkX-pCx,dz=lead.chunkZ-pCz;
            if(Math.abs(dx)>7||Math.abs(dz)>7)continue;
            int score=lead.score(c);
            if(score<c.threshold())continue;
            int color=score>=125?0xFFFF677E:score>=80?0xFFFFC168:0xFFFFF48B;
            box(g,cx+dx*8-2,cy+dz*8-2,5,5,color);
        }
        box(g,cx-2,cy-2,5,5,0xFFFFFFFF);
        g.drawString(mc.font,"N",cx-2,cy-70,SUBTLE);
        g.drawString(mc.font,"X "+px+"  Z "+pz,9,155,WHITE);
        box(g,0,170,155,2,c.rainbowAt(3));
    }
    private static void leadHud(GuiGraphics g,Minecraft mc,ScannerEngine e,int px,int pz,PixelConfig c) {
        List<ScannerEngine.Lead> leads=e.top(4);
        box(g,0,0,252,104,0xD0112045);
        topBar(g,252,"PIXEL CLIENT  //  BASE FINDER",c);
        g.drawString(mc.font,"Chunks "+e.scannedCount()+"   Leads "+e.leadCount()+"   Min "+c.threshold(),9,25,SUBTLE);
        if(leads.isEmpty())g.drawString(mc.font,"No strong leads scanned",9,43,GOLD);
        for(int i=0;i<leads.size();i++) {
            ScannerEngine.Lead l=leads.get(i);
            g.drawString(mc.font,"#"+(i+1)+" "+l.x()+", "+l.z()+"  ["+l.score(c)+"]  "+(int)l.distanceFrom(px,pz)+"m",
                9,42+i*13,i==0?GOLD:WHITE);
        }
        int yy=107;
        if(c.enabled(PixelConfig.Module.NETHER_COORDS) && !e.dimension().contains("the_end")){
            boolean nether=e.dimension().contains("the_nether");
            g.drawString(mc.font,(nether?"OW":"NET")+"  X "+(nether?px*8:px/8)+" Z "+(nether?pz*8:pz/8),9,yy,SUBTLE);
            yy+=13;
        }
        if(c.enabled(PixelConfig.Module.STORAGE_MARKERS)) {
            for(ScannerEngine.Marker m:e.nearestStorage(2,px,pz)){
                g.drawString(mc.font,m.type()+" @ "+m.x()+", "+m.y()+", "+m.z(),9,yy,0xFF80F1FF);
                yy+=13;
            }
        }
        ScannerEngine.Lead track=e.tracked();
        if(track!=null)g.drawString(mc.font,"WAYPOINT: "+track.x()+" / "+track.z(),9,yy,0xFFFFD782);
    }
}
