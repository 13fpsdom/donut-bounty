package com.example.donutbounty.client;

import com.example.donutbounty.BountyService;
import com.example.donutbounty.core.PaymentTracker;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Manual confirmation: add a payment you saw yourself but the mod did not detect. Also lists the recent
 * payment-related messages the mod saw, so you can compare them with the configured patterns.
 */
public class ManualPaymentScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget nameField;
    private TextFieldWidget amountField;
    private String nameText = "";
    private String amountText = "";
    private String status = "";

    public ManualPaymentScreen(Screen parent) {
        super(Text.literal("Add a payment manually"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(220, this.width - 40);
        int x = (this.width - w) / 2;

        nameField = new TextFieldWidget(this.textRenderer, x, 56, w, 18, Text.literal("Username"));
        nameField.setMaxLength(17);
        nameField.setPlaceholder(Text.literal("Minecraft username"));
        nameField.setText(nameText);
        nameField.setChangedListener(s -> nameText = s);
        addDrawableChild(nameField);
        setInitialFocus(nameField);

        amountField = new TextFieldWidget(this.textRenderer, x, 92, w, 18, Text.literal("Amount"));
        amountField.setMaxLength(20);
        amountField.setPlaceholder(Text.literal("e.g. 5000, $5,000, 2.5m"));
        amountField.setText(amountText);
        amountField.setChangedListener(s -> amountText = s);
        addDrawableChild(amountField);

        int half = (w - 4) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Add"), b -> submit())
                .dimensions(x, 118, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(x + half + 4, 118, half, 20).build());
    }

    private void submit() {
        PaymentTracker.Result r = DonutBountyClient.service().addManual(nameText, amountText);
        if (r.outcome() == PaymentTracker.Outcome.COUNTED) {
            close();
        } else {
            status = "Enter a valid Minecraft username and an amount such as 5000, $5,000 or 2.5m.";
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 10, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Only add payments you saw yourself that were not detected.", cx, 26, 0xFFCBD5E1);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Manual entries are trusted, so double-check the name and amount.", cx, 37, 0xFFCBD5E1);
        int w = Math.min(220, this.width - 40);
        int x = (this.width - w) / 2;
        ctx.drawText(this.textRenderer, "Username", x, 46, 0xFF9CA3AF, false);
        ctx.drawText(this.textRenderer, "Amount", x, 82, 0xFF9CA3AF, false);

        if (!status.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(status, this.width - 20), cx, 144, 0xFFF87171);
        }

        List<BountyService.Candidate> seen = DonutBountyClient.service().recentCandidates();
        int y = 162;
        ctx.drawText(this.textRenderer, "Recent payment-related messages (what the server really sends):", 10, y, 0xFF9CA3AF, false);
        y += 12;
        if (seen.isEmpty()) {
            ctx.drawText(this.textRenderer, "None yet. If you receive a payment and nothing shows here, the server may not", 10, y, 0xFF6B7280, false);
            ctx.drawText(this.textRenderer, "expose it as a system message. See the README (Limitations).", 10, y + 11, 0xFF6B7280, false);
        }
        int maxLines = Math.max(0, (this.height - y - 6) / 11);
        for (int i = 0; i < Math.min(maxLines, seen.size()); i++) {
            BountyService.Candidate c = seen.get(i);
            String line = "[" + c.verdict() + "] " + c.text();
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(line, this.width - 20), 10, y + i * 11, 0xFFE5E7EB, false);
        }
    }
}
