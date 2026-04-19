package pl.fuzjajadrowa.radiationzones.radiation;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import pl.fuzjajadrowa.radiationzones.RadiationZones;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BarConfig {
    private final String title;
    private final BarColor color;
    private final BarStyle style;
    private final BarFlag[] flags;

    public BarConfig(String title, BarColor color, BarStyle style, BarFlag[] flags) {
        this.title = Objects.requireNonNull(title, "title");
        this.color = Objects.requireNonNull(color, "color");
        this.style = Objects.requireNonNull(style, "style");
        this.flags = Objects.requireNonNull(flags, "flags");
    }

    public BarConfig(ConfigurationSection section) throws InvalidConfigurationException {
        if (section == null) {
            section = new MemoryConfiguration();
        }

        this.title = Objects.requireNonNull(RadiationZones.colorize(section.getString("title", "")));

        String color = section.getString("color", BarColor.WHITE.name());
        if (color == null) {
            throw new InvalidConfigurationException("Missing bar color.");
        }

        try {
            this.color = Objects.requireNonNull(BarColor.valueOf(color.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigurationException("Unknown bar color: " + color);
        }

        String style = section.getString("style", BarStyle.SOLID.name());
        if (style == null) {
            throw new InvalidConfigurationException("Missing bar style.");
        }

        try {
            this.style = Objects.requireNonNull(BarStyle.valueOf(style.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigurationException("Unknown bar style: " + style);
        }

        List<BarFlag> flags = new ArrayList<>();
        for (String flagName : section.getStringList("flags")) {
            try {
                BarFlag parsed = BarFlag.valueOf(flagName.toUpperCase());
                if (parsed == BarFlag.PLAY_BOSS_MUSIC) {
                    throw new InvalidConfigurationException("Unsupported bar flag: " + flagName);
                }
                flags.add(parsed);
            } catch (IllegalArgumentException e) {
                throw new InvalidConfigurationException("Unknown bar flag: " + flagName);
            }
        }
        this.flags = Objects.requireNonNull(flags.toArray(new BarFlag[0]));
    }

    public String title() {
        return this.title;
    }

    public BarColor color() {
        return this.color;
    }

    public BarStyle style() {
        return this.style;
    }

    public BarFlag[] flags() {
        return this.flags;
    }

    public BossBar create(Server server, ChatColor color) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(color, "color");

        return server.createBossBar(color + this.title(), this.color(), this.style(), this.flags());
    }
}