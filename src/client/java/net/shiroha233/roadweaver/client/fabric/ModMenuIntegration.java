package net.shiroha233.roadweaver.client.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Opens the RoadWeaver settings from the Mod Menu list. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreenFactoryImpl::createConfigScreen;
    }
}
