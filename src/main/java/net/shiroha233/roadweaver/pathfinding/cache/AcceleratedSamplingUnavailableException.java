/* 文件职责：表达强制加速采样无法继续且不得回退 CPU 的失败。 */
package net.shiroha233.roadweaver.pathfinding.cache;

/**
 * accelerated-only 采样失败。
 */
public final class AcceleratedSamplingUnavailableException extends RuntimeException {
    public AcceleratedSamplingUnavailableException(String message) {
        super(message);
    }

    public AcceleratedSamplingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
