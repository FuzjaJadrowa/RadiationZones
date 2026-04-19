package pl.fuzjajadrowa.radiationzones.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class RadiationServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<RadiationServerConfig>() {}.getType();

    private boolean enableCommands = true;
    private final Map<String, SafeZone> safeZonesByDimension = new LinkedHashMap<>();
    private String lugolColor = "#197d14";

    public static RadiationServerConfig loadOrCreate() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("radiationzones-server.json");

        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                RadiationServerConfig config = GSON.fromJson(reader, TYPE);
                if (config != null) {
                    return config;
                }
            } catch (IOException ignored) {
            }
        }

        RadiationServerConfig config = new RadiationServerConfig();
        config.save();
        return config;
    }

    public synchronized void save() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("radiationzones-server.json");

        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(this, TYPE, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public synchronized boolean isEnableCommands() {
        return this.enableCommands;
    }

    public synchronized void setSafeZone(String dimensionId, BlockPos center, int radius) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(center, "center");
        this.safeZonesByDimension.put(dimensionId, new SafeZone(center.getX(), center.getY(), center.getZ(), radius));
        this.save();
    }

    public synchronized boolean clearSafeZone(String dimensionId) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        boolean removed = this.safeZonesByDimension.remove(dimensionId) != null;
        if (removed) {
            this.save();
        }
        return removed;
    }

    public synchronized SafeZone getSafeZone(String dimensionId) {
        return this.safeZonesByDimension.get(dimensionId);
    }

    public synchronized String getLugolColor() {
        return this.lugolColor;
    }

    public record SafeZone(int x, int y, int z, int radius) {
    }
}