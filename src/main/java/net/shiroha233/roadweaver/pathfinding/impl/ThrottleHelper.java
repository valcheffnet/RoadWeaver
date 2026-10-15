package net.shiroha233.roadweaver.pathfinding.impl;

import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 基于 ThreadLocal 的 CPU 节流工具，控制寻路线程占空比
 */
public final class ThrottleHelper {
    private ThrottleHelper() {}

    private static final ThreadLocal<Long> WORK_START = ThreadLocal.withInitial(System::currentTimeMillis);

    public static void throttle(int duty) {
        if (duty >= RoadConstants.DUTY_CYCLE_MAX) return;
        if (duty <= 0) duty = RoadConstants.DEFAULT_DUTY_CYCLE;
        long now = System.currentTimeMillis();
        long elapsed = now - WORK_START.get();
        if (elapsed >= RoadConstants.WORK_PERIOD_MS) {
            long sleepMs = (long) (RoadConstants.WORK_PERIOD_MS * (100.0 - duty) / duty);
            if (sleepMs > 0) {
                try {
                    Thread.sleep(Math.min(sleepMs, RoadConstants.MAX_SLEEP_MS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            WORK_START.set(System.currentTimeMillis());
        }
    }

    public static void resetThrottle() {
        WORK_START.set(System.currentTimeMillis());
    }

    public static void clearThrottle() {
        WORK_START.remove();
    }
}
