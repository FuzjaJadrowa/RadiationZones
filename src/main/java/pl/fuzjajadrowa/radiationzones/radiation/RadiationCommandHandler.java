package pl.fuzjajadrowa.radiationzones.radiation;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import pl.fuzjajadrowa.radiationzones.lugolsiodine.LugolsIodinePotion;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class RadiationCommandHandler implements CommandExecutor, TabCompleter {
    static final Logger logger = Logger.getLogger(RadiationCommandHandler.class.getName());

    private final Plugin plugin;
    private final SafeZoneStore safeZoneStore;
    private final Function<String, LugolsIodinePotion> potionFinder;
    private final Supplier<Spliterator<LugolsIodinePotion>> potionLister;

    public RadiationCommandHandler(
            Plugin plugin,
            SafeZoneStore safeZoneStore,
            Function<String, LugolsIodinePotion> potionFinder,
            Supplier<Spliterator<LugolsIodinePotion>> potionLister
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.safeZoneStore = Objects.requireNonNull(safeZoneStore, "safeZoneStore");
        this.potionFinder = Objects.requireNonNull(potionFinder, "potionFinder");
        this.potionLister = Objects.requireNonNull(potionLister, "potionLister");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "potion" -> this.onPotion(sender, label, args);
                case "safe" -> this.onSafe(sender, label, args);
                case "clear" -> this.onClear(sender);
                default -> this.sendUsage(sender, command.getUsage());
            };
        }

        return this.sendUsage(sender, command.getUsage());
    }

    private boolean sendUsage(CommandSender sender, String usage) {
        sender.sendMessage(ChatColor.RED + usage);
        return true;
    }

    private boolean onPotion(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players may execute /" + label + " potion.");
            return true;
        }

        String usage = ChatColor.RED + "/" + label + " potion <identifier>";
        if (args.length < 2) {
            String accessiblePotionIds = StreamSupport.stream(this.potionLister.get(), false)
                    .map(LugolsIodinePotion::getId)
                    .sorted()
                    .collect(Collectors.joining(", "));

            sender.sendMessage(ChatColor.RED + "Provide lugol's iodine potion identifier in the first argument.");
            sender.sendMessage(usage);
            sender.sendMessage(ChatColor.RED + "Example: /" + label + " potion default");
            sender.sendMessage(ChatColor.RED + "Accessible potions: " + accessiblePotionIds);
            return true;
        }

        String id = args[1];
        LugolsIodinePotion potion = this.potionFinder.apply(id);
        if (potion == null) {
            sender.sendMessage(ChatColor.RED + "Unknown lugol's iodine potion identifier: " + id);
            sender.sendMessage(usage);
            return true;
        }

        ItemStack itemStack;
        try {
            itemStack = potion.createItemStack(1);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not create potion item for '" + player.getName() + "'.", e);
            sender.sendMessage(ChatColor.RED + "An internal error has occurred while creating potion item. See console.");
            return true;
        }

        ItemMeta itemMeta = Objects.requireNonNull(itemStack.getItemMeta());

        if (player.getInventory().addItem(itemStack).isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "You have received " + itemStack.getAmount() + " " + itemMeta.getDisplayName());
        } else {
            sender.sendMessage(ChatColor.RED + "Your inventory is full!");
        }
        return true;
    }

    private boolean onSafe(CommandSender sender, String label, String[] args) {
        String usage = ChatColor.RED + "/" + label + " safe <radius>";
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Provide safe-from-radiation zone radius in the first argument.");
            sender.sendMessage(usage);
            return true;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Number was expected, but " + args[1] + " was provided.");
            sender.sendMessage(usage);
            return true;
        }

        if (radius <= 0) {
            sender.sendMessage(ChatColor.RED + "Radius must be positive.");
            sender.sendMessage(usage);
            return true;
        }

        World world;
        int centerX;
        int centerZ;

        if (sender instanceof Player player) {
            world = player.getWorld();
            centerX = player.getLocation().getBlockX();
            centerZ = player.getLocation().getBlockZ();
        } else {
            List<World> worlds = this.plugin.getServer().getWorlds();
            if (worlds.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No worlds are loaded.");
                return true;
            }

            world = worlds.get(0);
            centerX = 0;
            centerZ = 0;
        }

        this.safeZoneStore.setZone(world, centerX, centerZ, radius);
        sender.sendMessage(ChatColor.GREEN + "Saved safe zone in world '" + world.getName() + "' with center (" +
                centerX + ", " + centerZ + ") and radius " + radius + ".");
        return true;
    }

    private boolean onClear(CommandSender sender) {
        World world;
        if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            List<World> worlds = this.plugin.getServer().getWorlds();
            if (worlds.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No worlds are loaded.");
                return true;
            }
            world = worlds.get(0);
        }

        if (this.safeZoneStore.removeZone(world)) {
            sender.sendMessage(ChatColor.GREEN + "Removed safe zone in world '" + world.getName() + "'.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "No safe zone was configured in world '" + world.getName() + "'.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String subCommandInput = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("potion", "safe", "clear")
                    .filter(subCommand -> subCommand.startsWith(subCommandInput))
                    .toList();
        }

        return Collections.emptyList();
    }

    public void register(PluginCommand command) {
        if (command == null) {
            throw new IllegalStateException("Command 'radiation' is missing in plugin.yml.");
        }

        command.setExecutor(this);
        command.setTabCompleter(this);
    }
}