package pl.fuzjajadrowa.radiationzones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.fuzjajadrowa.radiationzones.config.RadiationServerConfig;

public final class RadiationConfigScreen extends Screen {
    private final Screen parent;

    private boolean enableCommands;
    private EditBox intervalField;
    private Component status = Component.empty();

    public RadiationConfigScreen(Screen parent) {
        super(Component.literal("Radiation Zones Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        RadiationServerConfig config = RadiationServerConfig.loadOrCreate();
        this.enableCommands = config.isEnableCommands();

        int centerX = this.width / 2;
        int y = this.height / 2 - 30;

        this.addRenderableWidget(Button.builder(this.toggleLabel(), button -> {
            this.enableCommands = !this.enableCommands;
            button.setMessage(this.toggleLabel());
        }).bounds(centerX - 100, y, 200, 20).build());

        this.intervalField = new EditBox(this.font, centerX - 100, y + 28, 200, 20, Component.literal("Radiation check interval"));
        this.intervalField.setValue(Integer.toString(config.getRadiationCheckIntervalTicks()));
        this.addRenderableWidget(this.intervalField);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.saveConfig())
                .bounds(centerX - 100, y + 56, 98, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .bounds(centerX + 2, y + 56, 98, 20)
                .build());
    }

    private void saveConfig() {
        int interval;
        try {
            interval = Integer.parseInt(this.intervalField.getValue().trim());
        } catch (NumberFormatException ignored) {
            this.status = Component.literal("Interval must be a positive integer.");
            return;
        }

        if (interval <= 0) {
            this.status = Component.literal("Interval must be greater than 0.");
            return;
        }

        RadiationServerConfig config = RadiationServerConfig.loadOrCreate();
        config.setEnableCommands(this.enableCommands);
        config.setRadiationCheckIntervalTicks(interval);
        config.save();
        this.onClose();
    }

    private Component toggleLabel() {
        return Component.literal("Enable commands: " + (this.enableCommands ? "ON" : "OFF"));
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 58, 0xFFFFFF);
        graphics.drawString(this.font, Component.literal("Radiation check interval (ticks):"), this.width / 2 - 100, this.height / 2 - 2, 0xA0A0A0);
        if (!this.status.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height / 2 + 84, 0xFF5555);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}