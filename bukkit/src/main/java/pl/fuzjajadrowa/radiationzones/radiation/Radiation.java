package pl.fuzjajadrowa.radiationzones.radiation;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import pl.fuzjajadrowa.radiationzones.RadiationZones;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;

public class Radiation implements Listener {
    static final Logger logger = Logger.getLogger(Radiation.class.getName());
    private static final int EXIT_FADE_SECONDS = 5;

    private final Set<UUID> affectedPlayers = new HashSet<>(128);
    private final Set<UUID> playersInsideZone = new HashSet<>(128);
    private final Map<UUID, Integer> exitFadeByPlayer = new HashMap<>(128);
    private final Map<UUID, BossBar> bossBars = new HashMap<>(128);

    private final Plugin plugin;
    private final Matcher matcher;
    private final Config config;

    private Task task;

    public Radiation(Plugin plugin, Matcher matcher, Config config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.config = Objects.requireNonNull(config, "config");
    }

    public void enable() {
        this.task = new Task();
        this.task.runTaskTimer(this.plugin, 20L, 20L);
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    public void disable() {
        HandlerList.unregisterAll(this);

        if (this.task != null) {
            this.task.cancel();
        }

        this.bossBars.values().forEach(BossBar::removeAll);
        this.bossBars.clear();
        this.playersInsideZone.clear();
        this.exitFadeByPlayer.clear();
        this.affectedPlayers.clear();
    }

    private BossBar getOrCreateBossBar(Player player) {
        UUID playerId = player.getUniqueId();
        return this.bossBars.computeIfAbsent(playerId, ignored -> {
            BossBar bossBar = this.config.bar().create(this.plugin.getServer(), ChatColor.DARK_RED);
            bossBar.addPlayer(player);
            return bossBar;
        });
    }

    private void setBossBar(Player player, double progress) {
        BossBar bossBar = this.getOrCreateBossBar(player);
        if (!bossBar.getPlayers().contains(player)) {
            bossBar.addPlayer(player);
        }
        bossBar.setProgress(Math.max(0D, Math.min(1D, progress)));
    }

    private void removeBossBar(Player player) {
        BossBar bossBar = this.bossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    private void broadcastEnter(Player player) {
        String id = this.config.id();
        logger.info(player.getName() + " has entered '" + id + "' radiation zone at " + player.getLocation());

        this.config.enterMessage().ifPresent(rawMessage -> {
            String message = ChatColor.RED + rawMessage
                    .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                    .replace("%radiation%", id);
            for (Player online : this.plugin.getServer().getOnlinePlayers()) {
                if (online.canSee(player)) {
                    online.sendMessage(message);
                }
            }
        });
    }

    public Set<UUID> getAffectedPlayers() {
        return Collections.unmodifiableSet(this.affectedPlayers);
    }

    public String getId() {
        return this.config.id();
    }

    public boolean removeAffectedPlayer(Player player, boolean removeBossBar) {
        UUID playerId = player.getUniqueId();
        boolean changed = this.affectedPlayers.remove(playerId);
        this.playersInsideZone.remove(playerId);
        this.exitFadeByPlayer.remove(playerId);

        if (removeBossBar) {
            this.removeBossBar(player);
        }

        return changed;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.removeAffectedPlayer(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        this.removeAffectedPlayer(player, true);
        this.clearRadiationEffects(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        this.removeAffectedPlayer(player, true);
        this.clearRadiationEffects(player);
    }

    private void clearRadiationEffects(Player player) {
        for (PotionEffect effect : this.config.effects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    class Task extends BukkitRunnable {
        @Override
        public void run() {
            Server server = plugin.getServer();
            Iterable<PotionEffect> effects = config.effects();

            server.getOnlinePlayers().forEach(player -> {
                if (player.isDead() || player.getHealth() <= 0.0D) {
                    Radiation.this.removeAffectedPlayer(player, true);
                    Radiation.this.clearRadiationEffects(player);
                    return;
                }

                UUID playerId = player.getUniqueId();
                boolean inside = matcher.test(player);
                if (inside) {
                    boolean wasInside = playersInsideZone.contains(playerId);
                    playersInsideZone.add(playerId);
                    exitFadeByPlayer.remove(playerId);

                    RadiationEvent event = new RadiationEvent(player, Radiation.this);
                    server.getPluginManager().callEvent(event);

                    if (!event.isCancelled()) {
                        for (PotionEffect effect : effects) {
                            player.addPotionEffect(effect, true);
                        }
                        affectedPlayers.add(playerId);
                    } else {
                        affectedPlayers.remove(playerId);
                    }

                    if (event.shouldShowWarning()) {
                        setBossBar(player, 1D);
                        if (!wasInside) {
                            broadcastEnter(player);
                        }
                    } else {
                        removeBossBar(player);
                    }
                    return;
                }

                playersInsideZone.remove(playerId);
                if (!affectedPlayers.contains(playerId)) {
                    exitFadeByPlayer.remove(playerId);
                    removeBossBar(player);
                    return;
                }

                int remaining = exitFadeByPlayer.getOrDefault(playerId, EXIT_FADE_SECONDS - 1);
                if (remaining < 0) {
                    affectedPlayers.remove(playerId);
                    exitFadeByPlayer.remove(playerId);
                    removeBossBar(player);
                    return;
                }

                setBossBar(player, (double) remaining / EXIT_FADE_SECONDS);
                remaining--;
                exitFadeByPlayer.put(playerId, remaining);
            });
        }
    }

    public interface Matcher extends Predicate<Player> {
    }

    public static class SafeZoneMatcher implements Matcher {
        private final SafeZoneStore safeZoneStore;

        public SafeZoneMatcher(SafeZoneStore safeZoneStore) {
            this.safeZoneStore = Objects.requireNonNull(safeZoneStore, "safeZoneStore");
        }

        @Override
        public boolean test(Player player) {
            String radiationId = Config.DEFAULT_ID;
            Permission permission = new Permission("radiationzones.immune." + radiationId, PermissionDefault.FALSE);
            if (player.hasPermission(permission)) {
                return false;
            }

            Location location = player.getLocation();
            World world = location.getWorld();
            if (world == null || !this.safeZoneStore.hasZone(world)) {
                // Without any configured zone in a world, radiation is disabled there.
                return false;
            }

            return !this.safeZoneStore.isInSafeZone(location);
        }
    }

    public static class Config {
        public static final String DEFAULT_ID = "default";

        private final String id;
        private final BarConfig bar;
        private final Iterable<PotionEffect> effects;
        private final String enterMessage;

        public Config(ConfigurationSection section) throws InvalidConfigurationException {
            if (section == null) {
                section = new MemoryConfiguration();
            }

            String configuredId = section.getName();
            this.id = configuredId.isEmpty() ? DEFAULT_ID : configuredId;

            this.bar = new BarConfig(section.getConfigurationSection("bar"));
            this.effects = parseEffects(section.getConfigurationSection("effects"));

            String message = RadiationZones.colorize(section.getString("enter-message"));
            this.enterMessage = message == null || message.isEmpty() ? null : message;
        }

        private Iterable<PotionEffect> parseEffects(ConfigurationSection effectsSection) throws InvalidConfigurationException {
            if (effectsSection == null) {
                return Collections.emptyList();
            }

            List<PotionEffect> output = new ArrayList<>();
            for (String key : effectsSection.getKeys(false)) {
                ConfigurationSection effectSection = effectsSection.getConfigurationSection(key);
                if (effectSection == null) {
                    continue;
                }

                PotionEffectType type = PotionEffectType.getByName(key.toUpperCase(Locale.ROOT));
                if (type == null) {
                    throw new InvalidConfigurationException("Unknown effect type: " + key + '.');
                }

                int amplifier = Math.max(0, effectSection.getInt("level", 1) - 1);
                boolean ambient = effectSection.getBoolean("ambient", false);
                boolean hasParticles = effectSection.getBoolean("has-particles", false);
                boolean hasIcon = effectSection.getBoolean("has-icon", false);

                output.add(new PotionEffect(type, 20 * 5, amplifier, ambient, hasParticles, hasIcon));
            }

            return Collections.unmodifiableList(output);
        }

        public String id() {
            return this.id;
        }

        public BarConfig bar() {
            return this.bar;
        }

        public Iterable<PotionEffect> effects() {
            return this.effects;
        }

        public Optional<String> enterMessage() {
            return Optional.ofNullable(this.enterMessage);
        }
    }
}