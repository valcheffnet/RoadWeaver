package net.shiroha233.roadweaver.persistence.fabric;

import net.shiroha233.roadweaver.persistence.WorldDataProvider;

/**
 * Fabric world data provider instance, returned by WorldDataProvider.getInstance()
 */
public final class WorldDataProviderImpl {
    private static final WorldDataProvider INSTANCE = new FabricWorldDataProvider();

    public static WorldDataProvider getInstance() {
        return INSTANCE;
    }
}
