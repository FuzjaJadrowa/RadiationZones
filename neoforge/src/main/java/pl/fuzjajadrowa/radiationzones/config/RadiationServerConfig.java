package pl.fuzjajadrowa.radiationzones.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RadiationServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<RadiationServerConfig>() {}.getType();

    private boolean enableCommands = true;
    private int radiationCheckIntervalTicks = 20;

    private Messages messages = new Messages();
    private Lugol lugol = new Lugol();
    private RadiationBar radiationBar = new RadiationBar();

    private final List<EffectSpec> radiationEffects = new ArrayList<>();
    private final Map<String, SafeZone> safeZonesByDimension = new LinkedHashMap<>();

    public RadiationServerConfig() {
        if (this.radiationEffects.isEmpty()) {
            this.radiationEffects.add(new EffectSpec("minecraft:wither", 4, false, false, false));
            this.radiationEffects.add(new EffectSpec("minecraft:hunger", 0, false, false, false));
        }
    }

    public static RadiationServerConfig loadOrCreate() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("radiationzones-server.json");

        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                RadiationServerConfig config = GSON.fromJson(reader, TYPE);
                if (config != null) {
                    config.normalize();
                    return config;
                }
            } catch (IOException ignored) {
            }
        }

        RadiationServerConfig config = new RadiationServerConfig();
        config.save();
        return config;
    }

    private void normalize() {
        if (this.messages == null) {
            this.messages = new Messages();
        }
        if (this.lugol == null) {
            this.lugol = new Lugol();
        }
        if (this.radiationBar == null) {
            this.radiationBar = new RadiationBar();
        }
        if (this.radiationCheckIntervalTicks <= 0) {
            this.radiationCheckIntervalTicks = 20;
        }
        if (this.radiationEffects.isEmpty()) {
            this.radiationEffects.add(new EffectSpec("minecraft:wither", 4, false, false, false));
            this.radiationEffects.add(new EffectSpec("minecraft:hunger", 0, false, false, false));
        }
    }

    public synchronized void save() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("radiationzones-server.json");

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

    public synchronized int getRadiationCheckIntervalTicks() {
        return this.radiationCheckIntervalTicks;
    }

    public synchronized Messages getMessages() {
        return this.messages;
    }

    public synchronized Lugol getLugol() {
        return this.lugol;
    }

    public synchronized RadiationBar getRadiationBar() {
        return this.radiationBar;
    }

    public synchronized List<EffectSpec> getRadiationEffects() {
        return List.copyOf(this.radiationEffects);
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

    public record Messages(
            boolean broadcastDrink,
            boolean broadcastEnter,
            String drinkTemplate,
            String enterTemplate
    ) {
        public Messages() {
            this(false, false, "%player% drank Lugol's iodine.", "%player% entered radiation zone in %dimension%.");
        }
    }

    public record Lugol(
            String color,
            int durationSeconds,
            Recipe recipe
    ) {
        public Lugol() {
            this("#197d14", 600, new Recipe());
        }
    }

    public record Recipe(
            String basePotion,
            String ingredient
    ) {
        public Recipe() {
            this("minecraft:thick", "minecraft:ghast_tear");
        }
    }

    public record RadiationBar(
            boolean enabled,
            String color,
            String style,
            String title
    ) {
        public RadiationBar() {
            this(true, "red", "progress", "Radiation Zone");
        }
    }

    public record EffectSpec(
            String effect,
            int amplifier,
            boolean ambient,
            boolean showParticles,
            boolean showIcon
    ) {
    }

    public record SafeZone(int x, int y, int z, int radius) {
        public boolean containsHorizontal(int px, int pz) {
            return Math.abs(px - this.x) <= this.radius && Math.abs(pz - this.z) <= this.radius;
        }
    }
}