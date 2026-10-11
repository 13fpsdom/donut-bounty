package com.example.donutbounty.client;

import com.example.donutbounty.BountyService;
import com.example.donutbounty.core.BountyTimer;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/** Main screen (default key: H): pick a block, set duration and optional target, control the bounty. */
public class BountyScreen extends Screen {
    private static final int ROW_H = 18;

    private final Screen parent;

    private TextFieldWidget searchField;
    private TextFieldWidget durationField;
    private TextFieldWidget targetField;
    private ButtonWidget startButton;
    private ButtonWidget pauseButton;
    private ButtonWidget resumeButton;
    private ButtonWidget stopButton;

    private String searchText = "";
    private String durationText;
    private String targetText;
    private List<BlockCatalog.Entry> filtered = List.of();
    private BlockCatalog.Entry selected;
    private int scroll;
    private String status = "";

    private int topY;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int rightX;
    private int rightW;

    public BountyScreen(Screen parent) {
        super(Text.literal("Bounty Tracker"));
        this.parent = parent;
        BountyService svc = DonutBountyClient.service();
        this.durationText = svc.config().lastDuration;
        this.targetText = svc.config().lastTarget;
        String id = svc.blockId().isEmpty() ? svc.config().lastBlockId : svc.blockId();
        this.selected = BlockCatalog.byId(id).orElse(null);
    }

    @Override
    protected void init() {
        topY = 30;
        int margin = 12;
        listX = margin;
        listW = Math.max(120, Math.min(240, this.width / 2 - margin - 6));
        rightX = listX + listW + 14;
        rightW = Math.max(120, Math.min(220, this.width - rightX - margin));
        listY = topY + 24;
        listH = Math.max(ROW_H * 3, this.height - listY - 34);

        searchField = new TextFieldWidget(this.textRenderer, listX, topY, listW, 18, Text.literal("Search"));
        searchField.setPlaceholder(Text.literal("Search blocks..."));
        searchField.setMaxLength(48);
        searchField.setText(searchText);
        searchField.setChangedListener(s -> {
            searchText = s;
            refilter();
        });
        addDrawableChild(searchField);
        setInitialFocus(searchField);

        durationField = new TextFieldWidget(this.textRenderer, rightX, topY + 54, rightW, 18, Text.literal("Duration"));
        durationField.setMaxLength(16);
        durationField.setText(durationText);
        durationField.setChangedListener(s -> durationText = s);
        addDrawableChild(durationField);

        targetField = new TextFieldWidget(this.textRenderer, rightX, topY + 90, rightW, 18, Text.literal("Target"));
        targetField.setMaxLength(20);
        targetField.setPlaceholder(Text.literal("optional"));
        targetField.setText(targetText);
        targetField.setChangedListener(s -> targetText = s);
        addDrawableChild(targetField);

        int half = (rightW - 4) / 2;
        int row1 = topY + 116;
        int row2 = topY + 138;
        int row3 = topY + 164;
        int row4 = topY + 186;

        startButton = addDrawableChild(ButtonWidget.builder(Text.literal("Start"), b -> onStart())
                .dimensions(rightX, row1, half, 20).build());
        pauseButton = addDrawableChild(ButtonWidget.builder(Text.literal("Pause"), b -> {
            status = DonutBountyClient.service().pause() ? "Paused. Payments are not counted while paused." : "Can't pause right now.";
        }).dimensions(rightX + half + 4, row1, half, 20).build());
        resumeButton = addDrawableChild(ButtonWidget.builder(Text.literal("Resume"), b -> {
            status = DonutBountyClient.service().resume() ? "Resumed." : "Nothing to resume.";
        }).dimensions(rightX, row2, half, 20).build());
        stopButton = addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), b -> {
            status = DonutBountyClient.service().stop() ? "Stopped. The leaderboard keeps its totals." : "No bounty to stop.";
        }).dimensions(rightX + half + 4, row2, half, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Leaderboard"), b -> {
            remember();
            this.client.setScreen(new LeaderboardScreen(this));
        }).dimensions(rightX, row3, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("HUD settings"), b -> {
            remember();
            this.client.setScreen(new SettingsScreen(this));
        }).dimensions(rightX + half + 4, row3, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(rightX, row4, rightW, 20).build());

        refilter();
        updateButtons();
    }

    private void refilter() {
        filtered = BlockCatalog.filter(searchText);
        scroll = 0;
    }

    private void onStart() {
        if (selected == null) {
            status = "Pick a block first.";
            return;
        }
        String err = DonutBountyClient.service().start(selected.id().toString(), durationText, targetText);
        status = err == null ? "Bounty started. Confirmed payments are counted while it is running." : err;
    }

    private void remember() {
        DonutBountyClient.service().rememberInputs(selected == null ? "" : selected.id().toString(), durationText, targetText);
    }

    private void updateButtons() {
        BountyTimer.State st = DonutBountyClient.service().timerState();
        startButton.active = st == BountyTimer.State.IDLE || st == BountyTimer.State.FINISHED;
        pauseButton.active = st == BountyTimer.State.RUNNING;
        resumeButton.active = st == BountyTimer.State.PAUSED;
        stopButton.active = st != BountyTimer.State.IDLE;
    }

    @Override
    public void tick() {
        updateButtons();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        remember();
        this.client.setScreen(parent);
    }

    // ---- input on the block list ----

    private int visibleRows() {
        return Math.max(1, listH / ROW_H);
    }

    private boolean overList(double mx, double my) {
        return mx >= listX && mx < listX + listW && my >= listY && my < listY + visibleRows() * ROW_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && overList(mouseX, mouseY)) {
            int idx = scroll + (int) ((mouseY - listY) / ROW_H);
            if (idx >= 0 && idx < filtered.size()) {
                selected = filtered.get(idx);
                status = "";
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (overList(mouseX, mouseY)) {
            int maxScroll = Math.max(0, filtered.size() - visibleRows());
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount) * 3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ---- drawing ----

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // draws the background and all widgets
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
        drawList(ctx, mouseX, mouseY);
        drawRight(ctx);
        drawStatus(ctx);
    }

    private void drawList(DrawContext ctx, int mx, int my) {
        ctx.fill(listX - 2, listY - 2, listX + listW + 2, listY + listH + 2, 0xA0000000);
        int rows = visibleRows();
        int maxScroll = Math.max(0, filtered.size() - rows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        for (int i = 0; i < rows; i++) {
            int idx = scroll + i;
            if (idx >= filtered.size()) break;
            BlockCatalog.Entry e = filtered.get(idx);
            int y = listY + i * ROW_H;
            boolean hover = mx >= listX && mx < listX + listW && my >= y && my < y + ROW_H;
            boolean isSelected = selected != null && selected.id().equals(e.id());
            if (isSelected) {
                ctx.fill(listX, y, listX + listW, y + ROW_H, 0x805AC8FA);
            } else if (hover) {
                ctx.fill(listX, y, listX + listW, y + ROW_H, 0x40FFFFFF);
            }
            if (!e.icon().isEmpty()) ctx.drawItem(e.icon(), listX + 2, y + 1);
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(e.name(), listW - 30), listX + 24, y + 5, 0xFFFFFFFF, false);
        }
        if (filtered.isEmpty()) {
            ctx.drawText(this.textRenderer, "No blocks match your search.", listX + 6, listY + 6, 0xFF9CA3AF, false);
        }
        if (filtered.size() > rows) {
            int barH = Math.max(12, listH * rows / filtered.size());
            int barY = listY + (listH - barH) * scroll / Math.max(1, maxScroll);
            ctx.fill(listX + listW - 2, barY, listX + listW, barY + barH, 0xFFAAAAAA);
        }
        ctx.drawText(this.textRenderer, filtered.size() + " blocks", listX, listY + listH + 6, 0xFF9CA3AF, false);
    }

    private void drawRight(DrawContext ctx) {
        ctx.fill(rightX - 2, topY - 2, rightX + rightW + 2, topY + 38, 0xA0000000);
        if (selected != null) {
            if (!selected.icon().isEmpty()) {
                MatrixStack m = ctx.getMatrices();
                m.push();
                m.translate(rightX + 6.0f, topY + 3.0f, 0.0f);
                m.scale(2.0f, 2.0f, 1.0f);
                ctx.drawItem(selected.icon(), 0, 0);
                m.pop();
            }
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(selected.name(), rightW - 50), rightX + 44, topY + 8, 0xFFFFFFFF, true);
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(selected.id().toString(), rightW - 50), rightX + 44, topY + 21, 0xFF9CA3AF, false);
        } else {
            ctx.drawText(this.textRenderer, "No block selected. Click one in the list.", rightX + 6, topY + 14, 0xFF9CA3AF, false);
        }
        ctx.drawText(this.textRenderer, "Duration (30 = minutes, 45m, 1h30m, 90s, 1:30:00)", rightX, topY + 44, 0xFFCBD5E1, false);
        ctx.drawText(this.textRenderer, "Target amount (optional: 5000, $250,000, 2.5m)", rightX, topY + 80, 0xFFCBD5E1, false);
    }

    private void drawStatus(DrawContext ctx) {
        BountyService svc = DonutBountyClient.service();
        String msg = status;
        if (msg.isEmpty()) {
            List<String> warnings = svc.warnings();
            msg = !warnings.isEmpty() ? warnings.get(0) : svc.statusLine();
        }
        if (!msg.isEmpty()) {
            String shown = this.textRenderer.trimToWidth(msg, this.width - 24);
            ctx.drawCenteredTextWithShadow(this.textRenderer, shown, this.width / 2, this.height - 14, 0xFFFFD166);
        }
    }
}
