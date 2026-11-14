package dev.pixelclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only, main-thread chunk analyzer. No packet injection, reflection, networking,
 * account access, arbitrary file scanning, or executable downloads.
 *
 * It only examines chunks already received normally from the server.
 */
public final class ScannerEngine {
    public record Marker(String dimension, int x, int y, int z, String type) {
        public double distanceFrom(double px, double pz) { return Math.hypot(x - px, z - pz); }
    }

    public static final class Lead {
        public final String dimension;
        public final int chunkX;
        public final int chunkZ;
        public final int storage;
        public final int shulkers;
        public final int spawners;
        public final int buildBlocks;
        public final int workstations;
        public final int portals;
        public final int trails;
        public final int tunnels;
        public final int caveLights;
        public final long observedTick;
        public final int lowestY;

        Lead(String dim, int cx, int cz, Counts c, long tick) {
            dimension = dim; chunkX = cx; chunkZ = cz;
            storage = c.storage; shulkers = c.shulkers; spawners = c.spawners;
            buildBlocks = c.buildBlocks; workstations = c.workstations;
            portals = c.portals; trails = c.trails; tunnels = c.tunnels;
            caveLights = c.caveLights; lowestY = c.lowestY;
            observedTick = tick;
        }
        public int x() { return chunkX * 16 + 8; }
        public int z() { return chunkZ * 16 + 8; }
        public int score(PixelConfig c) {
            int score = 0;
            if (c.enabled(PixelConfig.Module.STORAGE))
                score += Math.min(180, storage * 14 + shulkers * 20);
            if (c.enabled(PixelConfig.Module.SPAWNERS))
                score += Math.min(30, spawners * 8);
            if (c.enabled(PixelConfig.Module.BUILDS))
                score += Math.min(90, buildBlocks * 2 + workstations * 11);
            if (c.enabled(PixelConfig.Module.PORTALS))
                score += Math.min(55, portals * 8);
            if (c.enabled(PixelConfig.Module.TRAILS))
                score += Math.min(34, trails * 3 + caveLights * 4);
            if (c.enabled(PixelConfig.Module.TUNNELS))
                score += Math.min(24, tunnels * 8);
            // Multiple independent signs are much stronger than one natural feature.
            int different = (storage > 0 ? 1 : 0) + (workstations > 0 ? 1 : 0)
                + (portals > 0 ? 1 : 0) + (buildBlocks > 2 ? 1 : 0) + (trails > 1 ? 1 : 0);
            if (different >= 3) score += 30;
            else if (different == 2) score += 10;
            return score;
        }
        public String evidence() {
            return "storage=" + storage + " (shulkers=" + shulkers + "), spawners=" + spawners
                + ", crafted=" + buildBlocks + ", workstations=" + workstations
                + ", portals=" + portals + ", trail=" + trails + ", tunnel=" + tunnels;
        }
        public double distanceFrom(double x, double z) { return Math.hypot(x() - x, z() - z); }
    }

    private static final class Counts {
        int storage, shulkers, spawners, buildBlocks, workstations, portals, trails, tunnels, caveLights;
        int lowestY = Integer.MAX_VALUE;
        final List<Marker> markers = new ArrayList<>();
        boolean hasSignals() {
            return storage + shulkers + spawners + buildBlocks + workstations + portals + trails + tunnels + caveLights > 0;
        }
        void hitY(int y) { lowestY = Math.min(lowestY, y); }
    }

    private final PixelConfig config;
    private ClientLevel currentLevel;
    private final Map<String, Lead> leads = new LinkedHashMap<>();
    private final Map<String, List<Marker>> markersByChunk = new LinkedHashMap<>();
    private final Map<String, Long> scannedAt = new HashMap<>();
    private final Set<String> alerted = new HashSet<>();
    private long tick = 0;
    private int scanCursor = 0;
    private int totalScanned = 0;
    private Lead tracked;

    public ScannerEngine(PixelConfig c) { config = c; }
    public int scannedCount() { return totalScanned; }
    public int leadCount() { return leads.size(); }
    public Lead tracked() { return tracked; }
    public String dimension() { return currentLevel == null ? "" : currentLevel.dimension().location().toString(); }
    public Collection<Lead> allLeads() { return List.copyOf(leads.values()); }

    public void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            if (currentLevel != null) reset();
            return;
        }
        if (client.level != currentLevel) {
            reset();
            currentLevel = client.level;
        }
        tick++;
        if (!config.enabled(PixelConfig.Module.CHUNK_SCANNER)) return;
        // Circular ring of 17x17 chunk candidates, one per client tick.
        // An unloaded chunk is skipped, never requested from the server.
        int radius = config.scanRadius();
        int side = 2 * radius + 1;
        int index = scanCursor++ % (side * side);
        int dx = index % side - radius;
        int dz = index / side - radius;
        if (dx * dx + dz * dz > radius * radius) return;
        int cx = client.player.blockPosition().getX() >> 4;
        int cz = client.player.blockPosition().getZ() >> 4;
        cx += dx; cz += dz;
        String key = key(dimension(), cx, cz);
        if (tick - scannedAt.getOrDefault(key, Long.MIN_VALUE / 2) < 400) return;
        LevelChunk chunk = client.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
        if (chunk == null) return;
        scannedAt.put(key, tick);
        if (scannedAt.size() > 10000) scannedAt.clear();
        totalScanned++;
        Counts counts = analyze(chunk, client.player.blockPosition().getY());
        if (counts.markers.isEmpty()) markersByChunk.remove(key);
        else markersByChunk.put(key, List.copyOf(counts.markers));
        if (markersByChunk.size() > 2500) markersByChunk.remove(markersByChunk.keySet().iterator().next());
        if (!counts.hasSignals()) {
            leads.remove(key);
            return;
        }
        Lead lead = new Lead(dimension(), cx, cz, counts, tick);
        if (lead.score(config) <= 0) { leads.remove(key); return; }
        leads.put(key, lead);
        if (leads.size() > 2500) leads.remove(leads.keySet().iterator().next());
        if (config.enabled(PixelConfig.Module.SUS_CHUNKS)
                && lead.score(config) >= config.threshold() && alerted.add(key)) {
            if (config.enabled(PixelConfig.Module.ALERTS)) {
                client.player.displayClientMessage(Component.literal(
                    "[Pixel Client] Possible base lead @ " + lead.x() + ", " + lead.z()
                        + " (score " + lead.score(config) + ") — unconfirmed"), false);
            }
            if (config.enabled(PixelConfig.Module.AUTO_LOG)) append(lead);
        }
    }

    private void reset() {
        leads.clear(); markersByChunk.clear(); scannedAt.clear(); alerted.clear();
        tracked = null; scanCursor = 0; totalScanned = 0; tick = 0; currentLevel = null;
    }

    private static String key(String dim, int cx, int cz) { return dim + "/" + cx + "/" + cz; }

    private Counts analyze(LevelChunk chunk, int playerY) {
        Counts c = new Counts();
        // Exact block-entity list; no approximate sampling for containers.
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            BlockPos p = entity.getBlockPos();
            Block b = entity.getBlockState().getBlock();
            if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL
                    || b == Blocks.HOPPER || b == Blocks.DROPPER || b == Blocks.DISPENSER) {
                c.storage++; c.hitY(p.getY());
                c.markers.add(new Marker(dimension(), p.getX(), p.getY(), p.getZ(), b == Blocks.BARREL ? "Barrel" : "Container"));
            } else if (b instanceof ShulkerBoxBlock) {
                c.storage++; c.shulkers++; c.hitY(p.getY());
                c.markers.add(new Marker(dimension(), p.getX(), p.getY(), p.getZ(), "Shulker"));
            } else if (b == Blocks.SPAWNER || b == Blocks.TRIAL_SPAWNER) {
                c.spawners++; c.hitY(p.getY());
            } else if (isWorkstation(b)) {
                c.workstations++; c.hitY(p.getY());
            } else if (b == Blocks.BEACON || b == Blocks.ENDER_CHEST) {
                c.workstations++; c.hitY(p.getY());
            }
        }
        int min = chunk.getMinY();
        int max = chunk.getMaxY();
        int[] sampledY = { -58, -48, -32, -16, 0, 12, 24, 36, 48, 64, 80, 96, 128, 160, 192, 240, (playerY / 4) * 4 };
        int x0 = chunk.getPos().x * 16;
        int z0 = chunk.getPos().z * 16;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        // Sparse grid: checks 64 positions per layer; false negatives are possible.
        for (int y : sampledY) {
            if (y < min || y > max) continue;
            for (int lx = 1; lx < 16; lx += 2) {
                for (int lz = 1; lz < 16; lz += 2) {
                    at.set(x0 + lx, y, z0 + lz);
                    BlockState state = chunk.getBlockState(at);
                    Block b = state.getBlock();
                    if (isCrafted(b)) { c.buildBlocks++; c.hitY(y); }
                    if (isTrail(b)) {
                        c.trails++; c.hitY(y);
                        if (y <= 40 && (b == Blocks.TORCH || b == Blocks.WALL_TORCH || b == Blocks.LANTERN)) c.caveLights++;
                    }
                    if (b == Blocks.NETHER_PORTAL || b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN
                            || b == Blocks.RESPAWN_ANCHOR) { c.portals++; c.hitY(y); }
                }
            }
        }
        // Tunnel heuristic: 1x2 corridors with solid floor and side walls.
        // Natural caves can also match; intentionally low evidence weight.
        if (config.enabled(PixelConfig.Module.TUNNELS)) {
            for (int y : new int[]{ -48, -32, -16, 0, 24, 40 }) {
                if (y <= min || y + 2 > max) continue;
                for (int lx = 4; lx <= 10; lx += 3) {
                    for (int lz = 4; lz <= 10; lz += 3) {
                        if (corridor(chunk, x0 + lx, y, z0 + lz, true)
                                || corridor(chunk, x0 + lx, y, z0 + lz, false)) {
                            c.tunnels++; c.hitY(y);
                        }
                    }
                }
            }
        }
        return c;
    }

    private static boolean corridor(LevelChunk chunk, int x, int y, int z, boolean alongX) {
        for (int step = -2; step <= 2; step++) {
            int xx = x + (alongX ? step : 0);
            int zz = z + (alongX ? 0 : step);
            if (!isAir(chunk, xx, y, zz) || !isAir(chunk, xx, y + 1, zz)) return false;
            if (isAir(chunk, xx, y - 1, zz)) return false;
            int sx = alongX ? 0 : 1;
            int sz = alongX ? 1 : 0;
            if (isAir(chunk, xx + sx, y, zz + sz) || isAir(chunk, xx - sx, y, zz - sz)) return false;
        }
        return true;
    }

    private static boolean isAir(LevelChunk c, int x, int y, int z) {
        return c.getBlockState(new BlockPos(x, y, z)).isAir();
    }

    private static boolean isCrafted(Block b) {
        return b == Blocks.OAK_PLANKS || b == Blocks.SPRUCE_PLANKS || b == Blocks.BIRCH_PLANKS
            || b == Blocks.STONE_BRICKS || b == Blocks.DEEPSLATE_BRICKS || b == Blocks.POLISHED_DEEPSLATE
            || b == Blocks.CRAFTING_TABLE || b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE
            || b == Blocks.SMOKER || b == Blocks.ENCHANTING_TABLE || b == Blocks.ANVIL
            || b == Blocks.REDSTONE_WIRE || b == Blocks.REPEATER || b == Blocks.COMPARATOR
            || b == Blocks.PISTON || b == Blocks.STICKY_PISTON || b == Blocks.TNT
            || b == Blocks.CRAFTER || b == Blocks.GLOWSTONE || b == Blocks.REDSTONE_LAMP;
    }

    private static boolean isWorkstation(Block b) {
        return b == Blocks.FURNACE || b == Blocks.SMOKER || b == Blocks.BLAST_FURNACE
            || b == Blocks.CRAFTER || b == Blocks.BREWING_STAND || b == Blocks.LECTERN
            || b == Blocks.ENCHANTING_TABLE || b == Blocks.BEEHIVE;
    }

    private static boolean isTrail(Block b) {
        return b == Blocks.TORCH || b == Blocks.WALL_TORCH || b == Blocks.SOUL_TORCH
            || b == Blocks.LANTERN || b == Blocks.LADDER || b == Blocks.RAIL
            || b == Blocks.POWERED_RAIL || b == Blocks.DETECTOR_RAIL || b == Blocks.ACTIVATOR_RAIL
            || b == Blocks.SCAFFOLDING;
    }

    public List<Marker> nearestStorage(int max, double x, double z) {
        if (!config.enabled(PixelConfig.Module.STORAGE_MARKERS)) return List.of();
        return markersByChunk.values().stream().flatMap(List::stream)
            .filter(m -> m.dimension.equals(dimension()))
            .sorted(Comparator.comparingDouble(m -> m.distanceFrom(x, z)))
            .limit(max).toList();
    }

    public List<Lead> top(int max) {
        if (!config.enabled(PixelConfig.Module.SUS_CHUNKS)) return List.of();
        return leads.values().stream()
            .filter(l -> l.dimension.equals(dimension()) && l.score(config) >= config.threshold())
            .sorted(Comparator.comparingInt((Lead l) -> l.score(config)).reversed())
            .limit(max).toList();
    }

    public void trackBest() {
        List<Lead> choices = top(1);
        tracked = choices.isEmpty() ? null : choices.get(0);
    }
    public void clearTrack() { tracked = null; }

    public Path exportCsv() throws Exception {
        Path out = FabricLoader.getInstance().getConfigDir().resolve("pixelclient")
            .resolve("finds-" + LocalDate.now() + ".csv");
        Files.createDirectories(out.getParent());
        StringBuilder data = new StringBuilder(header());
        for (Lead l : leads.values()) data.append(row(l));
        Files.writeString(out, data.toString(), StandardCharsets.UTF_8);
        return out;
    }

    private void append(Lead l) {
        try {
            Path out = FabricLoader.getInstance().getConfigDir().resolve("pixelclient")
                .resolve("auto-finds-" + LocalDate.now() + ".csv");
            Files.createDirectories(out.getParent());
            if (!Files.exists(out)) Files.writeString(out, header(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
            Files.writeString(out, row(l), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            PixelClient.LOG.warn("Could not write local CSV", e);
        }
    }
    private static String header() {
        return "dimension,chunk_x,chunk_z,center_x,center_z,score,storage,shulkers,spawners,build_blocks,workstations,portals,trails,tunnels,low_y\n";
    }
    private String row(Lead l) {
        return String.join(",", l.dimension, Integer.toString(l.chunkX), Integer.toString(l.chunkZ),
            Integer.toString(l.x()), Integer.toString(l.z()), Integer.toString(l.score(config)),
            Integer.toString(l.storage), Integer.toString(l.shulkers), Integer.toString(l.spawners),
            Integer.toString(l.buildBlocks), Integer.toString(l.workstations), Integer.toString(l.portals),
            Integer.toString(l.trails), Integer.toString(l.tunnels), Integer.toString(l.lowestY)) + "\n";
    }
}
