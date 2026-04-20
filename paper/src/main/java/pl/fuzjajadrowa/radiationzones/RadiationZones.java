package pl.fuzjajadrowa.radiationzones;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import pl.fuzjajadrowa.radiationzones.lugolsiodine.LugolsIodineDisplay;
import pl.fuzjajadrowa.radiationzones.lugolsiodine.LugolsIodineEffect;
import pl.fuzjajadrowa.radiationzones.lugolsiodine.LugolsIodinePotion;
import pl.fuzjajadrowa.radiationzones.nms.PaperNmsBridge;
import pl.fuzjajadrowa.radiationzones.nms.RadiationNmsBridge;
import pl.fuzjajadrowa.radiationzones.radiation.BarConfig;
import pl.fuzjajadrowa.radiationzones.radiation.Radiation;
import pl.fuzjajadrowa.radiationzones.radiation.RadiationCommandHandler;
import pl.fuzjajadrowa.radiationzones.radiation.SafeZoneStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RadiationZones extends JavaPlugin {
    static final Logger logger = Logger.getLogger(RadiationZones.class.getName());

    private static final char COLOR_CODE = '&';
    private static final String CONFIG_VERSION_KEY = "file-version-dont-touch";
    private static final int CONFIG_VERSION = 1;

    private RadiationNmsBridge radiationNmsBridge;
    private Config config;
    private SafeZoneStore safeZoneStore;

    private LugolsIodineEffect effect;
    private LugolsIodineDisplay display;

    private final Map<String, LugolsIodinePotion> potions = new LinkedHashMap<>();
    private final Map<String, Radiation> activeRadiations = new LinkedHashMap<>();

    public static String colorize(String input) {
        return input == null ? null : ChatColor.translateAlternateColorCodes(COLOR_CODE, input);
    }

    @Override
    public void onEnable() {
        Server server = this.getServer();
        this.saveDefaultConfig();

        this.radiationNmsBridge = new PaperNmsBridge(server);

        FileConfiguration rawConfig = this.getConfig();
        int configVersion = this.resolveConfigVersion(rawConfig);
        if (!this.migrate(rawConfig, configVersion)) {
            this.setEnabled(false);
            return;
        }

        try {
            this.config = new Config(rawConfig);
        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Could not load configuration file.", e);
            this.setEnabled(false);
            return;
        }

        this.safeZoneStore = new SafeZoneStore(this);
        this.safeZoneStore.load();

        this.effect = new LugolsIodineEffect(this);
        this.display = new LugolsIodineDisplay(this, this.effect, this.config.lugolsIodineBars());

        for (LugolsIodinePotion.Config potionConfig : this.config.lugolsIodinePotions()) {
            this.potions.put(potionConfig.id(), new LugolsIodinePotion(this, this.effect, potionConfig));
        }

        for (Radiation.Config radiationConfig : this.config.radiations()) {
            String id = radiationConfig.id();
            if (!Radiation.Config.DEFAULT_ID.equals(id)) {
                logger.warning("Skipping unsupported radiation id '" + id + "'. Standalone mode currently supports only '" +
                        Radiation.Config.DEFAULT_ID + "'.");
                continue;
            }

            Radiation.Matcher matcher = new Radiation.SafeZoneMatcher(this.safeZoneStore);
            this.activeRadiations.put(id, new Radiation(this, matcher, radiationConfig));
        }

        RadiationCommandHandler commandHandler = new RadiationCommandHandler(
                this,
                this.safeZoneStore,
                this.potions::get,
                () -> this.potions.values().spliterator()
        );
        commandHandler.register(this.getCommand("radiation"));

        this.effect.enable();
        this.display.enable();

        this.potions.values().forEach(potion -> potion.enable(this.radiationNmsBridge));
        this.logLoaded("lugol's iodine potion", this.potions.keySet());

        this.activeRadiations.values().forEach(Radiation::enable);
        this.logLoaded("radiation", this.activeRadiations.keySet());
    }

    @Override
    public void onDisable() {
        this.activeRadiations.values().forEach(Radiation::disable);
        this.activeRadiations.clear();

        this.potions.values().forEach(potion -> potion.disable(this.radiationNmsBridge));
        this.potions.clear();

        if (this.display != null) {
            this.display.disable();
        }
        if (this.effect != null) {
            this.effect.disable();
        }
    }

    public LugolsIodineEffect getEffectHandler() {
        return this.effect;
    }

    public Map<String, LugolsIodinePotion> getPotionHandlers() {
        return Collections.unmodifiableMap(this.potions);
    }

    public Map<String, Radiation> getActiveRadiations() {
        return Collections.unmodifiableMap(this.activeRadiations);
    }

    private void logLoaded(String noun, Set<String> ids) {
        Set<String> sorted = new TreeSet<>(Comparator.naturalOrder());
        sorted.addAll(ids);
        logger.info("Loaded and enabled " + sorted.size() + " " + noun + "(s): " + String.join(", ", sorted));
    }

    private int resolveConfigVersion(ConfigurationSection section) {
        if (section.contains(CONFIG_VERSION_KEY)) {
            return section.getInt(CONFIG_VERSION_KEY, -1);
        }

        return -1;
    }

    private boolean migrate(ConfigurationSection section, int configVersion) {
        Objects.requireNonNull(section, "section");

        if (configVersion > CONFIG_VERSION) {
            logger.severe("Config version " + configVersion + " is newer than supported by this build.");
            return false;
        }

        if (configVersion < CONFIG_VERSION) {
            section.set(CONFIG_VERSION_KEY, CONFIG_VERSION);
            this.saveConfig();
        }
        return true;
    }

    public static class Config {
        private final Map<String, BarConfig> lugolsIodineBars;
        private final Iterable<LugolsIodinePotion.Config> lugolsIodinePotions;
        private final Iterable<Radiation.Config> radiations;

        public Config(ConfigurationSection section) throws InvalidConfigurationException {
            if (section == null) {
                section = new MemoryConfiguration();
            }

            this.lugolsIodineBars = this.readBars(section);
            this.lugolsIodinePotions = this.readPotions(section);
            this.radiations = this.readRadiations(section);
        }

        private Map<String, BarConfig> readBars(ConfigurationSection section) throws InvalidConfigurationException {
            if (!section.isConfigurationSection("lugols-iodine-bars")) {
                throw new InvalidConfigurationException("Missing lugols-iodine-bars section.");
            }

            Map<String, BarConfig> bars = new LinkedHashMap<>();
            ConfigurationSection barsSection = Objects.requireNonNull(section.getConfigurationSection("lugols-iodine-bars"));
            for (String key : barsSection.getKeys(false)) {
                if (!barsSection.isConfigurationSection(key)) {
                    throw new InvalidConfigurationException(key + " is not a lugols-iodine-bar section.");
                }
                bars.put(key, new BarConfig(barsSection.getConfigurationSection(key)));
            }

            return Collections.unmodifiableMap(bars);
        }

        private Iterable<LugolsIodinePotion.Config> readPotions(ConfigurationSection section) throws InvalidConfigurationException {
            if (!section.isConfigurationSection("lugols-iodine-potions")) {
                throw new InvalidConfigurationException("Missing lugols-iodine-potions section.");
            }

            List<LugolsIodinePotion.Config> potions = new ArrayList<>();
            ConfigurationSection potionsSection = Objects.requireNonNull(section.getConfigurationSection("lugols-iodine-potions"));
            for (String key : potionsSection.getKeys(false)) {
                if (!potionsSection.isConfigurationSection(key)) {
                    throw new InvalidConfigurationException(key + " is not a lugols-iodine-potion section.");
                }
                potions.add(new LugolsIodinePotion.Config(potionsSection.getConfigurationSection(key)));
            }

            return Collections.unmodifiableCollection(potions);
        }

        private Iterable<Radiation.Config> readRadiations(ConfigurationSection section) throws InvalidConfigurationException {
            if (!section.isConfigurationSection("radiations")) {
                throw new InvalidConfigurationException("Missing radiations section.");
            }

            List<Radiation.Config> radiations = new ArrayList<>();
            ConfigurationSection radiationsSection = Objects.requireNonNull(section.getConfigurationSection("radiations"));
            for (String key : radiationsSection.getKeys(false)) {
                if (!radiationsSection.isConfigurationSection(key)) {
                    throw new InvalidConfigurationException(key + " is not a radiation section.");
                }
                radiations.add(new Radiation.Config(radiationsSection.getConfigurationSection(key)));
            }

            return Collections.unmodifiableCollection(radiations);
        }

        public Map<String, BarConfig> lugolsIodineBars() {
            return this.lugolsIodineBars;
        }

        public Iterable<LugolsIodinePotion.Config> lugolsIodinePotions() {
            return this.lugolsIodinePotions;
        }

        public Iterable<Radiation.Config> radiations() {
            return this.radiations;
        }
    }
}