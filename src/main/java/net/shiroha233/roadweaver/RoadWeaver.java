package net.shiroha233.roadweaver;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.shiroha233.roadweaver.command.MapAccessCommand;
import net.shiroha233.roadweaver.command.RoadWeaverCommand;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.features.config.RoadFeatureRegistry;
import net.shiroha233.roadweaver.network.neoforge.MapNetworkNeoForge;
import net.shiroha233.roadweaver.persistence.attachments.WorldDataAttachment;
import net.shiroha233.roadweaver.planning.neoforge.ServerPlanningHooks;
import net.shiroha233.roadweaver.structures.neoforge.StructureRegistryNeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RoadWeaver 模组主类 (NeoForge)
 */
@Mod(RoadWeaver.MOD_ID)
public class RoadWeaver {
    public static final String MOD_ID = "roadweaver";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public RoadWeaver(IEventBus modBus) {
        LOGGER.info("Initializing RoadWeaver (NeoForge)...");

        WorldDataAttachment.register(modBus);

        ConfigService.load();
        LOGGER.info("Configuration loaded");

        StructureRegistryNeoForge.register(modBus);
        RoadFeatureRegistry.register(modBus);
        MapNetworkNeoForge.register(modBus);

        NeoForge.EVENT_BUS.addListener(RoadWeaver::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(RoadWeaver::onPlayerLoggedIn);

        ServerPlanningHooks.register();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        RoadWeaverCommand.register(event.getDispatcher());
        MapAccessCommand.register(event.getDispatcher());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MapNetworkNeoForge.syncMapAccess(player);
        }
    }
}
