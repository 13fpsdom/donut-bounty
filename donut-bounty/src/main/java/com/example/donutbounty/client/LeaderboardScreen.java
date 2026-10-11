package com.example.donutbounty.client;

import com.example.donutbounty.BountyService;
import com.example.donutbounty.core.Leaderboard;
import com.example.donutbounty.core.Money;
import com.example.donutbounty.core.TimeAgo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Ranked list of everyone who has paid you, with export and clear (after confirmation). */
public class LeaderboardScreen extends Screen {
    private static final int ROW_H = 14;

    private final Screen parent;
    private int scroll;
    private String status = "";

    private int tableX;
    private int tableW;
    private int rowsTop;
    private int rowsBottom;

    public LeaderboardScreen(Screen parent) {
        super(Text.literal("Leaderboard"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        tableW = Math.min(this.width - 24, 440);
        tableX = (this.width - tableW) / 2;
        rowsTop = 58;
        rowsBottom = this.height - 44;

        int gap = 4;
        int w = (tableW - gap * 3) / 4;
        int y = this.height - 30;
        BountyService svc = DonutBountyClient.service();

        addDrawableChild(ButtonWidget.builder(Text.literal("Export CSV"), b -> {
            try {
                Path file = svc.exportCsv();
                this.client.keyboard.setClipboard(file.toString());
                status = "Saved " + file.getFileName() + " (full path copied to clipboard)";
            } catch (IOException e) {
                status = "Export failed: " + e.getMessage();
            }
        }).dimensions(tableX, y, w, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Add manually"), b ->
                this.client.setScreen(new ManualPaymentScreen(this))
        ).dimensions(tableX + (w + gap), y, w, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear data"), b ->
                this.client.setScreen(new ConfirmScreen(
                        confirmed -> {
                            if (confirmed) svc.clearLeaderboard();
                            this.client.setScreen(new LeaderboardScreen(parent));
                        },
                        Text.literal("Clear the leaderboard?"),
                        Text.literal("This permanently deletes all recorded payments. Exported CSV files are kept. Export first if you want a copy.")))
        ).dimensions(tableX + (w + gap) * 2, y, w, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(tableX + (w + gap) * 3, y, w, 20).build());
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int visibleRows() {
        return Math.max(1, (rowsBottom - rowsTop) / ROW_H);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int total = DonutBountyClient.service().leaderboard().playerCount();
        int maxScroll = Math.max(0, total - visibleRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount) * 2));
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        BountyService svc = DonutBountyClient.service();
        Leaderboard board = svc.leaderboard();
        List<Leaderboard.Entry> ranked = board.ranked();

        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);
        String summary = ranked.size() + " player" + (ranked.size() == 1 ? "" : "s") + "  |  "
                + board.paymentCount() + " payment" + (board.paymentCount() == 1 ? "" : "s") + "  |  "
                + Money.format(board.totalCents()) + " total";
        ctx.drawCenteredTextWithShadow(this.textRenderer, summary, this.width / 2, 25, 0xFFCBD5E1);

        int nameW = (int) (tableW * 0.28);
        int totalW = (int) (tableW * 0.22);
        int countW = (int) (tableW * 0.12);
        int c1 = tableX + 6;
        int c2 = tableX + 28;
        int c3 = c2 + nameW;
        int c4 = c3 + totalW;
        int c5 = c4 + countW;

        ctx.fill(tableX - 2, 40, tableX + tableW + 2, rowsBottom + 2, 0xA0000000);
        ctx.fill(tableX - 2, 40, tableX + tableW + 2, 54, 0xC0202833);
        ctx.drawText(this.textRenderer, "#", c1, 43, 0xFF9CA3AF, false);
        ctx.drawText(this.textRenderer, "Player", c2, 43, 0xFF9CA3AF, false);
        ctx.drawText(this.textRenderer, "Total paid", c3, 43, 0xFF9CA3AF, false);
        ctx.drawText(this.textRenderer, "Payments", c4, 43, 0xFF9CA3AF, false);
        ctx.drawText(this.textRenderer, "Last payment", c5, 43, 0xFF9CA3AF, false);

        if (ranked.isEmpty()) {
            ctx.drawText(this.textRenderer, "No payments recorded yet.", tableX + 8, rowsTop + 6, 0xFF9CA3AF, false);
            ctx.drawText(this.textRenderer, "Start a bounty and wait for payments, or use Add manually.", tableX + 8, rowsTop + 20, 0xFF9CA3AF, false);
        }

        int rows = visibleRows();
        int maxScroll = Math.max(0, ranked.size() - rows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        long now = System.currentTimeMillis();
        for (int i = 0; i < rows; i++) {
            int idx = scroll + i;
            if (idx >= ranked.size()) break;
            Leaderboard.Entry e = ranked.get(idx);
            int y = rowsTop + i * ROW_H;
            if (idx % 2 == 0) ctx.fill(tableX, y - 2, tableX + tableW, y + ROW_H - 2, 0x20FFFFFF);
            int rankColour = idx == 0 ? 0xFFFFD700 : idx == 1 ? 0xFFC0C0C0 : idx == 2 ? 0xFFCD7F32 : 0xFFFFFFFF;
            ctx.drawText(this.textRenderer, String.valueOf(idx + 1), c1, y, rankColour, false);
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(e.name(), nameW - 6), c2, y, 0xFFFFFFFF, false);
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(Money.format(e.totalCents()), totalW - 6), c3, y, 0xFF4ADE80, false);
            ctx.drawText(this.textRenderer, String.valueOf(e.count()), c4, y, 0xFFFFFFFF, false);
            String last = TimeAgo.format(e.lastPaymentMillis(), now) + " (" + Money.compact(e.lastAmountCents()) + ")";
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(last, tableX + tableW - c5 - 4), c5, y, 0xFFCBD5E1, false);
        }

        String msg = status.isEmpty() ? svc.statusLine() : status;
        if (!msg.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(msg, this.width - 24),
                    this.width / 2, this.height - 42, 0xFFFFD166);
        }
    }
}
