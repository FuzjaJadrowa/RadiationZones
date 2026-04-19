package pl.fuzjajadrowa.radiationzones.radiation;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.ChatColor;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import pl.fuzjajadrowa.radiationzones.RadiationZones;
import pl.fuzjajadrowa.radiationzones.nms.RadiationNmsBridge;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;

public class Radiation implements Listener {
    static final Logger logger = Logger.getLogger(Radiation.class.getName());

    private final Set<UUID> affectedPlayers = new HashSet<>(128);

    private final Plugin plugin;
    private final Matcher matcher;
    private final Config config;

    private BossBar bossBar;
    private Task task;

    public Radiation(Plugin plugin, Matcher matcher, Config config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.config = Objects.requireNonNull(config, "config");
    }

    public void enable() {
        Server server = this.plugin.getServer();
        this.bossBar = this.config.bar().create(server, ChatColor.DARK_RED);
        this.task = new Task();
        this.task.runTaskTimer(this.plugin, 20L, 20L);
        server.getPluginManager().registerEvents(this, this.plugin);
    }

    public void disable() {
        HandlerList.unregisterAll(this);

        if (this.task != null) {
            this.task.cancel();
        }

        if (this.bossBar != null) {
            this.bossBar.removeAll();
        }

        this.affectedPlayers.clear();
    }

    private void addBossBar(Player player) {
        this.bossBar.addPlayer(player);
    }

    private void broadcastEnter(Player player) {
        String id = this.config.id();
        logger.info(player.getName() + " has entered '" + id + "' radiation zone at " + player.getLocation());

        this.config.enterMessage().ifPresent(rawMessage -> {
            String message = ChatColor.RED + MessageFormat.format(rawMessage, player.getDisplayName() + ChatColor.RESET, id);
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
        boolean ok = this.affectedPlayers.remove(player.getUniqueId());
        if (removeBossBar) {
            this.bossBar.removePlayer(player);
        }

        return ok;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.removeAffectedPlayer(event.getPlayer(), true);
    }

    class Task extends BukkitRunnable {
        @Override
        public void run() {
            Server server = plugin.getServer();
            Iterable<PotionEffect> effects = config.effects();

            server.getOnlinePlayers().forEach(player -> {
                if (matcher.test(player)) {
                    RadiationEvent event = new RadiationEvent(player, Radiation.this);
                    server.getPluginManager().callEvent(event);

                    boolean showBossBar = event.shouldShowWarning();
                    boolean cancelled = event.isCancelled();
                    boolean alreadyOnBar = bossBar.getPlayers().contains(player);

                    if (!cancelled) {
                        for (PotionEffect effect : effects) {
                            player.addPotionEffect(effect, true);
                        }
                        affectedPlayers.add(player.getUniqueId());
                    }

                    if (showBossBar) {
                        addBossBar(player);
                        if (!alreadyOnBar) {
                            broadcastEnter(player);
                        }
                    } else {
                        bossBar.removePlayer(player);
                    }
                } else {
                    removeAffectedPlayer(player, true);
                }
            });
        }
    }

    public interface Matcher extends Predicate<Player> {
    }

    public interface WorldGuardMatcher extends Matcher {
        @Override
        default boolean test(Player player) {
            RegionContainer regionContainer = this.getRegionContainer();
            return regionContainer != null && this.test(player, regionContainer);
        }

        default RegionContainer getRegionContainer() {
            WorldGuardPlatform platform = WorldGuard.getInstance().getPlatform();
            return platform != null ? platform.getRegionContainer() : null;
        }

        boolean test(Player player, RegionContainer regionContainer);
    }

    public static class FlagMatcher implements WorldGuardMatcher {
        private final RadiationNmsBridge nmsBridge;
        private final Flag<Boolean> isRadioactiveFlag;
        private final Flag<String> radiationTypeFlag;
        private final Set<String> acceptedRadiationTypes;

        public FlagMatcher(RadiationNmsBridge nmsBridge, Flag<Boolean> isRadioactiveFlag, Flag<String> radiationTypeFlag, Set<String> acceptedRadiationTypes) {
            this.nmsBridge = Objects.requireNonNull(nmsBridge, "nmsBridge");
            this.isRadioactiveFlag = Objects.requireNonNull(isRadioactiveFlag, "isRadioactiveFlag");
            this.radiationTypeFlag = Objects.requireNonNull(radiationTypeFlag, "radiationTypeFlag");
            this.acceptedRadiationTypes = Objects.requireNonNull(acceptedRadiationTypes, "acceptedRadiationTypes");
        }

        @Override
        public boolean test(Player player, RegionContainer regionContainer) {
            org.bukkit.Location bukkitLocation = player.getLocation();
            World world = player.getWorld();
            int minY = this.nmsBridge.getMinWorldHeight(world);
            int maxY = world.getMaxHeight() - 1;

            Location location = BukkitAdapter.adapt(bukkitLocation);
            location = location.setY(Math.max(minY, Math.min(maxY, location.getY())));

            ApplicableRegionSet regions = regionContainer.createQuery().getApplicableRegions(location);
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);

            Boolean radioactive = regions.queryValue(localPlayer, this.isRadioactiveFlag);
            if (!Boolean.TRUE.equals(radioactive)) {
                return false;
            }

            String radiationId = regions.queryValue(localPlayer, this.radiationTypeFlag);
            if (radiationId == null || radiationId.isBlank()) {
                radiationId = Config.DEFAULT_ID;
            }

            Permission permission = new Permission("radiationzones.immune." + radiationId, PermissionDefault.FALSE);
            if (player.hasPermission(permission)) {
                return false;
            }

            return this.acceptedRadiationTypes.contains(radiationId);
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