/* 文件职责：验证世界生成指纹在相同输入下稳定、在关键世界生成输入变化时发生变化。 */
package net.shiroha233.roadweaver.persistence;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WorldgenFingerprintServiceTest {
    private static HolderLookup.Provider registries;
    private static int dataVersion;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createLookup();
        dataVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    @Test
    void fingerprintIsStableForSameWorldgenInputs() {
        Holder<NoiseGeneratorSettings> overworld = noiseSettings(NoiseGeneratorSettings.OVERWORLD);

        WorldgenFingerprint first = WorldgenFingerprintService.create(
                Level.OVERWORLD.location(),
                0x5EED_1234_ABCDL,
                "minecraft:noise|test",
                registries,
                overworld,
                dataVersion,
                WorldgenFingerprintService.CURRENT_SCHEMA_VERSION);
        WorldgenFingerprint second = WorldgenFingerprintService.create(
                Level.OVERWORLD.location(),
                0x5EED_1234_ABCDL,
                "minecraft:noise|test",
                registries,
                overworld,
                dataVersion,
                WorldgenFingerprintService.CURRENT_SCHEMA_VERSION);

        assertEquals(first, second);
    }

    @Test
    void fingerprintChangesWhenSeedOrNoiseSettingsChange() {
        Holder<NoiseGeneratorSettings> overworld = noiseSettings(NoiseGeneratorSettings.OVERWORLD);
        Holder<NoiseGeneratorSettings> nether = noiseSettings(NoiseGeneratorSettings.NETHER);

        WorldgenFingerprint baseline = WorldgenFingerprintService.create(
                Level.OVERWORLD.location(),
                12345L,
                "minecraft:noise|test",
                registries,
                overworld,
                dataVersion,
                WorldgenFingerprintService.CURRENT_SCHEMA_VERSION);
        WorldgenFingerprint differentSeed = WorldgenFingerprintService.create(
                Level.OVERWORLD.location(),
                12346L,
                "minecraft:noise|test",
                registries,
                overworld,
                dataVersion,
                WorldgenFingerprintService.CURRENT_SCHEMA_VERSION);
        WorldgenFingerprint differentSettings = WorldgenFingerprintService.create(
                Level.NETHER.location(),
                12345L,
                "minecraft:noise|test",
                registries,
                nether,
                dataVersion,
                WorldgenFingerprintService.CURRENT_SCHEMA_VERSION);

        assertNotEquals(baseline.namespace(), differentSeed.namespace());
        assertNotEquals(baseline.namespace(), differentSettings.namespace());
    }

    private static Holder<NoiseGeneratorSettings> noiseSettings(net.minecraft.resources.ResourceKey<NoiseGeneratorSettings> key) {
        return registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
    }
}
