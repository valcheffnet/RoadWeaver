package net.shiroha233.roadweaver.client.neoforge;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.client.debug.RawPathDebugOverlay;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes;
import net.shiroha233.roadweaver.client.map.data.MapDataStorage;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.tile.ClientMapTileTextureCache;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import net.shiroha233.roadweaver.network.neoforge.MapNetworkNeoForgeClient;
import org.lwjgl.glfw.GLFW;

/**
 * NeoForge 客户端初始化
 */
@Mod(value = RoadWeaver.MOD_ID, dist = Dist.CLIENT)
public class ClientInit {
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(RoadWeaver.MOD_ID, "roadweaver"));
    public static final KeyMapping OPEN_MAP = new KeyMapping("key.roadweaver.open_map", GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping DEBUG_RAW_PATHS =
            new KeyMapping("key.roadweaver.debug_raw_paths", GLFW.GLFW_KEY_F9, CATEGORY);

    public ClientInit(IEventBus modBus, ModContainer container) {
        MapNetworkNeoForgeClient.register(modBus);
        modBus.addListener(ClientInit::onRegisterKeyMappings);

        NeoForge.EVENT_BUS.addListener(ClientInit::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientInit::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(ClientInit::onClientTick);
        NeoForge.EVENT_BUS.addListener(RawPathDebugOverlay::render);

        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> ConfigScreenFactoryImpl.createConfigScreen(parent));

        // Structure discovery reads the registries of the world this client has loaded.
        StructureDiscoveryService.setClientRegistryAccess(() -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.level != null ? mc.level.registryAccess() : null;
        });
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_MAP);
        event.register(DEBUG_RAW_PATHS);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft client = Minecraft.getInstance();
        RawPathDebugOverlay.reset();
        ClientMapAccessGuard.reset();
        MapSnapshotCache.setCurrentWorldId(MapDataStorage.getWorldId());
        MapSnapshotCache.clearNow();
        ClientMapTileTextureCache.clear(client);
        ClientMapNotes.onWorldJoin();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft client = Minecraft.getInstance();
        RawPathDebugOverlay.reset();
        ClientMapAccessGuard.reset();
        MapSnapshotCache.clearNow();
        MapSnapshotCache.setCurrentWorldId(null);
        ClientMapTileTextureCache.clear(client);
        ClientMapNotes.onWorldLeave();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (client.gui.screen() == null) {
            while (DEBUG_RAW_PATHS.consumeClick()) RawPathDebugOverlay.toggle(client);
        }
        RawPathDebugOverlay.tick(client);
        if (client.gui.screen() instanceof RoadMapScreen) return;
        while (OPEN_MAP.consumeClick()) {
            if (!ClientMapAccessGuard.canOpen(client)) {
                continue;
            }
            client.gui.setScreen(new RoadMapScreen());
        }
    }
}
