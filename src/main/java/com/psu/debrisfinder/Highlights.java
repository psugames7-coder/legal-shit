package com.psu.debrisfinder;

import com.psu.debrisfinder.mixin.BlockDisplayInvoker;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class Highlights {
    public static final int COLOR = 0x3366FF;
    private static final int MAX_MARKERS = 2000;
    private static final int SCAN_INTERVAL = 10;
    private static final int FIRST_ID = -1_000_000;

    private static final IntOpenHashSet MARKER_IDS = new IntOpenHashSet();
    private static final Long2ObjectOpenHashMap<DisplayEntity.BlockDisplayEntity> MARKERS = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<long[]> PREDICTIONS = new Long2ObjectOpenHashMap<>();

    private static boolean enabled = true;
    private static ClientWorld world;
    private static Long predictedSeed;
    private static int timer;
    private static int nextId = FIRST_ID;
    private static boolean warnedNoSeed;
    private static boolean warnedError;

    private Highlights() {}

    public static boolean isMarker(Entity entity) {
        return entity.getId() <= FIRST_ID && MARKER_IDS.contains(entity.getId());
    }

    public static boolean enabled() {
        return enabled;
    }

    public static int count() {
        return MARKERS.size();
    }

    public static boolean toggle() {
        enabled = !enabled;
        timer = SCAN_INTERVAL;
        return enabled;
    }

    public static void tick(MinecraftClient client) {
        if (client.world != world) {
            MARKERS.clear();
            MARKER_IDS.clear();
            PREDICTIONS.clear();
            world = client.world;
            warnedNoSeed = false;
            timer = SCAN_INTERVAL;
        }
        if (world == null || client.player == null) return;

        boolean nether = world.getRegistryKey() == World.NETHER;
        Long seed = SeedStore.current();

        if (!enabled || !nether || seed == null || !DebrisPredictor.ready()) {
            if (!MARKERS.isEmpty()) removeAll();
            if (enabled && nether) warn(seed);
            return;
        }

        if (!seed.equals(predictedSeed)) {
            PREDICTIONS.clear();
            removeAll();
            predictedSeed = seed;
            timer = SCAN_INTERVAL;
        }

        if (++timer < SCAN_INTERVAL) return;
        timer = 0;
        scan(client, seed);
    }

    private static void warn(Long seed) {
        if (seed == null && !warnedNoSeed) {
            warnedNoSeed = true;
            DebrisFinder.chat(Text.literal(DebrisFinder.PREFIX + "No seed yet. Crack it with SeedCracker or use /debris seed <seed>")
                    .formatted(Formatting.YELLOW));
        } else if (seed != null && DebrisPredictor.error() != null && !warnedError) {
            warnedError = true;
            DebrisFinder.chat(Text.literal(DebrisFinder.PREFIX + "Predictor failed: " + DebrisPredictor.error())
                    .formatted(Formatting.RED));
        }
    }

    private static void scan(MinecraftClient client, long seed) {
        int pcx = client.player.getBlockX() >> 4;
        int pcz = client.player.getBlockZ() >> 4;
        int radius = client.options.getClampedViewDistance();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        LongOpenHashSet found = new LongOpenHashSet();

        if (PREDICTIONS.size() > 60_000) PREDICTIONS.clear();

        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                long key = ChunkPos.toLong(cx, cz);
                long[] predicted = PREDICTIONS.get(key);
                if (predicted == null) {
                    predicted = DebrisPredictor.predict(seed, cx, cz);
                    PREDICTIONS.put(key, predicted);
                }
                for (long packed : predicted) {
                    pos.set(packed);
                    if (valid(pos)) found.add(packed);
                }
            }
        }

        List<Long> wanted = new ArrayList<>(found);
        if (wanted.size() > MAX_MARKERS) {
            double px = client.player.getX();
            double py = client.player.getY();
            double pz = client.player.getZ();
            wanted.sort(Comparator.comparingDouble(p -> {
                double dx = BlockPos.unpackLongX(p) + 0.5 - px;
                double dy = BlockPos.unpackLongY(p) + 0.5 - py;
                double dz = BlockPos.unpackLongZ(p) + 0.5 - pz;
                return dx * dx + dy * dy + dz * dz;
            }));
            wanted = wanted.subList(0, MAX_MARKERS);
        }
        LongOpenHashSet keep = new LongOpenHashSet();
        for (Long p : wanted) keep.add(p.longValue());

        var it = MARKERS.long2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!keep.contains(entry.getLongKey())) {
                remove(entry.getValue());
                it.remove();
            }
        }
        for (long p : keep) {
            if (!MARKERS.containsKey(p)) MARKERS.put(p, spawn(p));
        }
    }

    private static boolean valid(BlockPos pos) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        BlockState state = world.getBlockState(pos);
        if (state.isOf(Blocks.ANCIENT_DEBRIS)) return true;
        if (!(state.isIn(BlockTags.BASE_STONE_NETHER) || state.isOf(Blocks.NETHER_GOLD_ORE) || state.isOf(Blocks.NETHER_QUARTZ_ORE))) return false;
        for (Direction direction : Direction.values()) {
            if (world.getBlockState(pos.offset(direction)).isAir()) return false;
        }
        return true;
    }

    private static DisplayEntity.BlockDisplayEntity spawn(long packed) {
        DisplayEntity.BlockDisplayEntity marker = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        int id = nextId--;
        if (nextId < -2_000_000_000) nextId = FIRST_ID;
        marker.setId(id);
        ((BlockDisplayInvoker) marker).debrisfinder$setBlockState(Blocks.ANCIENT_DEBRIS.getDefaultState());
        marker.refreshPositionAndAngles(BlockPos.unpackLongX(packed), BlockPos.unpackLongY(packed), BlockPos.unpackLongZ(packed), 0.0f, 0.0f);
        marker.setGlowing(true);
        MARKER_IDS.add(id);
        world.addEntity(marker);
        return marker;
    }

    private static void remove(Entity marker) {
        MARKER_IDS.remove(marker.getId());
        world.removeEntity(marker.getId(), Entity.RemovalReason.DISCARDED);
    }

    private static void removeAll() {
        for (DisplayEntity.BlockDisplayEntity marker : MARKERS.values()) remove(marker);
        MARKERS.clear();
        MARKER_IDS.clear();
    }
}
