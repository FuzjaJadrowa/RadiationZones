![Radiation Zones](https://cdn.modrinth.com/data/cached_images/a9c60fb5045832654ece6f6cc43fcbee31ecaef1.png)

---

<div align="center">
  <p><strong>Radiation Zones</strong> adds configurable radiation zones to Minecraft. Players outside safe areas receive harmful effects unless protected by Lugol's iodine. Customizable with admin commands and advanced config available on <code>Paper</code>, <code>Fabric</code>, and <code>NeoForge</code>.</p>

  <p>
    <a href="https://github.com/FuzjaJadrowa/RadiationZones/actions/workflows/fabric-neoforge-build.yml"><img src="https://github.com/FuzjaJadrowa/RadiationZones/actions/workflows/fabric-neoforge-build.yml/badge.svg" alt="Fabric-Neoforge build" /></a>                                                                                                                                                                                                            
    <a href="https://github.com/FuzjaJadrowa/RadiationZones/actions/workflows/bukkit-build.yml"><img src="https://github.com/FuzjaJadrowa/RadiationZones/actions/workflows/bukkit-build.yml/badge.svg" alt="Bukkit build" /></a> 
  </p>
</div>

---

## How to install?
1. Ensure you are using correct version of the mod loader or plugin loader.
2. Drop the downloaded mod/plugin into your mods/plugins folder.
3. Launch the game. The config should create in `config/` folder.
## Features
- Radiation zones defined per dimension.
- Admin commands to set and clear safe zones.
- Harmful effects applied to players outside safe zones.
- Lugol's iodine (custom effect + potion) as radiation protection.
- Configurable boss bar warning inside radiation zones.
- Configurable broadcast messages (zone entry / Lugol consumption).
- Shared gameplay concept across: `paper`, `fabric`, `neoforge`.

## Commands (mod/plugin)
- `/radiation safe <radius>` - sets a safe zone in the current dimension around the command source position.
- `/radiation clear` - removes the safe zone in the current dimension.

Note: commands can be disabled in configuration (`enableCommands`).

## Configuration
- Fabric/NeoForge: `radiationzones-server.json` in the instance `config` directory.
- Paper: YAML configuration in the `paper` module (`config.yml`, `zones.yml`).

Configurable areas include:
- radiation check interval,
- radiation effect list,
- Lugol settings (color, duration, recipe),
- boss bar settings,
- broadcast toggles and message templates.

## Credits
- This project is a fork of **CraftserveRadiation**.
- The original project was released under the **Apache-2.0** license.
- The original developer is **Aleksander Jagiello**.

## License
This project is distributed under the **GPL-3.0** license.