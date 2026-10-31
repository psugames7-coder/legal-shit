package com.psu.debrisfinder;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;

public final class DebrisPredictor {
    private static volatile int largeStep = -1;
    private static volatile int largeIndex = -1;
    private static volatile int smallStep = -1;
    private static volatile int smallIndex = -1;
    private static volatile boolean ready;
    private static volatile boolean started;
    private static volatile String error;

    private DebrisPredictor() {}

    public static boolean ready() {
        return ready;
    }

    public static String error() {
        return error;
    }

    public static void initAsync() {
        if (started) return;
        started = true;
        Thread thread = new Thread(DebrisPredictor::init, "DebrisFinder indexer");
        thread.setDaemon(true);
        thread.start();
    }

    private static void init() {
        try {
            RegistryWrapper.WrapperLookup lookup = BuiltinRegistries.createWrapperLookup();
            var biomes = lookup.getOrThrow(RegistryKeys.BIOME);
            List<RegistryEntry<Biome>> nether = new ArrayList<>();
            nether.add(biomes.getOrThrow(BiomeKeys.NETHER_WASTES));
            nether.add(biomes.getOrThrow(BiomeKeys.SOUL_SAND_VALLEY));
            nether.add(biomes.getOrThrow(BiomeKeys.CRIMSON_FOREST));
            nether.add(biomes.getOrThrow(BiomeKeys.WARPED_FOREST));
            nether.add(biomes.getOrThrow(BiomeKeys.BASALT_DELTAS));

            List<?> steps = PlacedFeatureIndexer.collectIndexedFeatures(nether,
                    (RegistryEntry<Biome> biome) -> biome.value().getGenerationSettings().getFeatures(), false);

            var placed = lookup.getOrThrow(RegistryKeys.PLACED_FEATURE);
            PlacedFeature large = placed.getOrThrow(OrePlacedFeatures.ORE_ANCIENT_DEBRIS_LARGE).value();
            PlacedFeature small = placed.getOrThrow(OrePlacedFeatures.ORE_DEBRIS_SMALL).value();

            int[] l = find(steps, large);
            int[] s = find(steps, small);
            largeStep = l[0];
            largeIndex = l[1];
            smallStep = s[0];
            smallIndex = s[1];
            ready = true;
            DebrisFinder.LOGGER.info("Debris features indexed: large step {} index {}, small step {} index {}", largeStep, largeIndex, smallStep, smallIndex);
        } catch (Throwable t) {
            error = t.getClass().getSimpleName() + ": " + t.getMessage();
            DebrisFinder.LOGGER.error("Debris feature indexing failed", t);
        }
    }

    private static int[] find(List<?> steps, PlacedFeature target) throws Exception {
        for (int step = 0; step < steps.size(); step++) {
            int index = featuresOf(steps.get(step)).indexOf(target);
            if (index >= 0) return new int[]{step, index};
        }
        throw new IllegalStateException("debris feature not in nether feature list");
    }

    private static List<?> featuresOf(Object stepData) throws Exception {
        Class<?> type = stepData.getClass();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (List.class.isAssignableFrom(component.getType())) {
                    Method accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    return (List<?>) accessor.invoke(stepData);
                }
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && List.class.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                return (List<?>) method.invoke(stepData);
            }
        }
        throw new IllegalStateException("cannot read indexed features");
    }

    public static long[] predict(long worldSeed, int chunkX, int chunkZ) {
        LongOpenHashSet out = new LongOpenHashSet();
        ChunkRandom random = new ChunkRandom(new Xoroshiro128PlusPlusRandom(0L));
        int bx = chunkX << 4;
        int bz = chunkZ << 4;
        long population = random.setPopulationSeed(worldSeed, bx, bz);

        random.setDecoratorSeed(population, largeIndex, largeStep);
        int x = bx + random.nextInt(16);
        int z = bz + random.nextInt(16);
        int y = 8 + random.nextInt(9) + random.nextInt(9);
        vein(random, x, y, z, 3, out);

        random.setDecoratorSeed(population, smallIndex, smallStep);
        x = bx + random.nextInt(16);
        z = bz + random.nextInt(16);
        y = 8 + random.nextInt(112);
        vein(random, x, y, z, 2, out);

        return out.toLongArray();
    }

    private static void vein(ChunkRandom random, int ox, int oy, int oz, int size, LongOpenHashSet out) {
        float angle = random.nextFloat() * (float) Math.PI;
        float spread = (float) size / 8.0f;
        int pad = MathHelper.ceil(((float) size / 16.0f * 2.0f + 1.0f) / 2.0f);
        double startX = ox + Math.sin(angle) * spread;
        double endX = ox - Math.sin(angle) * spread;
        double startZ = oz + Math.cos(angle) * spread;
        double endZ = oz - Math.cos(angle) * spread;
        double startY = oy + random.nextInt(3) - 2;
        double endY = oy + random.nextInt(3) - 2;
        int minX = ox - MathHelper.ceil(spread) - pad;
        int minY = oy - 2 - pad;
        int minZ = oz - MathHelper.ceil(spread) - pad;

        double[] ds = new double[size * 4];
        for (int k = 0; k < size; k++) {
            float f = (float) k / (float) size;
            double cx = MathHelper.lerp((double) f, startX, endX);
            double cy = MathHelper.lerp((double) f, startY, endY);
            double cz = MathHelper.lerp((double) f, startZ, endZ);
            double h = random.nextDouble() * (double) size / 16.0;
            double r = ((double) (MathHelper.sin((float) Math.PI * f) + 1.0f) * h + 1.0) / 2.0;
            ds[k * 4] = cx;
            ds[k * 4 + 1] = cy;
            ds[k * 4 + 2] = cz;
            ds[k * 4 + 3] = r;
        }

        for (int k = 0; k < size - 1; k++) {
            if (ds[k * 4 + 3] <= 0.0) continue;
            for (int m = k + 1; m < size; m++) {
                if (ds[m * 4 + 3] <= 0.0) continue;
                double dx = ds[k * 4] - ds[m * 4];
                double dy = ds[k * 4 + 1] - ds[m * 4 + 1];
                double dz = ds[k * 4 + 2] - ds[m * 4 + 2];
                double dr = ds[k * 4 + 3] - ds[m * 4 + 3];
                if (!(dx * dx + dy * dy + dz * dz < dr * dr)) continue;
                if (dr > 0.0) {
                    ds[m * 4 + 3] = -1.0;
                } else {
                    ds[k * 4 + 3] = -1.0;
                }
            }
        }

        for (int m = 0; m < size; m++) {
            double r = ds[m * 4 + 3];
            if (r < 0.0) continue;
            double cx = ds[m * 4];
            double cy = ds[m * 4 + 1];
            double cz = ds[m * 4 + 2];
            int x0 = Math.max(MathHelper.floor(cx - r), minX);
            int y0 = Math.max(MathHelper.floor(cy - r), minY);
            int z0 = Math.max(MathHelper.floor(cz - r), minZ);
            int x1 = Math.max(MathHelper.floor(cx + r), x0);
            int y1 = Math.max(MathHelper.floor(cy + r), y0);
            int z1 = Math.max(MathHelper.floor(cz + r), z0);
            for (int x = x0; x <= x1; x++) {
                double u = ((double) x + 0.5 - cx) / r;
                if (!(u * u < 1.0)) continue;
                for (int y = y0; y <= y1; y++) {
                    double v = ((double) y + 0.5 - cy) / r;
                    if (!(u * u + v * v < 1.0)) continue;
                    for (int z = z0; z <= z1; z++) {
                        double w = ((double) z + 0.5 - cz) / r;
                        if (!(u * u + v * v + w * w < 1.0) || y < 0 || y > 255) continue;
                        out.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }
    }
}
