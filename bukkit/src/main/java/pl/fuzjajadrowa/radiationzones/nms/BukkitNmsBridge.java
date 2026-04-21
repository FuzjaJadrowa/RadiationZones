package pl.fuzjajadrowa.radiationzones.nms;

import org.bukkit.NamespacedKey;
import pl.fuzjajadrowa.radiationzones.lugolsiodine.LugolsIodinePotion;

public class BukkitNmsBridge implements RadiationNmsBridge {
    @Override
    public void registerLugolsIodinePotion(NamespacedKey potionKey, LugolsIodinePotion.Config.Recipe config) {
        // Bukkit/Spigot does not expose runtime custom potion mix registration like Paper.
        // Brewing support is handled by BrewEvent transformation in LugolsIodinePotion.
    }

    @Override
    public void unregisterLugolsIodinePotion(NamespacedKey potionKey) {
        // Nothing to unregister in Bukkit/Spigot mode.
    }
}