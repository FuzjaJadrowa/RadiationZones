package pl.fuzjajadrowa.radiationzones.lugolsiodine;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;
import pl.fuzjajadrowa.radiationzones.RadiationZones;
import pl.fuzjajadrowa.radiationzones.nms.RadiationNmsBridge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LugolsIodinePotion implements Listener, Predicate<ItemStack> {
    static final Logger logger = Logger.getLogger(LugolsIodinePotion.class.getName());

    private static final byte TRUE = 1;

    private final Plugin plugin;
    private final LugolsIodineEffect effect;
    private final Config config;

    private NamespacedKey potionIdKey;
    private NamespacedKey radiationIdsKey;
    private NamespacedKey durationSecondsKey;
    private NamespacedKey legacyPotionKey;
    private NamespacedKey legacyDurationKey;

    private NamespacedKey recipeKey;

    public LugolsIodinePotion(Plugin plugin, LugolsIodineEffect effect, Config config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.effect = Objects.requireNonNull(effect, "effect");
        this.config = Objects.requireNonNull(config, "config");
    }

    public void enable(RadiationNmsBridge nmsBridge) {
        this.potionIdKey = new NamespacedKey(this.plugin, "lugols_iodine_id");
        this.radiationIdsKey = new NamespacedKey(this.plugin, "radiation_ids");
        this.durationSecondsKey = new NamespacedKey(this.plugin, "duration_seconds");
        this.legacyPotionKey = new NamespacedKey(this.plugin, "lugols_iodine");
        this.legacyDurationKey = new NamespacedKey(this.plugin, "duration");

        Config.Recipe recipeConfig = this.config.recipe();
        if (recipeConfig.enabled()) {
            this.recipeKey = new NamespacedKey(this.plugin, "lugols_iodine_" + this.config.id().toLowerCase(Locale.ROOT));
            nmsBridge.registerLugolsIodinePotion(this.recipeKey, recipeConfig);
        }

        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    public void disable(RadiationNmsBridge nmsBridge) {
        HandlerList.unregisterAll(this);
        if (this.config.recipe().enabled() && this.recipeKey != null) {
            nmsBridge.unregisterLugolsIodinePotion(this.recipeKey);
        }
    }

    public Duration getDuration() {
        return this.config.duration();
    }

    @Override
    public boolean test(ItemStack itemStack) {
        if (!itemStack.hasItemMeta()) {
            return false;
        }

        String id = this.config.id();
        PersistentDataContainer container = Objects.requireNonNull(itemStack.getItemMeta()).getPersistentDataContainer();

        if (id.equals(Config.DEFAULT_ID) && container.has(this.legacyPotionKey, PersistentDataType.BYTE)) {
            Byte value = container.get(this.legacyPotionKey, PersistentDataType.BYTE);
            return value != null && value == TRUE;
        }

        if (container.has(this.potionIdKey, PersistentDataType.STRING)) {
            return id.equals(container.get(this.potionIdKey, PersistentDataType.STRING));
        }

        return false;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!this.test(item)) {
            return;
        }

        Player player = event.getPlayer();
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return;
        }

        PersistentDataContainer container = itemMeta.getPersistentDataContainer();

        List<String> radiationIds = null;
        if (container.has(this.radiationIdsKey, PersistentDataType.BYTE_ARRAY)) {
            byte[] bytes = container.get(this.radiationIdsKey, PersistentDataType.BYTE_ARRAY);
            if (bytes != null && bytes.length != 0) {
                try {
                    radiationIds = this.readRadiationIds(bytes);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Could not read radiation IDs from bytes on '" + player.getName() + "'.", e);
                    return;
                }
            }
        }

        Duration duration = Duration.ZERO;
        if (container.has(this.durationSecondsKey, PersistentDataType.INTEGER)) {
            duration = Duration.ofSeconds(container.getOrDefault(this.durationSecondsKey, PersistentDataType.INTEGER, 0));
        } else if (container.has(this.legacyDurationKey, PersistentDataType.INTEGER)) {
            duration = Duration.ofMinutes(container.getOrDefault(this.legacyDurationKey, PersistentDataType.INTEGER, 0));
        }

        if (duration.isNegative() || duration.isZero()) {
            return;
        }

        try {
            this.effect.appendEffect(player, new LugolsIodineEffect.Effect(this.getId(), duration, radiationIds));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not set lugol's iodine potion effect on '" + player.getName() + "'.", e);
            return;
        }

        this.broadcastConsumption(player, duration);
    }

    private void broadcastConsumption(Player player, Duration duration) {
        String name = this.config.name();
        logger.info(player.getName() + " has consumed " + name + " with a duration of " + duration.getSeconds() + " seconds");

        this.config.drinkMessage().ifPresent(rawMessage -> {
            String message = ChatColor.RED + applyAliases(rawMessage, player.getDisplayName() + ChatColor.RESET, name, null);
            for (Player online : this.plugin.getServer().getOnlinePlayers()) {
                if (online.canSee(player)) {
                    online.sendMessage(message);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Config.Recipe recipeConfig = this.config.recipe();
        if (!recipeConfig.enabled()) {
            return;
        }

        BrewerInventory inventory = event.getContents();
        BrewingStandWindow window = BrewingStandWindow.fromArray(inventory.getContents());

        if (window.ingredient == null || !window.ingredient.getType().equals(recipeConfig.ingredient())) {
            return;
        }

        boolean[] modified = new boolean[BrewingStandWindow.SLOTS];

        for (int i = 0; i < BrewingStandWindow.SLOTS; i++) {
            ItemStack result = window.results[i];
            if (result == null) {
                continue;
            }

            ItemMeta itemMeta = result.getItemMeta();
            if (!(itemMeta instanceof PotionMeta potionMeta)) {
                continue;
            }

            PotionType baseType = potionMeta.getBasePotionType();
            if (baseType != recipeConfig.basePotion()) {
                continue;
            }

            try {
                result.setItemMeta(this.convert(potionMeta));
                modified[i] = true;
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not transform potion to lugol's iodine.", e);
            }
        }

        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            for (int i = 0; i < BrewingStandWindow.SLOTS; i++) {
                if (!modified[i]) {
                    continue;
                }

                ItemStack[] contents = inventory.getContents();
                contents[i] = window.getResult(i);
                inventory.setContents(contents);
            }
        });
    }

    public ItemStack createItemStack(int amount) throws IOException {
        ItemStack itemStack = new ItemStack(Material.POTION, amount);
        PotionMeta potionMeta = (PotionMeta) Objects.requireNonNull(itemStack.getItemMeta());
        potionMeta.setBasePotionType(this.config.recipe().basePotion());
        itemStack.setItemMeta(this.convert(potionMeta));
        return itemStack;
    }

    public PotionMeta convert(PotionMeta potionMeta) throws IOException {
        Duration duration = this.getDuration();
        String formattedDuration = formatDuration(duration);

        this.config.color().ifPresent(potionMeta::setColor);
        potionMeta.addItemFlags(ItemFlag.HIDE_ITEM_SPECIFICS);
        potionMeta.setDisplayName(ChatColor.AQUA + this.config.name());
        String description = applyAliases(this.config.description(), null, null, formattedDuration);
        potionMeta.setLore(Collections.singletonList(ChatColor.BLUE + description));

        PersistentDataContainer container = potionMeta.getPersistentDataContainer();
        container.set(this.potionIdKey, PersistentDataType.STRING, this.config.id());

        List<String> radiationIds = this.config.radiationIds();
        if (radiationIds != null && !radiationIds.isEmpty()) {
            byte[] bytes = this.writeRadiationIds(radiationIds);
            if (bytes.length != 0) {
                container.set(this.radiationIdsKey, PersistentDataType.BYTE_ARRAY, bytes);
            }
        }

        container.set(this.durationSecondsKey, PersistentDataType.INTEGER, (int) duration.getSeconds());
        return potionMeta;
    }

    public String getId() {
        return this.config.id();
    }

    private List<String> readRadiationIds(byte[] bytes) throws IOException {
        List<String> radiationIds = new ArrayList<>();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             DataInputStream dis = new DataInputStream(bais)) {
            int count = dis.readInt();
            for (int i = 0; i < count; i++) {
                radiationIds.add(dis.readUTF());
            }
        }

        return radiationIds;
    }

    private byte[] writeRadiationIds(List<String> radiationIds) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(radiationIds.size());
            for (String radiationId : radiationIds) {
                dos.writeUTF(radiationId);
            }

            return baos.toByteArray();
        }
    }

    static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        long secondsLeft = seconds - (TimeUnit.MINUTES.toSeconds(minutes));

        return (minutes < 10 ? "0" : "") + minutes + ":" + (secondsLeft < 10 ? "0" : "") + secondsLeft;
    }

    private static String applyAliases(String input, String player, String mixture, String time) {
        String output = input;
        if (player != null) {
            output = output.replace("%player%", player);
        }
        if (mixture != null) {
            output = output.replace("%mixture%", mixture);
        }
        if (time != null) {
            output = output.replace("%time%", time);
        }
        return output;
    }

    static class BrewingStandWindow {
        static final int SLOTS = 3;

        final ItemStack ingredient;
        final ItemStack fuel;
        final ItemStack[] results;

        BrewingStandWindow(ItemStack ingredient, ItemStack fuel, ItemStack[] results) {
            this.ingredient = ingredient;
            this.fuel = fuel;
            this.results = Objects.requireNonNull(results, "results");

            if (results.length != SLOTS) {
                throw new IllegalArgumentException(results.length + " array length, expected " + SLOTS);
            }
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", BrewingStandWindow.class.getSimpleName() + "[", "]")
                    .add("ingredient=" + ingredient)
                    .add("fuel=" + fuel)
                    .add("results=" + Arrays.toString(results))
                    .toString();
        }

        static BrewingStandWindow fromArray(ItemStack[] contents) {
            if (contents.length != 5) {
                throw new IllegalArgumentException("length is " + contents.length + ", expected 5");
            }

            ItemStack ingredient = contents[3];
            ItemStack fuel = contents[4];
            return new BrewingStandWindow(ingredient, fuel, Arrays.copyOfRange(contents, 0, 3));
        }

        ItemStack getResult(int index) {
            return this.results[index];
        }
    }

    public static class Config {
        public static final String DEFAULT_ID = "default";

        private final String id;
        private final Recipe recipe;
        private final String name;
        private final Color color;
        private final String description;
        private final List<String> radiationIds;
        private final Duration duration;
        private final String drinkMessage;

        public Config(ConfigurationSection section) throws InvalidConfigurationException {
            if (section == null) {
                section = new MemoryConfiguration();
            }

            String configuredId = section.getName();
            this.id = configuredId.isEmpty() ? DEFAULT_ID : configuredId;
            this.recipe = new Recipe(section.getConfigurationSection("recipe"));
            this.name = Objects.requireNonNull(section.getString("name", "Lugol's Iodine"));

            String colorHex = section.getString("color", null);
            try {
                this.color = colorHex == null || colorHex.isEmpty() ? null : Color.fromRGB(
                        Integer.parseInt(colorHex.substring(1, 3), 16),
                        Integer.parseInt(colorHex.substring(3, 5), 16),
                        Integer.parseInt(colorHex.substring(5, 7), 16)
                );
            } catch (NumberFormatException | StringIndexOutOfBoundsException exception) {
                throw new InvalidConfigurationException("Invalid potion color.", exception);
            }

            this.description = Objects.requireNonNull(section.getString("description", "Radiation resistance (%time%)"));

            List<String> configuredRadiationIds = section.getStringList("radiation-ids");
            this.radiationIds = configuredRadiationIds.isEmpty() ? null : configuredRadiationIds;

            this.duration = Duration.ofSeconds(section.getInt("duration", 600));

            String drinkMessage = RadiationZones.colorize(section.getString("drink-message"));
            this.drinkMessage = drinkMessage != null && !drinkMessage.isEmpty() ? drinkMessage : null;

            if (this.duration.isZero() || this.duration.isNegative()) {
                throw new InvalidConfigurationException("Given potion duration must be positive.");
            }
        }

        public String id() {
            return this.id;
        }

        public Recipe recipe() {
            return this.recipe;
        }

        public String name() {
            return this.name;
        }

        public Optional<Color> color() {
            return Optional.ofNullable(this.color);
        }

        public String description() {
            return this.description;
        }

        public List<String> radiationIds() {
            return this.radiationIds;
        }

        public Duration duration() {
            return this.duration;
        }

        public Optional<String> drinkMessage() {
            return Optional.ofNullable(this.drinkMessage);
        }

        public static class Recipe {
            public static final Material DEFAULT_INGREDIENT = Material.GHAST_TEAR;
            public static final PotionType DEFAULT_BASE_POTION = PotionType.THICK;

            private final boolean enabled;
            private final PotionType basePotion;
            private final Material ingredient;

            public Recipe(ConfigurationSection section) throws InvalidConfigurationException {
                if (section == null) {
                    section = new MemoryConfiguration();
                }

                this.enabled = section.getBoolean("enabled", true);

                String ingredientInput = Objects.requireNonNull(section.getString("ingredient", DEFAULT_INGREDIENT.getKey().getKey()));
                this.ingredient = Material.matchMaterial(ingredientInput);
                if (this.ingredient == null) {
                    throw new InvalidConfigurationException("Invalid recipe ingredient name: " + ingredientInput);
                }

                String basePotionInput = Objects.requireNonNull(section.getString("base-potion", DEFAULT_BASE_POTION.name())).toUpperCase(Locale.ROOT);
                try {
                    this.basePotion = PotionType.valueOf(basePotionInput);
                } catch (IllegalArgumentException exception) {
                    throw new InvalidConfigurationException("Invalid recipe base potion name: " + basePotionInput, exception);
                }
            }

            public boolean enabled() {
                return this.enabled;
            }

            public PotionType basePotion() {
                return this.basePotion;
            }

            public Material ingredient() {
                return this.ingredient;
            }
        }
    }
}