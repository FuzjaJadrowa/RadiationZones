package pl.fuzjajadrowa.radiationzones;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pl.fuzjajadrowa.radiationzones.config.RadiationServerConfig;
import pl.fuzjajadrowa.radiationzones.effect.LugolsIodineMobEffect;
import pl.fuzjajadrowa.radiationzones.util.ColorUtil;
import net.neoforged.fml.loading.FMLPaths;

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

@Mod(RadiationZones.MOD_ID)
public final class RadiationZones {
    public static final String MOD_ID = "radiationzones";
    private static final int EXIT_FADE_SECONDS = 5;
    private static final RadiationServerConfig CONFIG = RadiationServerConfig.loadOrCreate(FMLPaths.CONFIGDIR.get());

    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, MOD_ID);
    private static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(BuiltInRegistries.POTION, MOD_ID);

    private static final DeferredHolder<MobEffect, MobEffect> LUGOLS_EFFECT = EFFECTS.register(
            "lugols_iodine",
            () -> new LugolsIodineMobEffect(ColorUtil.parseHexColor(CONFIG.getLugol().color(), 0x197d14))
    );

    private static final DeferredHolder<Potion, Potion> LUGOLS_POTION = POTIONS.register(
            "lugols_iodine",
            () -> new Potion(new MobEffectInstance(LUGOLS_EFFECT, CONFIG.getLugol().durationSeconds() * 20, 0, false, true, true))
    );

    private final Set<UUID> playersInRadiation = new HashSet<>();
    private final Set<UUID> affectedPlayers = new HashSet<>();
    private final Set<UUID> hadLugolEffect = new HashSet<>();
    private final Map<UUID, Integer> exitFadeByPlayer = new HashMap<>();
    private final Map<UUID, ServerBossEvent> barsByPlayer = new HashMap<>();

    public RadiationZones(IEventBus modBus) {
        EFFECTS.register(modBus);
        POTIONS.register(modBus);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onRegisterBrewingRecipes);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerDeath);
        NeoForge.EVENT_BUS.addListener(this::onPlayerRespawn);
    }

    private void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        this.registerBrewingRecipe(event);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("radiation")
                .executes(context -> {
                    if (!CONFIG.isEnableCommands()) {
                        context.getSource().sendSuccess(() -> Component.literal("Radiation commands are disabled in config."), false);
                        return 0;
                    }

                    context.getSource().sendSuccess(() -> Component.literal("Use: /radiation safe <radius> or /radiation clear"), false);
                    return 1;
                })
                .then(Commands.literal("safe")
                        .then(Commands.argument("radius", integer(1))
                                .executes(context -> this.handleSafe(context, getInteger(context, "radius")))))
                .then(Commands.literal("clear")
                        .executes(this::handleClear)));
    }

    private int handleSafe(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();

        if (!CONFIG.isEnableCommands()) {
            source.sendSuccess(() -> Component.literal("Radiation commands are disabled in config."), false);
            return 0;
        }

        ServerLevel level = source.getLevel();
        String dimensionId = level.dimension().location().toString();
        BlockPos center = BlockPos.containing(source.getPosition());
        CONFIG.setSafeZone(dimensionId, center.getX(), center.getY(), center.getZ(), radius);

        source.sendSuccess(() -> Component.literal("Saved safe zone for " + dimensionId + " with radius " + radius + "."), true);
        return 1;
    }

    private int handleClear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!CONFIG.isEnableCommands()) {
            source.sendSuccess(() -> Component.literal("Radiation commands are disabled in config."), false);
            return 0;
        }

        ServerLevel level = source.getLevel();
        String dimensionId = level.dimension().location().toString();
        boolean removed = CONFIG.clearSafeZone(dimensionId);

        if (removed) {
            source.sendSuccess(() -> Component.literal("Removed safe zone for " + dimensionId + "."), true);
        } else {
            source.sendSuccess(() -> Component.literal("No safe zone configured for " + dimensionId + "."), false);
        }

        return 1;
    }

    private void onServerTick(ServerTickEvent.Post event) {
        int interval = Math.max(1, CONFIG.getRadiationCheckIntervalTicks());
        if (event.getServer().getTickCount() % interval != 0) {
            return;
        }

        Set<UUID> online = new HashSet<>();
        List<ServerPlayer> players = event.getServer().getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            online.add(player.getUUID());
            this.tickPlayer(player, players);
        }

        this.playersInRadiation.removeIf(uuid -> !online.contains(uuid));
        this.affectedPlayers.removeIf(uuid -> !online.contains(uuid));
        this.hadLugolEffect.removeIf(uuid -> !online.contains(uuid));
        this.exitFadeByPlayer.entrySet().removeIf(entry -> !online.contains(entry.getKey()));
        this.barsByPlayer.entrySet().removeIf(entry -> !online.contains(entry.getKey()));
    }

    private void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            this.clearPlayerRadiationState(player, true);
        }
    }

    private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        this.clearPlayerRadiationState(event.getEntity(), true);
    }

    private void tickPlayer(ServerPlayer player, List<ServerPlayer> allPlayers) {
        if (!player.isAlive()) {
            this.clearPlayerRadiationState(player, true);
            return;
        }

        String dimensionId = player.level().dimension().location().toString();
        RadiationServerConfig.SafeZone safeZone = CONFIG.getSafeZone(dimensionId);
        UUID playerId = player.getUUID();

        boolean hasLugol = player.hasEffect(LUGOLS_EFFECT);
        boolean had = this.hadLugolEffect.contains(playerId);
        if (hasLugol) {
            this.hadLugolEffect.add(playerId);
            if (!had && CONFIG.getMessages().broadcastDrink()) {
                this.broadcast(allPlayers, this.replace(CONFIG.getMessages().drinkTemplate(), player, dimensionId));
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
        if (entering && CONFIG.getMessages().broadcastEnter()) {
            this.broadcast(allPlayers, this.replace(CONFIG.getMessages().enterTemplate(), player, dimensionId));
        }

        if (!hasLugol) {
            for (MobEffectInstance effect : this.buildRadiationEffects()) {
                player.addEffect(effect);
            }
        }

        this.showRadiationBar(player, 1.0F);
    }

    private void showRadiationBar(ServerPlayer player, float progress) {
        RadiationServerConfig.RadiationBar barConfig = CONFIG.getRadiationBar();
        if (!barConfig.enabled()) {
            this.removeBar(player);
            return;
        }

        ServerBossEvent bar = this.barsByPlayer.computeIfAbsent(player.getUUID(), ignored ->
                new ServerBossEvent(this.createBarTitle(barConfig.title()), this.parseBarColor(barConfig.color()), this.parseBarOverlay(barConfig.style())));

        bar.setName(this.createBarTitle(barConfig.title()));
        bar.setColor(this.parseBarColor(barConfig.color()));
        bar.setOverlay(this.parseBarOverlay(barConfig.style()));
        this.applyBarFlags(bar, barConfig);
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, progress)));

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    private void removeBar(ServerPlayer player) {
        ServerBossEvent bar = this.barsByPlayer.remove(player.getUUID());
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    private void clearPlayerRadiationState(ServerPlayer player, boolean clearEffects) {
        UUID playerId = player.getUUID();
        this.playersInRadiation.remove(playerId);
        this.affectedPlayers.remove(playerId);
        this.hadLugolEffect.remove(playerId);
        this.exitFadeByPlayer.remove(playerId);
        this.removeBar(player);

        if (clearEffects) {
            for (MobEffectInstance effect : this.buildRadiationEffects()) {
                player.removeEffect(effect.getEffect());
            }
        }
    }

    private Component createBarTitle(String title) {
        return Component.literal(title).withStyle(ChatFormatting.DARK_RED);
    }

    private void applyBarFlags(ServerBossEvent bar, RadiationServerConfig.RadiationBar barConfig) {
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

        bar.setDarkenScreen(darkenSky);
        bar.setCreateWorldFog(createFog);
    }

    private List<MobEffectInstance> buildRadiationEffects() {
        List<MobEffectInstance> list = new ArrayList<>();
        for (RadiationServerConfig.EffectSpec spec : CONFIG.getRadiationEffects()) {
            ResourceLocation id = this.safeId(spec.effect());
            if (id == null) {
                continue;
            }

            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT
                    .getHolder(ResourceKey.create(Registries.MOB_EFFECT, id))
                    .orElse(null);
            if (effect == null) {
                continue;
            }

            list.add(new MobEffectInstance(effect, 100, Math.max(0, spec.amplifier()), spec.ambient(), spec.showParticles(), spec.showIcon()));
        }
        return list;
    }

    private void registerBrewingRecipe(RegisterBrewingRecipesEvent event) {
        RadiationServerConfig.Recipe recipe = CONFIG.getLugol().recipe();

        Holder<Potion> basePotion = this.resolvePotion(recipe.basePotion());
        Item ingredient = this.resolveItem(recipe.ingredient());

        event.getBuilder().addMix(basePotion, ingredient, LUGOLS_POTION);
    }

    private void broadcast(List<ServerPlayer> players, String message) {
        Component text = Component.literal(message);
        for (ServerPlayer online : players) {
            online.sendSystemMessage(text);
        }
    }

    private String replace(String template, ServerPlayer player, String dimensionId) {
        return template
                .replace("%player%", player.getName().getString())
                .replace("%dimension%", dimensionId);
    }

    private Holder<Potion> resolvePotion(String input) {
        ResourceLocation id = this.safeId(input);
        if (id == null) {
            return Potions.THICK;
        }

        Potion potion = BuiltInRegistries.POTION.get(id);
        return potion == null ? Potions.THICK : BuiltInRegistries.POTION.wrapAsHolder(potion);
    }

    private Item resolveItem(String input) {
        ResourceLocation id = this.safeId(input);
        if (id == null) {
            return Items.GHAST_TEAR;
        }

        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null ? Items.GHAST_TEAR : item;
    }

    private ResourceLocation safeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            if (raw.contains(":")) {
                return ResourceLocation.parse(raw);
            }
            return ResourceLocation.fromNamespaceAndPath("minecraft", raw.toLowerCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private BossEvent.BossBarColor parseBarColor(String input) {
        if (input == null) {
            return BossEvent.BossBarColor.RED;
        }

        return switch (input.toLowerCase(Locale.ROOT)) {
            case "pink" -> BossEvent.BossBarColor.PINK;
            case "blue" -> BossEvent.BossBarColor.BLUE;
            case "white" -> BossEvent.BossBarColor.WHITE;
            case "green" -> BossEvent.BossBarColor.GREEN;
            case "yellow" -> BossEvent.BossBarColor.YELLOW;
            case "purple" -> BossEvent.BossBarColor.PURPLE;
            default -> BossEvent.BossBarColor.RED;
        };
    }

    private BossEvent.BossBarOverlay parseBarOverlay(String input) {
        if (input == null) {
            return BossEvent.BossBarOverlay.PROGRESS;
        }

        return switch (input.toLowerCase(Locale.ROOT)) {
            case "notched_6", "segmented_6" -> BossEvent.BossBarOverlay.NOTCHED_6;
            case "notched_10", "segmented_10" -> BossEvent.BossBarOverlay.NOTCHED_10;
            case "notched_12", "segmented_12" -> BossEvent.BossBarOverlay.NOTCHED_12;
            case "notched_20", "segmented_20" -> BossEvent.BossBarOverlay.NOTCHED_20;
            default -> BossEvent.BossBarOverlay.PROGRESS;
        };
    }
}