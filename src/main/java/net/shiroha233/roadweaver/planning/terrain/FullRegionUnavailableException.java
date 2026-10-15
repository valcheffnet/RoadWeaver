/* 文件职责：表示全区域精采无法安全使用 GPU 完成并应触发会话级策略降级。 */
package net.shiroha233.roadweaver.planning.terrain;

final class FullRegionUnavailableException extends RuntimeException {
    FullRegionUnavailableException(String message) {
        super(message);
    }

    FullRegionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
