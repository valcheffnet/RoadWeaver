package net.shiroha233.roadweaver.client.loading;

import net.shiroha233.roadweaver.client.loading.neoforge.OpenMapKeyTextBridgeImpl;

/**
 * 加载提示中“打开地图”按键文案的跨平台桥接。
 */
public final class OpenMapKeyTextBridge {
    private OpenMapKeyTextBridge() {
    }

    public static String getDisplayText() {
        return OpenMapKeyTextBridgeImpl.getDisplayText();
    }
}