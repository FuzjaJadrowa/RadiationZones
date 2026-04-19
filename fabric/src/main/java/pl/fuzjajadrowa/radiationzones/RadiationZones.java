package pl.fuzjajadrowa.radiationzones;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import pl.fuzjajadrowa.radiationzones.config.RadiationServerConfig;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class RadiationZones implements ModInitializer {
    private final RadiationServerConfig config = RadiationServerConfig.loadOrCreate();

    @Override
    public void onInitialize() {
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
        this.config.setSafeZone(dimensionId, BlockPos.ofFloored(source.getPosition()), radius);
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
}