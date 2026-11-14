package dev.pixelclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;

/** Crisp, 8-bit styled button. Drawn in-game, with no custom external textures. */
public final class PixelWidget extends AbstractWidget {
    private final Runnable action;
    private final int swatch;
    private final boolean selected;

    public PixelWidget(int x, int y, int width, int height, String title, Runnable action, int swatch, boolean selected) {
        super(x, y, width, height, Component.literal(title));
        this.action = action;
        this.swatch = swatch;
        this.selected = selected;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
        int x = getX(), y = getY();
        int w = getWidth(), h = getHeight();
        int accent = swatch == 0 ? PixelClient.CONFIG.accent() : swatch;
        int bg = selected ? 0xFF304271 : 0xFF202A4A;
        if (!active) bg = 0xFF2A2B37;
        if (isHovered && active) bg = 0xFF344979;
        // Stepped pixel shadow and one-pixel hard edges, not a vanilla round button.
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0xA000071F);
        g.fill(x + 2, y, x + w - 2, y + h, accent);
        g.fill(x, y + 2, x + w, y + h - 2, accent);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, bg);
        g.fill(x + 3, y + 3, x + w - 3, y + 4, selected ? accent : 0xFF496088);
        if (selected) g.fill(x + 2, y + 2, x + 5, y + h - 2, accent);
        int fg = active ? 0xFFF8FBFF : 0xFF9A9DB1;
        String text = getMessage().getString();
        var font = Minecraft.getInstance().font;
        while (!text.isEmpty() && font.width(text) > w - 9) text = text.substring(0, text.length()-1);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, fg);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (active && visible) action.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isFocused() && active && (event.key() == GLFW.GLFW_KEY_ENTER
                || event.key() == GLFW.GLFW_KEY_KP_ENTER || event.key() == GLFW.GLFW_KEY_SPACE)) {
            action.run();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, getMessage());
    }
}
