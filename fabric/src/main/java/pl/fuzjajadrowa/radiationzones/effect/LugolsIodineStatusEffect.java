package pl.fuzjajadrowa.radiationzones.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public final class LugolsIodineStatusEffect extends StatusEffect {
    public LugolsIodineStatusEffect(int color) {
        super(StatusEffectCategory.BENEFICIAL, color);
    }
}