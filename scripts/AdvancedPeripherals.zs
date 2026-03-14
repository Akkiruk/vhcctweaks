/* Advanced Peripherals recipe overrides for Vault Hunters
   All recipes gated behind VH materials to match modpack balance.
   AP is a 2-star research — basic peripherals use chromatic iron + larimar,
   powerful peripherals use chromatic steel + perfect larimar,
   automata cores use chromatic steel + vault diamond.
   scripts made by Douwsky / VHCCTweaks
   https://github.com/Akkiruk/vhcctweaks */

// Remove all default Advanced Peripherals recipes — our VH versions replace them below
craftingTable.removeByModid("advancedperipherals");

var chrIron = <item:the_vault:chromatic_iron_ingot>;
var chrSteel = <item:the_vault:chromatic_steel_ingot>;
var larimar = <item:the_vault:gem_larimar>;
var perfectLar = <item:the_vault:perfect_larimar>;
var vaultDiamond = <item:the_vault:vault_diamond>;
var air = <item:minecraft:air>;
var iron = <tag:items:forge:ingots/iron>;
var redstoneBlock = <tag:items:forge:storage_blocks/redstone>;
var casing = <item:advancedperipherals:peripheral_casing>;

// ============================================================
//  BASE COMPONENT
// ============================================================

// Peripheral Casing — base crafting component for all AP peripherals
craftingTable.addShaped("vhcc_peripheral_casing", <item:advancedperipherals:peripheral_casing>, [
    [iron, <item:minecraft:iron_bars>, iron],
    [<item:minecraft:iron_bars>, chrIron, <item:minecraft:iron_bars>],
    [iron, <item:minecraft:iron_bars>, iron]
]);

// ============================================================
//  BASIC PERIPHERALS — Chromatic Iron + Larimar
// ============================================================

// Chat Box — communication peripheral
craftingTable.addShaped("vhcc_chat_box", <item:advancedperipherals:chat_box>, [
    [<tag:items:minecraft:logs>, larimar, <tag:items:minecraft:logs>],
    [<tag:items:minecraft:logs>, casing, <tag:items:minecraft:logs>],
    [<tag:items:minecraft:logs>, chrIron, <tag:items:minecraft:logs>]
]);

// Player Detector — detects nearby players
craftingTable.addShaped("vhcc_player_detector", <item:advancedperipherals:player_detector>, [
    [<item:minecraft:smooth_stone>, chrIron, <item:minecraft:smooth_stone>],
    [chrIron, casing, chrIron],
    [<item:minecraft:smooth_stone>, larimar, <item:minecraft:smooth_stone>]
]);

// Redstone Integrator — redstone I/O
craftingTable.addShaped("vhcc_redstone_integrator", <item:advancedperipherals:redstone_integrator>, [
    [<item:minecraft:redstone>, chrIron, <item:minecraft:redstone>],
    [<item:minecraft:comparator>, casing, <item:minecraft:comparator>],
    [<item:minecraft:redstone>, larimar, <item:minecraft:redstone>]
]);

// Environment Detector — reads biome/weather/time
craftingTable.addShaped("vhcc_environment_detector", <item:advancedperipherals:environment_detector>, [
    [<tag:items:minecraft:leaves>, <tag:items:minecraft:saplings>, <tag:items:minecraft:leaves>],
    [<tag:items:minecraft:saplings>, casing, <tag:items:minecraft:saplings>],
    [chrIron, larimar, chrIron]
]);

// Computer Tool — used to configure peripherals
craftingTable.addShaped("vhcc_computer_tool", <item:advancedperipherals:computer_tool>, [
    [air, chrIron, air],
    [air, <item:minecraft:blue_terracotta>, air],
    [air, iron, air]
]);

// AR Goggles — wearable display
craftingTable.addShaped("vhcc_ar_goggles", <item:advancedperipherals:ar_goggles>, [
    [chrIron, larimar, chrIron],
    [<tag:items:forge:glass/black>, <tag:items:forge:rods/wooden>, <tag:items:forge:glass/black>],
    [air, air, air]
]);

// Memory Card — data storage
craftingTable.addShaped("vhcc_memory_card", <item:advancedperipherals:memory_card>, [
    [iron, <tag:items:forge:glass/white>, iron],
    [iron, <item:minecraft:observer>, iron],
    [air, larimar, air]
]);

// ============================================================
//  MID-TIER PERIPHERALS — Chromatic Iron + Perfect Larimar
// ============================================================

// Inventory Manager — container access, quite powerful
craftingTable.addShaped("vhcc_inventory_manager", <item:advancedperipherals:inventory_manager>, [
    [iron, <item:minecraft:chest>, iron],
    [chrIron, casing, chrIron],
    [iron, perfectLar, iron]
]);

// NBT Storage — data persistence, mid-tier
craftingTable.addShaped("vhcc_nbt_storage", <item:advancedperipherals:nbt_storage>, [
    [iron, redstoneBlock, iron],
    [<item:minecraft:chest>, casing, <item:minecraft:chest>],
    [chrIron, perfectLar, chrIron]
]);

// AR Controller — drives AR goggles display
craftingTable.addShaped("vhcc_ar_controller", <item:advancedperipherals:ar_controller>, [
    [<item:minecraft:smooth_stone>, <item:minecraft:ender_pearl>, <item:minecraft:smooth_stone>],
    [chrIron, casing, chrIron],
    [<item:minecraft:smooth_stone>, perfectLar, <item:minecraft:smooth_stone>]
]);

// ============================================================
//  POWERFUL PERIPHERALS — Chromatic Steel + Perfect Larimar
// ============================================================

// Energy Detector — energy flow monitoring
craftingTable.addShaped("vhcc_energy_detector", <item:advancedperipherals:energy_detector>, [
    [<item:minecraft:redstone_torch>, chrSteel, <item:minecraft:redstone_torch>],
    [<item:minecraft:comparator>, casing, <item:minecraft:comparator>],
    [redstoneBlock, perfectLar, redstoneBlock]
]);

// Block Reader — reads block NBT data
craftingTable.addShaped("vhcc_block_reader", <item:advancedperipherals:block_reader>, [
    [iron, chrSteel, iron],
    [<item:minecraft:observer>, casing, <item:minecraft:observer>],
    [redstoneBlock, perfectLar, redstoneBlock]
]);

// Geo Scanner — scans underground blocks
craftingTable.addShaped("vhcc_geo_scanner", <item:advancedperipherals:geo_scanner>, [
    [<item:minecraft:diamond>, chrSteel, <item:minecraft:diamond>],
    [<item:minecraft:observer>, casing, <item:minecraft:observer>],
    [redstoneBlock, perfectLar, redstoneBlock]
]);

// Chunk Controller — keeps chunks loaded
craftingTable.addShaped("vhcc_chunk_controller", <item:advancedperipherals:chunk_controller>, [
    [chrSteel, <item:minecraft:ender_eye>, chrSteel],
    [<item:minecraft:ender_eye>, perfectLar, <item:minecraft:ender_eye>],
    [chrSteel, <item:minecraft:ender_eye>, chrSteel]
]);

// ============================================================
//  BRIDGE PERIPHERALS — require mod-specific + chromatic steel
// ============================================================

// ME Bridge — AE2 integration
craftingTable.addShaped("vhcc_me_bridge", <item:advancedperipherals:me_bridge>, [
    [<item:ae2:fluix_block>, chrSteel, <item:ae2:fluix_block>],
    [<item:ae2:interface>, casing, <item:ae2:interface>],
    [<item:ae2:fluix_block>, perfectLar, <item:ae2:fluix_block>]
]);

// RS Bridge — Refined Storage integration
craftingTable.addShaped("vhcc_rs_bridge", <item:advancedperipherals:rs_bridge>, [
    [<item:refinedstorage:quartz_enriched_iron>, chrSteel, <item:refinedstorage:quartz_enriched_iron>],
    [<item:refinedstorage:interface>, casing, <item:refinedstorage:interface>],
    [<item:refinedstorage:quartz_enriched_iron>, perfectLar, <item:refinedstorage:quartz_enriched_iron>]
]);

// ============================================================
//  AUTOMATA CORES — Chromatic Steel + Vault Diamond (highest tier)
// ============================================================

// Weak Automata Core — base automata
craftingTable.addShaped("vhcc_weak_automata_core", <item:advancedperipherals:weak_automata_core>, [
    [redstoneBlock, casing, redstoneBlock],
    [chrSteel, <item:minecraft:soul_lantern>, chrSteel],
    [redstoneBlock, vaultDiamond, redstoneBlock]
]);

// Overpowered Weak Automata Core — weak + nether star
craftingTable.addShapeless("vhcc_overpowered_weak_automata_core", <item:advancedperipherals:overpowered_weak_automata_core>, [
    <item:advancedperipherals:weak_automata_core>, <item:minecraft:nether_star>
]);

// Overpowered End Automata Core — end automata + nether star
craftingTable.addShapeless("vhcc_overpowered_end_automata_core", <item:advancedperipherals:overpowered_end_automata_core>, [
    <item:advancedperipherals:end_automata_core>, <item:minecraft:nether_star>
]);

// Overpowered Husbandry Automata Core — husbandry automata + nether star
craftingTable.addShapeless("vhcc_overpowered_husbandry_automata_core", <item:advancedperipherals:overpowered_husbandry_automata_core>, [
    <item:advancedperipherals:husbandry_automata_core>, <item:minecraft:nether_star>
]);
