package pl.fuzjajadrowa.radiationzones.lugolsiodine;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import pl.fuzjajadrowa.radiationzones.radiation.Radiation;
import pl.fuzjajadrowa.radiationzones.radiation.RadiationEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LugolsIodineEffect implements Listener {
    static final Logger logger = Logger.getLogger(LugolsIodineEffect.class.getName());

    private static final Duration TASK_PERIOD = Duration.ofSeconds(1);

    private NamespacedKey entityStorageKey;
    private NamespacedKey legacyInitialSecondsKey;
    private NamespacedKey legacySecondsLeftKey;

    private final Plugin plugin;
    private Task task;

    public LugolsIodineEffect(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void enable() {
        this.entityStorageKey = new NamespacedKey(this.plugin, "effect_data");
        this.legacyInitialSecondsKey = new NamespacedKey(this.plugin, "initial_seconds");
        this.legacySecondsLeftKey = new NamespacedKey(this.plugin, "seconds_left");

        this.task = new Task();
        this.task.runTaskTimer(this.plugin, 0L, TASK_PERIOD.toSeconds() * 20L);

        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        if (this.task != null) {
            this.task.cancel();
        }
    }

    public void appendEffect(Entity entity, Effect effect) throws IOException {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        List<Effect> effectList = new ArrayList<>(this.readEffects(container));
        ListIterator<Effect> iterator = effectList.listIterator();

        AtomicBoolean replaced = new AtomicBoolean();
        while (iterator.hasNext()) {
            Effect next = iterator.next();
            if (next.getId().equals(effect.getId())) {
                iterator.set(this.merge(next, effect));
                replaced.set(true);
                break;
            }
        }

        if (!replaced.get()) {
            effectList.add(effect);
        }

        this.writeEffects(container, effectList);
    }

    public List<Effect> getEffects(Entity entity) throws IOException {
        this.migrateLegacyEffect(entity);

        return this.readEffects(entity.getPersistentDataContainer()).stream()
                .filter(effect -> !effect.getTimeLeft().isNegative())
                .toList();
    }

    public void removeAllEffects(Entity entity) {
        this.removeAllEffects(entity.getPersistentDataContainer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMilkBucketConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.MILK_BUCKET) {
            this.removeAllEffects(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        this.removeAllEffects(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemOfUndyingUse(EntityResurrectEvent event) {
        this.removeAllEffects(event.getEntity());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRadiation(RadiationEvent event) {
        Player player = event.getPlayer();
        Radiation radiation = event.getRadiation();

        List<Effect> effects;
        try {
            effects = this.getEffects(player);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not get lugol's iodine effects on '" + player.getName() + "'.", e);
            return;
        }

        for (Effect effect : effects) {
            if (effect.canEnter(radiation)) {
                event.setCancelled(true);
                event.setShowWarning(true);
                break;
            }
        }
    }

    private Effect merge(Effect existing, Effect replacement) {
        String id = existing.id;
        Duration initialDuration = replacement.initialDuration;
        Duration timeLeft = Duration.ofMillis(Math.max(existing.timeLeft.toMillis(), replacement.timeLeft.toMillis()));
        List<String> radiationIds = replacement.radiationIds;

        return new Effect(id, initialDuration, timeLeft, radiationIds);
    }

    private void migrateLegacyEffect(Entity entity) throws IOException {
        PersistentDataContainer container = entity.getPersistentDataContainer();

        int initialSeconds;
        int secondsLeft;
        try {
            initialSeconds = container.getOrDefault(this.legacyInitialSecondsKey, PersistentDataType.INTEGER, -1);
            secondsLeft = container.getOrDefault(this.legacySecondsLeftKey, PersistentDataType.INTEGER, -1);
        } finally {
            container.remove(this.legacyInitialSecondsKey);
            container.remove(this.legacySecondsLeftKey);
        }

        if (initialSeconds == -1 || secondsLeft <= -1) {
            return;
        }

        String id = "__legacy_effect__";
        Duration initialDuration = Duration.ofSeconds(initialSeconds);
        Duration timeLeft = Duration.ofSeconds(secondsLeft);
        List<String> radiationIds = null;

        Effect effect = new Effect(id, initialDuration, timeLeft, radiationIds);
        this.appendEffect(entity, effect);
    }

    private List<Effect> readEffects(PersistentDataContainer container) throws IOException {
        if (!container.has(this.entityStorageKey, PersistentDataType.BYTE_ARRAY)) {
            return Collections.emptyList();
        }

        byte[] bytes = container.get(this.entityStorageKey, PersistentDataType.BYTE_ARRAY);
        if (bytes == null || bytes.length == 0) {
            return Collections.emptyList();
        }

        List<Effect> effectList = new ArrayList<>();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             DataInputStream dis = new DataInputStream(bais)) {
            short protocolVersion = dis.readShort();
            if (protocolVersion != 0) {
                throw new IOException("Unsupported protocol version: " + protocolVersion);
            }

            int effectListCount = dis.readInt();
            for (int i = 0; i < effectListCount; i++) {
                String id = dis.readUTF();
                Duration initialDuration = Duration.ofMillis(dis.readLong());
                Duration timeLeft = Duration.ofMillis(dis.readLong());
                List<String> radiationIds = null;

                int radiationIdCount = dis.readInt();
                for (int j = 0; j < radiationIdCount; j++) {
                    if (radiationIds == null) {
                        radiationIds = new ArrayList<>();
                    }
                    radiationIds.add(dis.readUTF());
                }

                effectList.add(new Effect(id, initialDuration, timeLeft, radiationIds));
            }
        }

        return effectList;
    }

    private void writeEffects(PersistentDataContainer container, List<Effect> effectList) throws IOException {
        if (effectList == null || effectList.isEmpty()) {
            this.removeAllEffects(container);
            return;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeShort(0);
            dos.writeInt(effectList.size());

            for (Effect effect : effectList) {
                dos.writeUTF(effect.id);
                dos.writeLong(effect.initialDuration.toMillis());
                dos.writeLong(effect.timeLeft.toMillis());

                if (effect.radiationIds == null) {
                    dos.writeInt(0);
                } else {
                    dos.writeInt(effect.radiationIds.size());
                    for (String radiationId : effect.radiationIds) {
                        dos.writeUTF(radiationId);
                    }
                }
            }

            container.set(this.entityStorageKey, PersistentDataType.BYTE_ARRAY, baos.toByteArray());
        }
    }

    private void removeAllEffects(PersistentDataContainer container) {
        container.remove(this.entityStorageKey);
        container.remove(this.legacyInitialSecondsKey);
        container.remove(this.legacySecondsLeftKey);
    }

    public static class Effect {
        private final String id;
        private final Duration initialDuration;
        private final Duration timeLeft;
        private final List<String> radiationIds;

        public Effect(String id, Duration duration, List<String> radiationIds) {
            this(id, duration, duration, radiationIds);
        }

        public Effect(String id, Duration initialDuration, Duration timeLeft, List<String> radiationIds) {
            this.id = Objects.requireNonNull(id, "id");
            this.initialDuration = initialDuration;
            this.timeLeft = timeLeft;
            this.radiationIds = radiationIds;
        }

        public String getId() {
            return this.id;
        }

        public Duration getInitialDuration() {
            return this.initialDuration;
        }

        public Duration getTimeLeft() {
            return this.timeLeft;
        }

        public Effect timePassed(Duration timePassed) {
            Duration newTimeLeft = this.timeLeft.minus(timePassed);
            return new Effect(this.id, this.initialDuration, newTimeLeft, this.radiationIds);
        }

        public boolean canEnter(Radiation radiation) {
            if (this.radiationIds == null) {
                return true;
            }

            return this.radiationIds.contains(radiation.getId());
        }
    }

    class Task extends BukkitRunnable {
        @Override
        public void run() {
            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                try {
                    this.tick(onlinePlayer, TASK_PERIOD);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Could not tick effects on player '" + onlinePlayer.getName() + "'.", e);
                }
            }
        }

        private void tick(Player player, Duration timePassed) throws IOException {
            List<Effect> effectList = getEffects(player).stream()
                    .map(effect -> effect.timePassed(timePassed))
                    .collect(Collectors.toList());

            writeEffects(player.getPersistentDataContainer(), effectList);
        }
    }
}