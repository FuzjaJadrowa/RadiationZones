package pl.fuzjajadrowa.radiationzones.radiation;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

public class RadiationEvent extends Event implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return getHandlerList();
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    private final Player player;
    private final Radiation radiation;
    private boolean cancel;
    private boolean showWarning = true;

    public RadiationEvent(Player player, Radiation radiation) {
        this.player = Objects.requireNonNull(player, "player");
        this.radiation = Objects.requireNonNull(radiation, "radiation");
    }

    @Override
    public boolean isCancelled() {
        return this.cancel;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;

        if (cancel) {
            this.showWarning = false;
        }
    }

    public Player getPlayer() {
        return this.player;
    }

    public Radiation getRadiation() {
        return this.radiation;
    }

    public boolean shouldShowWarning() {
        return this.showWarning;
    }

    public void setShowWarning(boolean showWarning) {
        this.showWarning = showWarning;
    }
}