# MineGen

<img src="assets/minegen-icon.png" width="160" height="160" alt="MineGen terrain block">

**Standalone terrain generator for Paper 1.21.11 · Java 21**

MineGen creates a dedicated Minecraft world using the terrain engine used by Eternium. Install one plugin, start your server, and explore with `/minegen tp`.

[Download MineGen 1.0.0](https://github.com/PrivacyOnion/MineGen/releases/tag/v1.0.0) · [Report an issue](https://github.com/PrivacyOnion/MineGen/issues)

## Features

- Custom terrain, surface, caves and bedrock generation.
- Server-managed decorations, ores, structures and mob spawning.
- Automatic creation and loading of a separate `minegen` world.
- Configurable world name, seed, structures and difficulty.
- Optional first-join teleportation for new players.
- Paper-specific optimizations with a Bukkit fallback when internal hooks are unavailable.

Your existing main world is preserved. No additional plugin is required.

## Installation

1. Stop your **Paper 1.21.11** server running **Java 21**.
2. Download `MineGen-1.0.0.jar` from Releases and place it in `plugins/`. Keep only one MineGen JAR.
3. Start the server. MineGen creates its configuration and a world named `minegen`.
4. As a server operator, run `/minegen tp`.

This is a **server plugin**. Players do not need a client mod. Spigot, Folia and other Minecraft versions are not advertised as compatible.

## Commands

| Command | Purpose |
| --- | --- |
| `/minegen status` | Display the managed world's status. |
| `/minegen tp` | Teleport to the world's spawn. |
| `/mg` | Alias for `/minegen`. |

`minegen.teleport` grants teleport access and defaults to operators. With optional LuckPerms: `lp group default permission set minegen.teleport true`.

## Configuration

See [the example configuration](release/config.yml). Stop the server before editing, then restart it.

Choose the seed and structures before creating a world. Changing the configured seed does not regenerate an existing world; choose an unused world name for a different seed. Preserve the generated `internal` configuration alongside the world: it records ownership of the managed folder.

Back up worlds and configuration before upgrading. Use a full restart rather than `/reload`.

## Validation and limitations

The existing [validation report](release/VALIDATION.txt) records tests from September 12, 2026 on Windows, Java 21.0.11 and official Paper 1.21.11 build 132. It covers installation, restarts, custom seeds, permissions and client teleportation. It is not a load-test report or a guarantee for every seed and plugin combination. The Bukkit fallback and first-join client flow were not specifically forced in that test pass.

No heavy benchmark or full-world pregeneration runs automatically. No performance multiplier is claimed.

## Source and builds

- `java/src/`: terrain engine, development bootstrap and diagnostic tools.
- `java/src_nms/`: optional server internals hooks.
- `release/src/`: standalone distribution bootstrap.
- `release/`: configuration, packaging scripts and historical validation.
- `docs/`: Modrinth copy and release notes.
- `assets/`: SVG and PNG branding for GitHub, with provenance notes.

The historical build scripts depend on external Minecraft data, Paper libraries and local workspace paths. **A reproducible, self-contained release build is not currently provided.** `build.sh` builds the development plugin; `release/build.ps1` wraps a separately validated engine. Use the tested JAR attached to the release for installation. Compiled outputs and server/player data are excluded from source control.

## Credits and usage

**JMS — project creator. ChatGPT — assistance with publication, documentation and GitHub branding.** See [CREDITS.md](CREDITS.md).

No open-source license has been selected. Public source visibility alone does not grant a general reuse or redistribution license. Third-party components and Minecraft-derived data remain subject to their respective terms. MineGen is not affiliated with Mojang or Microsoft.
