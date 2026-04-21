package pl.fuzjajadrowa.radiationzones;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import pl.fuzjajadrowa.radiationzones.config.RadiationServerConfig;
import pl.fuzjajadrowa.radiationzones.effect.LugolsIodineStatusEffect;
import pl.fuzjajadrowa.radiationzones.util.ColorUtil;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class RadiationZones implements ModInitializer {
    public static final String MOD_ID = "radiationzones";
    public static final Identifier LUGOLS_EFFECT_ID = Identifier.of(MOD_ID, "lugols_iodine");
    public static final Identifier LUGOLS_POTION_ID = Identifier.of(MOD_ID, "lugols_iodine");
    private static final int EXIT_FADE_SECONDS = 5;

    private RadiationServerConfig config;

    private RegistryEntry.Reference<StatusEffect> lugolsEffect;
    private RegistryEntry.Reference<Potion> lugolsPotion;

    private final Set<UUID> playersInRadiation = new HashSet<>();
    private final Set<UUID> affectedPlayers = new HashSet<>();
    private final Set<UUID> hadLugolEffect = new HashSet<>();
    private final Map<UUID, Integer> exitFadeByPlayer = new HashMap<>();
    private final Map<UUID, ServerBossBar> barsByPlayer = new HashMap<>();

    @Override
    public void onInitialize() {
        this.config = RadiationServerConfig.loadOrCreate(FabricLoader.getInstance().getConfigDir());

        this.lugolsEffect = Registry.registerReference(
                Registries.STATUS_EFFECT,
                LUGOLS_EFFECT_ID,
                new LugolsIodineStatusEffect(ColorUtil.parseHexColor(this.config.getLugol().color(), 0x197d14))
        );

        this.lugolsPotion = Registry.registerReference(
                Registries.POTION,
                LUGOLS_POTION_ID,
                new Potion(new StatusEffectInstance(this.lugolsEffect, this.config.getLugol().durationSeconds() * 20, 0, false, true, true))
        );

        this.registerBrewingRecipe();
        this.registerCommands();
        this.registerRadiationTicker();
        this.registerRespawnHandler();
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("radiation")
                    .executes(context -> {
                        if (!this.config.isEnableCommands()) {
                            context.getSource().sendFeedback(() -> Text.literal("Radiation commands are disabled in config."), false);
                            return 0;
                        }

                        context.getSource().sendFeedback(() -> Text.literal("Use: /radiation safe <radius> or /radiation clear"), false);
                        return 1;
                    })
                    .then(literal("safe")
                            .then(argument("radius", integer(1))
                                    .executes(context -> this.handleSafe(context.getSource(), getInteger(context, "radius")))))
                    .then(literal("clear")
                            .executes(context -> this.handleClear(context.getSource()))));
        });
    }

    private int handleSafe(ServerCommandSource source, int radius) {
        if (!this.config.isEnableCommands()) {
            source.sendFeedback(() -> Text.literal("Radiation commands are disabled in config."), false);
            return 0;
        }

        ServerWorld world = source.getWorld();
        String dimensionId = world.getRegistryKey().getValue().toString();
        BlockPos center = BlockPos.ofFloored(source.getPosition());
        this.config.setSafeZone(dimensionId, center.getX(), center.getY(), center.getZ(), radius);
        source.sendFeedback(() -> Text.literal("Saved safe zone for " + dimensionId + " with radius " + radius + "."), true);
        return 1;
    }

    private int handleClear(ServerCommandSource source) {
        if (!this.config.isEnableCommands()) {
            source.sendFeedback(() -> Text.literal("Radiation commands are disabled in config."), false);
            return 0;
        }

        ServerWorld world = source.getWorld();
        String dimensionId = world.getRegistryKey().getValue().toString();
        boolean removed = this.config.clearSafeZone(dimensionId);

        if (removed) {
            source.sendFeedback(() -> Text.literal("Removed safe zone for " + dimensionId + "."), true);
        } else {
            source.sendFeedback(() -> Text.literal("No safe zone configured for " + dimensionId + "."), false);
        }
        return 1;
    }

    private void registerBrewingRecipe() {
        RadiationServerConfig.Recipe recipe = this.config.getLugol().recipe();

        RegistryEntry.Reference<Potion> basePotion = this.resolvePotion(recipe.basePotion());
        Item ingredient = this.resolveItem(recipe.ingredient());
        Ingredient ingredientStack = Ingredient.ofItems(ingredient);

        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> builder.registerPotionRecipe(basePotion, ingredientStack, this.lugolsPotion));
    }

    private void registerRadiationTicker() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int interval = Math.max(1, this.config.getRadiationCheckIntervalTicks());
            if (server.getTicks() % interval != 0) {
                return;
            }

            Set<UUID> online = new HashSet<>();
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            for (ServerPlayerEntity player : players) {
                online.add(player.getUuid());
                this.tickPlayer(player, players);
            }

            // cleanup maps for players that left
            this.playersInRadiation.removeIf(uuid -> !online.contains(uuid));
            this.affectedPlayers.removeIf(uuid -> !online.contains(uuid));
            this.hadLugolEffect.removeIf(uuid -> !online.contains(uuid));
            this.exitFadeByPlayer.entrySet().removeIf(entry -> !online.contains(entry.getKey()));
            this.barsByPlayer.entrySet().removeIf(entry -> !online.contains(entry.getKey()));
        });
    }

    private void registerRespawnHandler() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> this.clearPlayerRadiationState(newPlayer, true));
    }

    private void tickPlayer(ServerPlayerEntity player, List<ServerPlayerEntity> allPlayers) {
        if (!player.isAlive()) {
            this.clearPlayerRadiationState(player, true);
            return;
        }

        String dimensionId = player.getWorld().getRegistryKey().getValue().toString();
        RadiationServerConfig.SafeZone safeZone = this.config.getSafeZone(dimensionId);
        UUID playerId = player.getUuid();

        boolean hasLugol = player.hasStatusEffect(this.lugolsEffect);
        boolean had = this.hadLugolEffect.contains(playerId);
        if (hasLugol) {
            this.hadLugolEffect.add(playerId);
            if (!had && this.config.getMessages().broadcastDrink()) {
                this.broadcast(allPlayers, this.replace(this.config.getMessages().drinkTemplate(), player, dimensionId));
            }
        } else {
            this.hadLugolEffect.remove(playerId);
        }

        if (safeZone == null) {
            this.playersInRadiation.remove(playerId);
            if (!this.affectedPlayers.contains(playerId)) {
                this.exitFadeByPlayer.remove(playerId);
                this.removeBar(player);
                return;
            }

            int remaining = this.exitFadeByPlayer.getOrDefault(playerId, EXIT_FADE_SECONDS - 1);
            if (remaining < 0) {
                this.affectedPlayers.remove(playerId);
                this.exitFadeByPlayer.remove(playerId);
                this.removeBar(player);
                return;
            }

            this.showRadiationBar(player, (float) remaining / EXIT_FADE_SECONDS);
            remaining--;
            this.exitFadeByPlayer.put(playerId, remaining);
            return;
        }

        boolean isInsideSafe = safeZone.containsHorizontal(player.getBlockX(), player.getBlockZ());
        boolean isInRadiationZone = !isInsideSafe;

        if (!isInRadiationZone) {
            this.playersInRadiation.remove(playerId);
            if (!this.affectedPlayers.contains(playerId)) {
                this.exitFadeByPlayer.remove(playerId);
                this.removeBar(player);
                return;
            }

            int remaining = this.exitFadeByPlayer.getOrDefault(playerId, EXIT_FADE_SECONDS - 1);
            if (remaining < 0) {
                this.affectedPlayers.remove(playerId);
                this.exitFadeByPlayer.remove(playerId);
                this.removeBar(player);
                return;
            }

            this.showRadiationBar(player, (float) remaining / EXIT_FADE_SECONDS);
            remaining--;
            this.exitFadeByPlayer.put(playerId, remaining);
            return;
        }

        boolean entering = this.playersInRadiation.add(playerId);
        this.affectedPlayers.add(playerId);
        this.exitFadeByPlayer.remove(playerId);
        if (entering && this.config.getMessages().broadcastEnter()) {
            this.broadcast(allPlayers, this.replace(this.config.getMessages().enterTemplate(), player, dimensionId));
        }

        if (!hasLugol) {
            for (StatusEffectInstance effect : this.buildRadiationEffects()) {
                player.addStatusEffect(effect);
            }
        }

        this.showRadiationBar(player, 1.0F);
    }

    private void showRadiationBar(ServerPlayerEntity player, float progress) {
        RadiationServerConfig.RadiationBar barConfig = this.config.getRadiationBar();
        if (!barConfig.enabled()) {
            this.removeBar(player);
            return;
        }

        ServerBossBar bar = this.barsByPlayer.computeIfAbsent(player.getUuid(), ignored ->
                new ServerBossBar(this.createBarTitle(barConfig.title()), this.parseBarColor(barConfig.color()), this.parseBarStyle(barConfig.style())));

        bar.setName(this.createBarTitle(barConfig.title()));
        bar.setColor(this.parseBarColor(barConfig.color()));
        bar.setStyle(this.parseBarStyle(barConfig.style()));
        this.applyBarFlags(bar, barConfig);
        bar.setPercent(Math.max(0.0F, Math.min(1.0F, progress)));

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    private void removeBar(ServerPlayerEntity player) {
        ServerBossBar bar = this.barsByPlayer.remove(player.getUuid());
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    private void clearPlayerRadiationState(ServerPlayerEntity player, boolean clearEffects) {
        UUID playerId = player.getUuid();
        this.playersInRadiation.remove(playerId);
        this.affectedPlayers.remove(playerId);
        this.hadLugolEffect.remove(playerId);
        this.exitFadeByPlayer.remove(playerId);
        this.removeBar(player);

        if (clearEffects) {
            for (StatusEffectInstance effect : this.buildRadiationEffects()) {
                player.removeStatusEffect(effect.getEffectType());
            }
        }
    }

    private Text createBarTitle(String title) {
        return Text.literal(title).formatted(Formatting.DARK_RED);
    }

    private void applyBarFlags(ServerBossBar bar, RadiationServerConfig.RadiationBar barConfig) {
        boolean darkenSky = false;
        boolean createFog = false;

        for (String flag : barConfig.flags()) {
            if (flag == null) {
                continue;
            }

            switch (flag.toLowerCase(Locale.ROOT)) {
                case "darken_sky" -> darkenSky = true;
                case "create_fog" -> createFog = true;
                default -> {
                }
            }
        }

        bar.setDarkenSky(darkenSky);
        bar.setThickenFog(createFog);
    }

    private List<StatusEffectInstance> buildRadiationEffects() {
        List<StatusEffectInstance> list = new ArrayList<>();
        for (RadiationServerConfig.EffectSpec spec : this.config.getRadiationEffects()) {
            Identifier id = this.safeId(spec.effect());
            if (id == null) {
                continue;
            }

            RegistryEntry<StatusEffect> effect = Registries.STATUS_EFFECT.getEntry(id).orElse(null);
            if (effect == null) {
                continue;
            }

            list.add(new StatusEffectInstance(effect, 100, Math.max(0, spec.amplifier()), spec.ambient(), spec.showParticles(), spec.showIcon()));
        }
        return list;
    }

    private void broadcast(List<ServerPlayerEntity> players, String message) {
        Text text = Text.literal(message);
        for (ServerPlayerEntity online : players) {
            online.sendMessage(text, false);
        }
    }

    private String replace(String template, ServerPlayerEntity player, String dimensionId) {
        return template
                .replace("%player%", player.getName().getString())
                .replace("%dimension%", dimensionId);
    }

    private RegistryEntry.Reference<Potion> resolvePotion(String input) {
        Identifier id = this.safeId(input);
        if (id == null) {
            return (RegistryEntry.Reference<Potion>) Potions.THICK;
        }

        return Registries.POTION.getEntry(id).orElse((RegistryEntry.Reference<Potion>) Potions.THICK);
    }

    private Item resolveItem(String input) {
        Identifier id = this.safeId(input);
        if (id == null) {
            return Items.GHAST_TEAR;
        }

        Item item = Registries.ITEM.get(id);
        return item == null ? Items.GHAST_TEAR : item;
    }

    private Identifier safeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        if (raw.contains(":")) {
            return Identifier.tryParse(raw);
        }

        return Identifier.tryParse("minecraft:" + raw.toLowerCase(Locale.ROOT));
    }

    private BossBar.Color parseBarColor(String input) {
        if (input == null) {
            return BossBar.Color.RED;
        }

        return switch (input.toLowerCase(Locale.ROOT)) {
            case "pink" -> BossBar.Color.PINK;
            case "blue" -> BossBar.Color.BLUE;
            case "white" -> BossBar.Color.WHITE;
            case "green" -> BossBar.Color.GREEN;
            case "yellow" -> BossBar.Color.YELLOW;
            case "purple" -> BossBar.Color.PURPLE;
            default -> BossBar.Color.RED;
        };
    }

    private BossBar.Style parseBarStyle(String input) {
        if (input == null) {
            return BossBar.Style.PROGRESS;
        }

        return switch (input.toLowerCase(Locale.ROOT)) {
            case "notched_6", "segmented_6" -> BossBar.Style.NOTCHED_6;
            case "notched_10", "segmented_10" -> BossBar.Style.NOTCHED_10;
            case "notched_12", "segmented_12" -> BossBar.Style.NOTCHED_12;
            case "notched_20", "segmented_20" -> BossBar.Style.NOTCHED_20;
            default -> BossBar.Style.PROGRESS;
        };
    }
}