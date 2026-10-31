package com.psu.debrisfinder;

import com.psu.debrisfinder.mixin.BiomeAccessAccessor;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.biome.source.BiomeAccess;

public final class SeedStore {
    private static final Pattern CRACKED = Pattern.compile("(?i)world\\s*seed[^0-9-]*(-?\\d{1,20})");
    private static final Pattern VANILLA = Pattern.compile("^Seed:\\s*\\[?(-?\\d{1,20})");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("debrisfinder-seeds.properties");
    private static final Properties SAVED = new Properties();

    private static String serverKey;
    private static Long current;

    private SeedStore() {}

    public static Long current() {
        return current;
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            SAVED.load(reader);
        } catch (IOException e) {
            DebrisFinder.LOGGER.warn("Could not read saved seeds", e);
        }
    }

    private static void save() {
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            SAVED.store(writer, "Debris Finder seeds per server");
        } catch (IOException e) {
            DebrisFinder.LOGGER.warn("Could not save seeds", e);
        }
    }

    public static void onJoin(MinecraftClient client) {
        current = null;
        serverKey = null;
        if (client.isIntegratedServerRunning() && client.getServer() != null) {
            serverKey = "singleplayer";
            current = client.getServer().getOverworld().getSeed();
            return;
        }
        ServerInfo info = client.getCurrentServerEntry();
        if (info == null || info.address == null) return;
        serverKey = info.address.toLowerCase(Locale.ROOT);
        Long saved = parse(SAVED.getProperty(serverKey));
        if (saved != null) {
            current = saved;
            client.execute(() -> DebrisFinder.chat(Text.literal(DebrisFinder.PREFIX + "Loaded saved seed " + saved + verifyLabel(client, saved))
                    .formatted(Formatting.AQUA)));
        }
    }

    public static void onLeave() {
        current = null;
        serverKey = null;
    }

    public static void clear() {
        current = null;
        if (serverKey != null && !serverKey.equals("singleplayer")) {
            SAVED.remove(serverKey);
            save();
        }
        DebrisFinder.chat(Text.literal(DebrisFinder.PREFIX + "Seed cleared").formatted(Formatting.GRAY));
    }

    public static void set(long seed, String source, boolean force) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (serverKey == null) {
            DebrisFinder.chat(Text.literal(DebrisFinder.PREFIX + "Join a server first").formatted(Formatting.RED));
            return;
        }
        Boolean ok = verify(client, seed);
        if (!force && Boolean.FALSE.equals(ok) && current != null && Boolean.TRUE.equals(verify(client, current))) return;
        current = seed;
        if (!serverKey.equals("singleplayer")) {
            SAVED.setProperty(serverKey, Long.toString(seed));
            save();
        }
        Formatting color = Boolean.FALSE.equals(ok) ? Formatting.YELLOW : Formatting.AQUA;
        DebrisFinder.chat(Text.literal(DebrisFinder.PREFIX + "Seed set from " + source + ": " + seed + verifyLabel(client, seed)).formatted(color));
    }

    public static void onChat(String text) {
        if (serverKey == null || text == null || text.startsWith(DebrisFinder.PREFIX)) return;
        Long seed = null;
        Matcher m = CRACKED.matcher(text);
        if (m.find()) {
            seed = parse(m.group(1));
        } else {
            m = VANILLA.matcher(text);
            if (m.find()) seed = parse(m.group(1));
        }
        if (seed == null || (current != null && current.longValue() == seed.longValue())) return;
        long found = seed;
        MinecraftClient.getInstance().execute(() -> set(found, "chat", false));
    }

    public static Boolean verify(MinecraftClient client, long seed) {
        if (client.world == null) return null;
        long hashed = ((BiomeAccessAccessor) (Object) client.world.getBiomeAccess()).debrisfinder$getSeed();
        return BiomeAccess.hashSeed(seed) == hashed;
    }

    public static String verifyLabel(MinecraftClient client, long seed) {
        Boolean ok = verify(client, seed);
        if (ok == null) return "";
        return ok ? " (verified)" : " (hash mismatch, may be wrong)";
    }

    private static Long parse(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
