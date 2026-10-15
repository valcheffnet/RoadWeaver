/* 文件职责：承载与世界生成身份绑定的稳定持久化指纹。 */
package net.shiroha233.roadweaver.persistence;

/**
 * 可跨持久化模块复用的世界生成指纹值对象。
 */
public record WorldgenFingerprint(String namespace, int schemaVersion, int dataVersion) {
    public WorldgenFingerprint {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        if (dataVersion < 0) {
            throw new IllegalArgumentException("dataVersion must not be negative");
        }
    }
}
