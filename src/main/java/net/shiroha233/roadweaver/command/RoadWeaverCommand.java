/* 文件职责：服务端专用分支的管理命令（无 GUI 时重新加载配置与预设）。 */
package net.shiroha233.roadweaver.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.PresetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * /roadweaver reload: re-reads config/roadweaver/roadweaver.json and the road presets.
 * Replaces the client config screens of upstream for a server without the mod on clients.
 */
public final class RoadWeaverCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private RoadWeaverCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("roadweaver")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource()))));
    }

    private static int reload(CommandSourceStack source) {
        try {
            ConfigService.load();
            PresetService.reload();
        } catch (RuntimeException e) {
            LOGGER.warn("RoadWeaver reload failed", e);
            source.sendFailure(Component.literal("RoadWeaver reload failed, see the server log."));
            return 0;
        }
        int presets = PresetService.getAllPresets().size();
        source.sendSuccess(() -> Component.literal("RoadWeaver config reloaded (" + presets
                + " road presets). New settings apply to roads planned from now on. Thread counts need a restart."), true);
        return 1;
    }
}
