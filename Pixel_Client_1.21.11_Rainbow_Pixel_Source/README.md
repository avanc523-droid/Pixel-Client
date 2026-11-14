# Pixel Client — Rainbow Pixel Edition

**Minecraft Java Edition 1.21.11 · Fabric · Java 21 · Source release**

A locally operating chunk-analysis and base-lead mod with an original, colorful, blocky platform-game-inspired Click GUI. Pixel Client is **not** affiliated with Nintendo, Corz, Gooba, Krypton, or 67 Client; it uses no assets or code from those clients.

## UI and customization

Press **Right Shift** in-game to open the rainbow pixel menu. The original in-game interface includes stepped/pixel borders, animated rainbow trim, pixel-art clouds and coins, a grass-and-dirt footer, accessible narrated buttons, and five pages:

- **Scanner** — toggles: chunk scanning, suspicious chunk ranking, storage, placed blocks, spawners, portals, tunnel hints, trail hints. Search module names/descriptions.
- **Radar + HUD** — toggles: radar, storage markers, Nether conversion, lead-list overlay, alerts, high sensitivity, automatic local CSV log.
- **Themes** — Rainbow (animated), Iris, Arctic, Viridian, Rose, Solar, Cobalt, Crimson, Monochrome, or Custom RGB. Custom RGB adjusts R/G/B channels in steps of 8. Toggle glow edges and pixel sparkles.
- **Tuning** — configurable scan radius, lead score threshold, menu scale, HUD scale, menu opacity, rainbow animation speed, HUD and radar corner positions.
- **Profiles** — save or load one of three configurations, export leads to CSV, track or clear top candidate.

Settings are persisted locally in `.minecraft/config/pixelclient.properties`; saved profiles are in `.minecraft/config/pixelclient/profiles/`. Existing AtlasHunter settings are not overwritten.

## Base finding

- Reads **only client-loaded chunk contents**, never asks a server for additional chunks or unseen data.
- Scans loaded block entities for chests, barrels, shulkers, droppers, hoppers, dispensers and some workstations.
- Samples blocks for player-like structure signs, redstone, portals, trail blocks and narrow air corridors.
- Assigns scores to suspicious chunks using multiple evidence types to help reduce false positives.
- Shows top candidate coordinates and a local 2D chunk radar; keeps exact positions for loaded storage containers.
- CSV exports and auto logs stay under `.minecraft/config/pixelclient/`, with auto logging **disabled by default**.

**Limitations:** This is heuristic only. Natural structures and abandoned mines can cause false positives; it cannot reveal unloaded bases, true chunk creation timestamps, player histories, or server-private information. No anti-cheat bypass or packet exploit is included. Server rules may prohibit mods like this.

## Build into an actual Fabric .jar

This download contains **source** because this execution environment cannot resolve/download the Fabric and Gradle artifacts needed to generate a runtime-tested remapped JAR. A renamed ZIP or manually compiled Java archive would not be a functioning Fabric mod. The included GitHub Actions workflow runs the real Loom remapping step.

**Option A — GitHub Actions (no local Gradle needed)**

1. Create a repository on GitHub. Upload the *contents* of this ZIP, including the hidden `.github/workflows/build.yml` path, to the repository.
2. Open **Actions → Build Pixel Client → Run workflow** (or let the push trigger the build).
3. When the build succeeds, open the workflow run's **Artifacts**, download `Pixel-Client-Fabric-1.21.11`, and extract `pixelclient-1.0.0.jar`.
4. Install Fabric Loader and Fabric API for Minecraft **1.21.11**. Copy `pixelclient-1.0.0.jar` into `%appdata%\.minecraft\mods` (Windows).

**Option B — Local build**

Install **Java 21** and **Gradle 9.2.1**, open a terminal in this project and run:

```sh
gradle clean build --no-daemon
```

The remapped output, on successful compilation, will be at `build/libs/pixelclient-1.0.0.jar`. Do **not** install `-sources.jar`.

The project's Java syntax was checked against Java 21; full Gradle dependency resolution and runtime testing in Minecraft remain unverified, so build errors may require fixes.

## Security design

This project contains no networking APIs, Discord token access, webhooks, updater, Minecraft authentication APIs, or browser/account file scanning. It does not send candidate base coordinates anywhere. The code is provided so you can inspect it and independently verify its build. No claim of absolute software safety is possible without a compiled-artifact audit.
