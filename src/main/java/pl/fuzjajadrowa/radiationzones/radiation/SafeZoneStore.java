package pl.fuzjajadrowa.radiationzones.radiation;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SafeZoneStore {
    private static final Logger logger = Logger.getLogger(SafeZoneStore.class.getName());

    private final Plugin plugin;
    private final File file;
    private final Map<String, SafeZone> zonesByWorld = new LinkedHashMap<>();

    public SafeZoneStore(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = new File(this.plugin.getDataFolder(), "zones.yml");
    }

    public synchronized void load() {
        this.zonesByWorld.clear();
        if (!this.file.exists()) {
            this.save();
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
        if (!yaml.isConfigurationSection("worlds")) {
            return;
        }

        for (String worldName : Objects.requireNonNull(yaml.getConfigurationSection("worlds")).getKeys(false)) {
            String path = "worlds." + worldName;
            int x = yaml.getInt(path + ".x", Integer.MIN_VALUE);
            int z = yaml.getInt(path + ".z", Integer.MIN_VALUE);
            int radius = yaml.getInt(path + ".radius", -1);

            if (x == Integer.MIN_VALUE || z == Integer.MIN_VALUE || radius <= 0) {
                logger.warning("Skipping invalid safe zone entry for world '" + worldName + "' in zones.yml.");
                continue;
            }

            this.zonesByWorld.put(worldName, new SafeZone(worldName, x, z, radius));
        }
    }

    public synchronized void setZone(World world, int x, int z, int radius) {
        Objects.requireNonNull(world, "world");
        this.zonesByWorld.put(world.getName(), new SafeZone(world.getName(), x, z, radius));
        this.save();
    }

    public synchronized boolean hasZone(World world) {
        Objects.requireNonNull(world, "world");
        return this.zonesByWorld.containsKey(world.getName());
    }

    public synchronized boolean isInSafeZone(Location location) {
        Objects.requireNonNull(location, "location");
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        SafeZone safeZone = this.zonesByWorld.get(world.getName());
        return safeZone != null && safeZone.contains(location.getBlockX(), location.getBlockZ());
    }

    private synchronized void save() {
        FileConfiguration yaml = new YamlConfiguration();
        yaml.set("file-version-dont-touch", 1);

        for (Map.Entry<String, SafeZone> entry : this.zonesByWorld.entrySet()) {
            String worldName = entry.getKey();
            SafeZone zone = entry.getValue();
            String path = "worlds." + worldName;
            yaml.set(path + ".x", zone.centerX());
            yaml.set(path + ".z", zone.centerZ());
            yaml.set(path + ".radius", zone.radius());
        }

        try {
            yaml.save(this.file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save zones.yml.", e);
        }
    }

    private record SafeZone(String worldName, int centerX, int centerZ, int radius) {
        boolean contains(int x, int z) {
            return Math.abs(x - this.centerX) <= this.radius && Math.abs(z - this.centerZ) <= this.radius;
        }
    }
}