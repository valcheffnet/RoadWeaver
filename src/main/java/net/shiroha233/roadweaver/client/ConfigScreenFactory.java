package net.shiroha233.roadweaver.client;

import net.shiroha233.roadweaver.client.neoforge.ConfigScreenFactoryImpl;
import net.minecraft.client.gui.screens.Screen;

/**
 * 配置屏幕工厂 - 平台抽象接口
 * 使用 Architectury 的 ExpectPlatform 机制实现跨平台调用
 */
public class ConfigScreenFactory {
    
    /**
     * 创建配置屏幕
     * @param parent 父屏幕
     * @return 配置屏幕实例
     */
    public static Screen createConfigScreen(Screen parent) {
        return ConfigScreenFactoryImpl.createConfigScreen(parent);
    }
}
