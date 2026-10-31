package com.psu.debrisfinder;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DebrisFinder implements ClientModInitializer {
    public static final String MOD_ID = "debrisfinder";
    public static final String PREFIX = "[Debris] ";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean keyWasDown;

    @Override
    public void onInitializeClient() {
        SeedStore.load();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> DebrisPredictor.initAsync());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SeedStore.onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SeedStore.onLeave());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            pollKey(client);
            Highlights.tick(client);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> registerCommands(dispatcher));
    }

    private static void pollKey(MinecraftClient client) {
        boolean down = client.currentScreen == null
                && client.getWindow() != null
                && GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_7) == GLFW.GLFW_PRESS;
        if (down && !keyWasDown && client.player != null) {
            boolean on = Highlights.toggle();
            client.player.sendMessage(Text.literal(PREFIX + "Highlights " + (on ? "ON" : "OFF"))
                    .formatted(on ? Formatting.AQUA : Formatting.GRAY), true);
        }
        keyWasDown = down;
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("debris")
                .then(ClientCommandManager.literal("seed")
                        .then(ClientCommandManager.argument("seed", LongArgumentType.longArg())
                                .executes(ctx -> {
                                    SeedStore.set(LongArgumentType.getLong(ctx, "seed"), "command", true);
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("clear")
                        .executes(ctx -> {
                            SeedStore.clear();
                            return 1;
                        }))
                .then(ClientCommandManager.literal("toggle")
                        .executes(ctx -> {
                            boolean on = Highlights.toggle();
                            ctx.getSource().sendFeedback(Text.literal(PREFIX + "Highlights " + (on ? "ON" : "OFF")));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("status")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal(PREFIX + status(MinecraftClient.getInstance())));
                            return 1;
                        })));
    }

    private static String status(MinecraftClient client) {
        Long seed = SeedStore.current();
        String seedText = seed == null ? "none" : seed + SeedStore.verifyLabel(client, seed);
        String predictor = DebrisPredictor.ready() ? "ready" : DebrisPredictor.error() != null ? "failed (" + DebrisPredictor.error() + ")" : "loading";
        return "seed " + seedText + ", highlights " + (Highlights.enabled() ? "on" : "off")
                + ", marked " + Highlights.count() + ", predictor " + predictor;
    }

    public static void chat(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) client.inGameHud.getChatHud().addMessage(text);
    }
}
