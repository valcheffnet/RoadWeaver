package net.shiroha233.roadweaver.structures.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.structures.pieces.ModStructurePieceTypes;
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece;
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes;
import net.shiroha233.roadweaver.structures.types.BridgeTemplateStructure;
import net.shiroha233.roadweaver.structures.types.RoadsideStructure;
import net.shiroha233.roadweaver.structures.types.RoadsideVillageStructure;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;

/**
 * NeoForge 结构注册
 */
public final class StructureRegistryNeoForge {
    private StructureRegistryNeoForge() {}
    
    /** Built-in registries are frozen on NeoForge; entries go in through the RegisterEvent. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(Registries.STRUCTURE_PIECE)) registerStructurePieceTypes(event);
            if (event.getRegistryKey().equals(Registries.STRUCTURE_TYPE)) registerStructureTypes(event);
        });
    }
    
    private static void registerStructurePieceTypes(RegisterEvent event) {
        StructurePieceType simpleTemplate = (StructurePieceType.StructureTemplateType) SimpleTemplatePiece::new;
        
        event.register(Registries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(RoadWeaver.MOD_ID, "simple_template"), () -> simpleTemplate);
        
        ModStructurePieceTypes.setSimpleTemplate(simpleTemplate);
    }
    
    private static void registerStructureTypes(RegisterEvent event) {
        StructureType<RoadsideStructure> roadsideType = () -> RoadsideStructure.CODEC;
        event.register(Registries.STRUCTURE_TYPE, Identifier.fromNamespaceAndPath(RoadWeaver.MOD_ID, "roadside"), () -> roadsideType);
        ModStructureTypes.setRoadside(roadsideType);

        StructureType<RoadsideVillageStructure> roadsideVillageType = () -> RoadsideVillageStructure.CODEC;
        event.register(Registries.STRUCTURE_TYPE, Identifier.fromNamespaceAndPath(RoadWeaver.MOD_ID, "roadside_village"), () -> roadsideVillageType);
        ModStructureTypes.setRoadsideVillage(roadsideVillageType);
        
        StructureType<SpawnCabinStructure> spawnCabinType = () -> SpawnCabinStructure.CODEC;
        event.register(Registries.STRUCTURE_TYPE, Identifier.fromNamespaceAndPath(RoadWeaver.MOD_ID, "spawn_cabin"), () -> spawnCabinType);
        ModStructureTypes.setSpawnCabin(spawnCabinType);

        StructureType<BridgeTemplateStructure> bridgeTemplateType = () -> BridgeTemplateStructure.CODEC;
        event.register(Registries.STRUCTURE_TYPE, Identifier.fromNamespaceAndPath(RoadWeaver.MOD_ID, "bridge"), () -> bridgeTemplateType);
        ModStructureTypes.setBridge(bridgeTemplateType);
    }
}
