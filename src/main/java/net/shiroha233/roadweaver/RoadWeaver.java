package net.shiroha233.roadweaver;

import net.fabricmc.api.ModInitializer;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.features.config.RoadFeatureRegistry;
import net.shiroha233.roadweaver.persistence.attachments.WorldDataAttachment;
import net.shiroha233.roadweaver.planning.fabric.ServerPlanningHooks;
import net.shiroha233.roadweaver.structures.fabric.StructureRegistryFabric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RoadWeaver 模组主类 (Fabric, server-side only)
 */
public class RoadWeaver implements ModInitializer {
    public static final String MOD_ID = "roadweaver";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing RoadWeaver (Fabric)...");
        
        WorldDataAttachment.registerWorldDataAttachment();
        
        ConfigService.load();
        LOGGER.info("Configuration loaded");
        
        StructureRegistryFabric.register();
        LOGGER.info("Structure types registered");
        
        RoadFeatureRegistry.register();
        
        ServerPlanningHooks.register();
    }
}
