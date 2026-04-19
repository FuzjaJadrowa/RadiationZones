package pl.fuzjajadrowa.radiationzones.lugolsiodine;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import pl.fuzjajadrowa.radiationzones.RadiationZones;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MetricsHandler {
    static final Logger logger = Logger.getLogger(MetricsHandler.class.getName());

    private static final int B_STATS_PLUGIN_ID = 13487;

    private final RadiationZones plugin;
    private final Server server;

    public MetricsHandler(RadiationZones plugin, Server server) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
    }

    public void start() {
        Metrics metrics = new Metrics(this.plugin, B_STATS_PLUGIN_ID);
        this.setupBStatsCharts(metrics);
    }

    public void stop() {
        // bStats handles shutdown with plugin lifecycle.
    }

    private void setupBStatsCharts(Metrics metrics) {
        metrics.addCustomChart(new SimplePie("lugols_iodine_potion_count", () -> Integer.toString(this.plugin.getPotionHandlers().size())));

        metrics.addCustomChart(new SimplePie("lugols_iodine_duration", () -> {
            Duration average = Duration.ZERO;
            Map<String, LugolsIodinePotion> potionHandlers = this.plugin.getPotionHandlers();
            if (potionHandlers.isEmpty()) {
                return LugolsIodinePotion.formatDuration(average);
            }

            for (LugolsIodinePotion potion : potionHandlers.values()) {
                average = average.plus(potion.getDuration());
            }

            Duration duration = average.dividedBy(potionHandlers.size());
            return LugolsIodinePotion.formatDuration(duration);
        }));

        metrics.addCustomChart(new SingleLineChart("lugols_iodione_affected_count", () -> (int) this.server.getOnlinePlayers().stream()
                .filter(this::hasEffect)
                .count()));

        metrics.addCustomChart(new SimplePie("active_radiations_count", () -> Integer.toString(this.plugin.getActiveRadiations().size())));

        metrics.addCustomChart(new SingleLineChart("active_radiations_affected_count", () -> this.plugin.getActiveRadiations().values().stream()
                .mapToInt(radiation -> radiation.getAffectedPlayers().size())
                .sum()));

        metrics.addCustomChart(new SimplePie("platform", () -> "paper-1.21.1"));
    }

    private boolean hasEffect(Player player) {
        LugolsIodineEffect effectHandler = this.plugin.getEffectHandler();

        List<LugolsIodineEffect.Effect> effects;
        try {
            effects = effectHandler.getEffects(player);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not get lugol's iodine effect on '" + player.getName() + "'.", e);
            return false;
        }

        return !effects.isEmpty();
    }
}