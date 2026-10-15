package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 粗高度批量采样器入口。
 */
public final class CoarseHeightBatchSamplers {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final AtomicBoolean FALLBACK_LOGGED = new AtomicBoolean();

    private CoarseHeightBatchSamplers() {}

    public static CoarseHeightBatchSampler create(ServerLevel level, CoarseHeightBatchRequest request) {
        InitialGenerationProgressTracker.setBackend("CPU", "CPU", null);
        if (FALLBACK_LOGGED.compareAndSet(false, true)) {
            LOGGER.info("粗高度采样使用 CPU");
        }
        return CpuCoarseHeightBatchSampler.create(level);
    }

}