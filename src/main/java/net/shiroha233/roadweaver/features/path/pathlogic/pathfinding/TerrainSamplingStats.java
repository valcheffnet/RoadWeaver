package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局地形采样统计：线程安全地汇总所有 TerrainSamplingCache 实例的缓存命中率和噪声采样数。
 * 用于在初始道路生成界面显示实时性能数据。
 */
public final class TerrainSamplingStats {
    private TerrainSamplingStats() {}

    // 缓存请求总数（包括命中和未命中）
    private static final AtomicLong totalRequests = new AtomicLong(0);
    // 缓存命中次数
    private static final AtomicLong cacheHits = new AtomicLong(0);
    // 噪声采样次数（实际调用生成器的次数，即缓存未命中时）
    private static final AtomicLong noiseSamples = new AtomicLong(0);
    
    // 用于计算每秒采样数的时间戳和快照
    private static volatile long lastSnapshotTimeMs = 0;
    private static volatile long lastSnapshotNoiseSamples = 0;
    private static volatile double samplesPerSecond = 0.0;

    /**
     * 记录一次缓存请求（命中）
     */
    public static void recordCacheHit() {
        totalRequests.incrementAndGet();
        cacheHits.incrementAndGet();
    }

    /**
     * 记录一次缓存请求（未命中，需要噪声采样）
     */
    public static void recordCacheMiss() {
        totalRequests.incrementAndGet();
        noiseSamples.incrementAndGet();
    }

    /**
     * 获取缓存命中率（0.0 ~ 1.0）
     */
    public static double getCacheHitRate() {
        long total = totalRequests.get();
        if (total == 0) return 0.0;
        return (double) cacheHits.get() / total;
    }

    /**
     * 获取缓存命中率百分比（0 ~ 100）
     */
    public static int getCacheHitRatePercent() {
        return (int) Math.round(getCacheHitRate() * 100);
    }

    /**
     * 获取总噪声采样数
     */
    public static long getTotalNoiseSamples() {
        return noiseSamples.get();
    }

    /**
     * 更新并获取每秒噪声采样数（需要定期调用以刷新）
     */
    public static double updateAndGetSamplesPerSecond() {
        long now = System.currentTimeMillis();
        long currentSamples = noiseSamples.get();
        
        if (lastSnapshotTimeMs == 0) {
            // 首次调用，初始化快照
            lastSnapshotTimeMs = now;
            lastSnapshotNoiseSamples = currentSamples;
            return 0.0;
        }
        
        long elapsedMs = now - lastSnapshotTimeMs;
        if (elapsedMs >= 500) { // 每 500ms 更新一次
            long samplesDelta = currentSamples - lastSnapshotNoiseSamples;
            samplesPerSecond = (samplesDelta * 1000.0) / elapsedMs;
            lastSnapshotTimeMs = now;
            lastSnapshotNoiseSamples = currentSamples;
        }
        
        return samplesPerSecond;
    }

    /**
     * 获取每秒噪声采样数（不更新，仅读取上次计算结果）
     */
    public static double getSamplesPerSecond() {
        return samplesPerSecond;
    }

    /**
     * 重置所有统计数据（在初始生成开始时调用）
     */
    public static void reset() {
        totalRequests.set(0);
        cacheHits.set(0);
        noiseSamples.set(0);
        lastSnapshotTimeMs = 0;
        lastSnapshotNoiseSamples = 0;
        samplesPerSecond = 0.0;
    }
}
