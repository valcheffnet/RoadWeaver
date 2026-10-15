/* 文件职责：定义精确高度批量采样的实时进度回调边界。 */
package net.shiroha233.roadweaver.pathfinding.cache;

/**
 * 区域级精确采样进度接收端。
 */
@FunctionalInterface
public interface AccurateSamplingProgress {
    AccurateSamplingProgress NONE = batch -> {};

    void onBatch(Batch batch);

    record Batch(long completedColumns,
                 long totalColumns,
                 int batchColumns,
                 long elapsedNanos,
                 long batchNanos,
                 long kernelNanos) {}
}
