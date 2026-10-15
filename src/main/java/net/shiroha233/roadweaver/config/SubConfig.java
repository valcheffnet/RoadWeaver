package net.shiroha233.roadweaver.config;

/**
 * 子配置基础接口，定义配置校验和快照能力
 */
public interface SubConfig {
    /** 校验并修正越界值到合法范围 */
    void sanitize();

    /** 返回当前配置的不可变快照副本 */
    SubConfig snapshot();
}
