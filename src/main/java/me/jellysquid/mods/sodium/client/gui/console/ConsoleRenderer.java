package me.jellysquid.mods.sodium.client.gui.console;

import me.jellysquid.mods.sodium.client.gui.console.message.Message;
import me.jellysquid.mods.sodium.client.gui.console.message.MessageLevel;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.util.ColorU8;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public class ConsoleRenderer {
    static final ConsoleRenderer INSTANCE = new ConsoleRenderer();

    private final LinkedList<ActiveMessage> activeMessages = new LinkedList<>();

    public void update(Console console, double currentTime) {
        this.purgeMessages(currentTime);
        this.pollMessages(console, currentTime);
    }

    private void purgeMessages(double currentTime) {
        this.activeMessages.removeIf(message ->
                currentTime > message.timestamp() + message.duration());
    }

    private void pollMessages(Console console, double currentTime) {
        var log = console.getMessageDrain();

        while (!log.isEmpty()) {
            this.activeMessages.add(ActiveMessage.create(log.poll(), currentTime));
        }
    }

    public void draw(DrawContext context) {
        var currentTime = GLFW.glfwGetTime();

        MinecraftClient client = MinecraftClient.getInstance();

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0.0f, 0.0f, 1000.0f);


        var paddingWidth = 3;
        var paddingHeight = 1;

        var renders = new ArrayList<MessageRender>();

        {
            int x = 4;
            int y = 4;

            for (ActiveMessage message : this.activeMessages) {
                double opacity = getMessageOpacity(message, currentTime);

                if (opacity < 0.025D) {
                    continue;
                }

                List<OrderedText> lines = new ArrayList<>();

                var messageWidth = 270;

                TextHandler textHandler = client.textRenderer.getTextHandler();
                textHandler.wrapLines(message.text(), messageWidth - 20, Style.EMPTY, (text, lastLineWrapped) -> {
                    lines.add(Language.getInstance().reorder(text));
                });

                var messageHeight = (client.textRenderer.fontHeight * lines.size()) + (paddingHeight * 2);

                renders.add(new MessageRender(x, y, messageWidth, messageHeight, message.level(), lines, opacity));

                y += messageHeight;
            }
        }

        var mouseX = client.mouse.getX() / client.getWindow().getScaleFactor();
        var mouseY = client.mouse.getY() / client.getWindow().getScaleFactor();

        boolean hovered = false;

        for (var render : renders) {
            if (mouseX >= render.x && mouseX < render.x + render.width && mouseY >= render.y && mouseY < render.y + render.height) {
                hovered = true;
                break;
            }
        }

        for (var render : renders) {
            var x = render.x();
            var y = render.y();

            var width = render.width();
            var height = render.height();

            var colors = COLORS.get(render.level());
            var opacity = render.opacity();

            if (hovered) {
                opacity *= 0.4D;
            }

            // message background
            context.fill(x, y, x + width, y + height,
                    ColorARGB.withAlpha(colors.background(), weightAlpha(opacity)));

            // message colored stripe
            context.fill(x, y, x + 1, y + height,
                    ColorARGB.withAlpha(colors.foreground(), weightAlpha(opacity)));

            for (var line : render.lines()) {
                // message text
                context.drawText(client.textRenderer, line, x + paddingWidth + 3, y + paddingHeight,
                        ColorARGB.withAlpha(colors.text(), weightAlpha(opacity)), false);

                y += client.textRenderer.fontHeight;
            }
        }

        matrices.pop();
    }

    private static double getMessageOpacity(ActiveMessage message, double time) {
        double midpoint = message.timestamp() + (message.duration() / 2.0D);

        if (time > midpoint) {
            return getFadeOutOpacity(message, time);
        } else if (time < midpoint) {
            return getFadeInOpacity(message, time);
        } else {
            return 1.0D;
        }
    }

    private static double getFadeInOpacity(ActiveMessage message, double time) {
        var animationDuration = 0.25D;

        var animationStart = message.timestamp();
        var animationEnd = message.timestamp() + animationDuration;

        return getAnimationProgress(time, animationStart, animationEnd);
    }

    private static double getFadeOutOpacity(ActiveMessage message, double time) {
        // animation duration is 1/5th the message's duration, or 0.5 seconds, whichever is smaller
        var animationDuration = Math.min(0.5D, message.duration() * 0.20D);

        var animationStart = message.timestamp() + message.duration() - animationDuration;
        var animationEnd = message.timestamp() + message.duration();

        return 1.0D - getAnimationProgress(time, animationStart, animationEnd);
    }

    private static double getAnimationProgress(double currentTime, double startTime, double endTime) {
        return MathHelper.clamp(MathHelper.getLerpProgress(currentTime, startTime, endTime), 0.0D, 1.0D);
    }

    private static int weightAlpha(double scale) {
        return ColorU8.normalizedFloatToByte((float) scale);
    }

    private record ActiveMessage(MessageLevel level, Text text, double duration, double timestamp) {

        public static ActiveMessage create(Message message, double timestamp) {
            var text = message.text()
                    .copy()
                    .styled((style) -> style.withFont(MinecraftClient.UNICODE_FONT_ID));

            return new ActiveMessage(message.level(), text, message.duration(), timestamp);
        }
    }

    private static final EnumMap<MessageLevel, ColorPalette> COLORS = new EnumMap<>(MessageLevel.class);

    static {
        COLORS.put(MessageLevel.INFO, new ColorPalette(
                ColorARGB.pack(255, 255, 255),
                ColorARGB.pack( 15,  15,  15),
                ColorARGB.pack( 15,  15,  15)
        ));

        COLORS.put(MessageLevel.WARN, new ColorPalette(
                ColorARGB.pack(224, 187,   0),
                ColorARGB.pack( 25,  21,   0),
                ColorARGB.pack(180, 150,   0)
        ));

        COLORS.put(MessageLevel.ERROR, new ColorPalette(
                ColorARGB.pack(220,   0,   0),
                ColorARGB.pack( 25,   0,   0),
                ColorARGB.pack(160,   0,   0)
        ));
    }

    private record ColorPalette(int text, int background, int foreground) {

    }

    private record MessageRender(int x, int y, int width, int height, MessageLevel level, List<OrderedText> lines, double opacity) {

    }
}
