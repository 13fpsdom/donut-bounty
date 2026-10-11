package com.example.donutbounty.client;

import com.example.donutbounty.BountyService;
import com.example.donutbounty.ModConfig;
import com.example.donutbounty.core.BountyTimer;
import com.example.donutbounty.core.Colors;
import com.example.donutbounty.core.DurationParser;
import com.example.donutbounty.core.Money;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

/** Compact top-centre panel: block icon + name, countdown, raised / target, progress bars. */
public final class BountyHud {
    private static final int PANEL_W = 176;

    private static String cachedId = null;
    private static ItemStack cachedIcon = ItemStack.EMPTY;
    private static String cachedName = "";

    private BountyHud() {}

    public static void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        BountyService svc = DonutBountyClient.service();
        if (svc == null || mc.options.hudHidden || mc.player == null) return;
        if (mc.currentScreen instanceof SettingsScreen) return; // the settings screen draws its own live preview
        ModConfig.Hud hud = svc.config().hud;
        if (!hud.enabled) return;
        BountyService.Snapshot snap = svc.snapshot();
        if (snap.state() == BountyTimer.State.IDLE) return;
        draw(context, mc.textRenderer, snap, hud, mc.getWindow().getScaledWidth());
    }

    /** Example data for the settings preview. */
    public static BountyService.Snapshot sample() {
        return new BountyService.Snapshot(BountyTimer.State.RUNNING, 754_000L, 0.42,
                "minecraft:diamond_block", 500_000_000L, 182_500_000L);
    }

    public static void draw(DrawContext ctx, TextRenderer tr, BountyService.Snapshot s, ModConfig.Hud hud, int screenWidth) {
        updateCache(s.blockId());

        int bg = Colors.parseOr(hud.background, 0xD0101418);
        int border = Colors.parseOr(hud.border, 0xFF3A4350);
        int text = Colors.parseOr(hud.text, 0xFFFFFFFF);
        int accent = Colors.parseOr(hud.accent, 0xFF4ADE80);
        int warning = Colors.parseOr(hud.warning, 0xFFF87171);
        int track = Colors.parseOr(hud.track, 0xFF2A323C);
        int dim = (text & 0x00FFFFFF) | 0xB0000000;

        boolean hasTarget = s.targetCents() > 0;
        int h = hasTarget ? 46 : 34;
        int x0 = -PANEL_W / 2;
        int barL = x0 + 8;
        int barR = x0 + PANEL_W - 8;

        String timerText;
        int timerColour = text;
        switch (s.state()) {
            case PAUSED -> {
                timerText = "PAUSED " + DurationParser.format(s.remainingMillis());
                timerColour = dim;
            }
            case FINISHED -> {
                timerText = "ENDED";
                timerColour = warning;
            }
            default -> {
                timerText = DurationParser.format(s.remainingMillis());
                if (s.remainingMillis() < 60_000L) timerColour = warning;
            }
        }

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(screenWidth / 2.0f + hud.offsetX, (float) hud.offsetY, 0.0f);
        m.scale((float) hud.scale, (float) hud.scale, 1.0f);

        ctx.fill(x0, 0, x0 + PANEL_W, h, bg);
        ctx.drawBorder(x0, 0, PANEL_W, h, border);
        ctx.fill(x0, 0, x0 + 3, h, accent);

        // icon (blocks without an item form get a plain placeholder square)
        if (!cachedIcon.isEmpty()) {
            ctx.drawItem(cachedIcon, x0 + 8, 5);
        } else {
            ctx.fill(x0 + 8, 5, x0 + 24, 21, track);
        }

        // row 1: block name (left) and timer (right)
        int timerW = tr.getWidth(timerText);
        int nameMax = PANEL_W - 34 - timerW - 12;
        ctx.drawText(tr, tr.trimToWidth(cachedName, Math.max(20, nameMax)), x0 + 30, 7, text, true);
        ctx.drawText(tr, timerText, barR - timerW, 7, timerColour, true);

        // row 2: raised / target
        String raised = Money.compact(s.raisedCents());
        if (hasTarget) {
            ctx.drawText(tr, raised + " / " + Money.compact(s.targetCents()), x0 + 30, 19, dim, false);
            int pct = (int) Math.min(999, (s.raisedCents() * 100L) / s.targetCents());
            String pctText = pct + "%";
            ctx.drawText(tr, pctText, barR - tr.getWidth(pctText), 19, pct >= 100 ? accent : dim, false);

            double frac = Math.min(1.0, s.raisedCents() / (double) s.targetCents());
            int y = h - 14;
            ctx.fill(barL, y, barR, y + 4, track);
            ctx.fill(barL, y, barL + (int) Math.round((barR - barL) * frac), y + 4, accent);
        } else {
            ctx.drawText(tr, "Raised " + raised, x0 + 30, 19, dim, false);
        }

        // time remaining bar along the bottom
        int ty = h - 5;
        ctx.fill(barL, ty, barR, ty + 2, track);
        int timeColour = s.remainingMillis() < 60_000L && s.state() == BountyTimer.State.RUNNING ? warning : accent;
        ctx.fill(barL, ty, barL + (int) Math.round((barR - barL) * s.fractionRemaining()), ty + 2, timeColour);

        m.pop();
    }

    private static void updateCache(String blockId) {
        if (blockId != null && blockId.equals(cachedId)) return;
        cachedId = blockId;
        var entry = BlockCatalog.byId(blockId);
        if (entry.isPresent()) {
            cachedIcon = entry.get().icon();
            cachedName = entry.get().name();
        } else {
            cachedIcon = ItemStack.EMPTY;
            cachedName = blockId == null ? "" : blockId;
        }
    }
}
