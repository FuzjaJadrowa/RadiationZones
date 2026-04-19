package pl.fuzjajadrowa.radiationzones.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import pl.fuzjajadrowa.radiationzones.config.RadiationServerConfig;

public final class RadiationConfigScreen extends Screen {
    private final Screen parent;

    private boolean enableCommands;
    private TextFieldWidget intervalField;
    private Text status = Text.empty();

    public RadiationConfigScreen(Screen parent) {
        super(Text.literal("Radiation Zones Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        RadiationServerConfig config = RadiationServerConfig.loadOrCreate();
        this.enableCommands = config.isEnableCommands();

        int centerX = this.width / 2;
        int y = this.height / 2 - 30;

        this.addDrawableChild(ButtonWidget.builder(this.toggleLabel(), button -> {
            this.enableCommands = !this.enableCommands;
            button.setMessage(this.toggleLabel());
        }).dimensions(centerX - 100, y, 200, 20).build());

        this.intervalField = new TextFieldWidget(this.textRenderer, centerX - 100, y + 28, 200, 20, Text.literal("Radiation check interval"));
        this.intervalField.setText(Integer.toString(config.getRadiationCheckIntervalTicks()));
        this.addDrawableChild(this.intervalField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveConfig())
                .dimensions(centerX - 100, y + 56, 98, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> this.close())
                .dimensions(centerX + 2, y + 56, 98, 20)
                .build());
    }

    private void saveConfig() {
        int interval;
        try {
            interval = Integer.parseInt(this.intervalField.getText().trim());
        } catch (NumberFormatException ignored) {
            this.status = Text.literal("Interval must be a positive integer.");
            return;
        }

        if (interval <= 0) {
            this.status = Text.literal("Interval must be greater than 0.");
            return;
        }

        RadiationServerConfig config = RadiationServerConfig.loadOrCreate();
        config.setEnableCommands(this.enableCommands);
        config.setRadiationCheckIntervalTicks(interval);
        config.save();
        this.close();
    }

    private Text toggleLabel() {
        return Text.literal("Enable commands: " + (this.enableCommands ? "ON" : "OFF"));
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 58, 0xFFFFFF);
        graphics.drawText(this.textRenderer, Text.literal("Radiation check interval (ticks):"), this.width / 2 - 100, this.height / 2 - 2, 0xA0A0A0, false);
        if (!this.status.getString().isEmpty()) {
            graphics.drawCenteredTextWithShadow(this.textRenderer, this.status, this.width / 2, this.height / 2 + 84, 0xFF5555);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}