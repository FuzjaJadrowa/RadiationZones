package pl.fuzjajadrowa.radiationzones.nms;

import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.PotionMeta;
import io.papermc.paper.potion.PotionMix;
import pl.fuzjajadrowa.radiationzones.lugolsiodine.LugolsIodinePotion;

import java.util.Objects;

public class PaperNmsBridge implements RadiationNmsBridge {
    private final Server server;

    public PaperNmsBridge(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public void registerLugolsIodinePotion(NamespacedKey potionKey, LugolsIodinePotion.Config.Recipe config) {
        ItemStack input = new ItemStack(org.bukkit.Material.POTION);
        PotionMeta inputMeta = (PotionMeta) input.getItemMeta();
        if (inputMeta == null) {
            return;
        }

        inputMeta.setBasePotionType(config.basePotion());
        input.setItemMeta(inputMeta);

        ItemStack output = input.clone();
        RecipeChoice ingredient = new RecipeChoice.MaterialChoice(config.ingredient());
        RecipeChoice base = new RecipeChoice.ExactChoice(input);

        this.server.getPotionBrewer().addPotionMix(new PotionMix(potionKey, output, base, ingredient));
    }

    @Override
    public void unregisterLugolsIodinePotion(NamespacedKey potionKey) {
        this.server.getPotionBrewer().removePotionMix(potionKey);
    }

    @Override
    public int getMinWorldHeight(World bukkitWorld) {
        return bukkitWorld.getMinHeight();
    }
}