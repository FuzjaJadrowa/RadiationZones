package pl.fuzjajadrowa.radiationzones.nms;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import pl.fuzjajadrowa.radiationzones.lugolsiodine.LugolsIodinePotion;

public interface RadiationNmsBridge {
    void registerLugolsIodinePotion(NamespacedKey potionKey, LugolsIodinePotion.Config.Recipe config);

    void unregisterLugolsIodinePotion(NamespacedKey potionKey);

    int getMinWorldHeight(World bukkitWorld);
}