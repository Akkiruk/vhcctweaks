/* CC:Tweaked recipe overrides for Vault Hunters
   All recipes gated behind VH materials to match modpack balance.
   CC is a 2-star research — basic tier uses chromatic iron + larimar,
   advanced tier uses chromatic steel + perfect larimar.
   scripts made by Douwsky / VHCCTweaks
   https://github.com/Akkiruk/vhcctweaks */

// Remove all default CC:Tweaked recipes — our VH versions replace them below
craftingTable.removeByModid("computercraft");

// ===== Variables for readability =====
var chrIron = <item:the_vault:chromatic_iron_ingot>;
var chrSteel = <item:the_vault:chromatic_steel_ingot>;
var larimar = <item:the_vault:gem_larimar>;
var perfectLar = <item:the_vault:perfect_larimar>;
var air = <item:minecraft:air>;
var stone = <item:minecraft:stone>;
var redstone = <item:minecraft:redstone>;
var gold = <item:minecraft:gold_ingot>;
var iron = <item:minecraft:iron_ingot>;
var glass = <item:minecraft:glass_pane>;
var paper = <item:minecraft:paper>;

// ============================================================
//  BASIC TIER — Chromatic Iron + Larimar
// ============================================================

// Computer Normal — the gateway item
craftingTable.addShaped("vhcc_computer_normal", <item:computercraft:computer_normal>, [
    [stone, larimar, stone],
    [chrIron, redstone, chrIron],
    [stone, chrIron, stone]
]);

// Monitor Normal (1)
craftingTable.addShaped("vhcc_monitor_normal", <item:computercraft:monitor_normal>, [
    [chrIron, glass, chrIron],
    [glass, larimar, glass],
    [chrIron, glass, chrIron]
]);

// Speaker
craftingTable.addShaped("vhcc_speaker", <item:computercraft:speaker>, [
    [stone, chrIron, stone],
    [chrIron, <item:minecraft:note_block>, chrIron],
    [stone, larimar, stone]
]);

// Printer
craftingTable.addShaped("vhcc_printer", <item:computercraft:printer>, [
    [stone, chrIron, stone],
    [<item:minecraft:ink_sac>, redstone, <item:minecraft:ink_sac>],
    [stone, larimar, stone]
]);

// Disk Drive
craftingTable.addShaped("vhcc_disk_drive", <item:computercraft:disk_drive>, [
    [stone, chrIron, stone],
    [chrIron, redstone, chrIron],
    [stone, larimar, stone]
]);

// Wired Modem
craftingTable.addShaped("vhcc_wired_modem", <item:computercraft:wired_modem>, [
    [stone, chrIron, stone],
    [redstone, larimar, redstone],
    [stone, chrIron, stone]
]);

// Wireless Modem Normal
craftingTable.addShaped("vhcc_wireless_modem_normal", <item:computercraft:wireless_modem_normal>, [
    [stone, chrIron, stone],
    [chrIron, <item:minecraft:ender_pearl>, chrIron],
    [stone, larimar, stone]
]);

// Cable (6)
craftingTable.addShaped("vhcc_cable", <item:computercraft:cable> * 6, [
    [air, stone, air],
    [stone, chrIron, stone],
    [air, stone, air]
]);

// Turtle Normal
craftingTable.addShaped("vhcc_turtle_normal", <item:computercraft:turtle_normal>, [
    [chrIron, iron, chrIron],
    [iron, <item:computercraft:computer_normal>, iron],
    [chrIron, <item:minecraft:chest>, chrIron]
]);

// Pocket Computer Normal
craftingTable.addShaped("vhcc_pocket_normal", <item:computercraft:pocket_computer_normal>, [
    [stone, chrIron, stone],
    [stone, glass, stone],
    [stone, larimar, stone]
]);

// Floppy Disk — cheap, just needs chromatic iron nugget
craftingTable.addShaped("vhcc_disk", <item:computercraft:disk>, [
    [air, air, air],
    [<item:the_vault:chromatic_iron_nugget>, paper, air],
    [<item:the_vault:chromatic_iron_nugget>, redstone, air]
]);

// Wired Modem Full — conversion from regular wired modem
craftingTable.addShapeless("vhcc_wired_modem_full_to", <item:computercraft:wired_modem_full>, [
    <item:computercraft:wired_modem>
]);
craftingTable.addShapeless("vhcc_wired_modem_full_from", <item:computercraft:wired_modem>, [
    <item:computercraft:wired_modem_full>
]);

// Printed Page — paper output from printer (shapeless)
craftingTable.addShapeless("vhcc_printout", <item:computercraft:printed_page>, [
    paper, <item:minecraft:ink_sac>, <item:the_vault:chromatic_iron_nugget>
]);

// Printed Pages — combine printed pages
craftingTable.addShapeless("vhcc_printed_pages", <item:computercraft:printed_pages>, [
    <item:computercraft:printed_page>, <item:computercraft:printed_page>, <item:minecraft:string>
]);

// Printed Book — bind pages into a book
craftingTable.addShapeless("vhcc_printed_book", <item:computercraft:printed_book>, [
    <item:minecraft:leather>, <item:computercraft:printed_page>, <item:minecraft:string>
]);

// ============================================================
//  ADVANCED TIER — Chromatic Steel + Perfect Larimar
// ============================================================

// Computer Advanced — direct craft
craftingTable.addShaped("vhcc_computer_advanced", <item:computercraft:computer_advanced>, [
    [gold, perfectLar, gold],
    [chrSteel, redstone, chrSteel],
    [gold, chrSteel, gold]
]);

// Computer Advanced — upgrade from normal
craftingTable.addShaped("vhcc_computer_advanced_upgrade", <item:computercraft:computer_advanced>, [
    [chrSteel, gold, chrSteel],
    [gold, <item:computercraft:computer_normal>, gold],
    [chrSteel, perfectLar, chrSteel]
]);

// Monitor Advanced (1)
craftingTable.addShaped("vhcc_monitor_advanced", <item:computercraft:monitor_advanced>, [
    [chrSteel, glass, chrSteel],
    [glass, perfectLar, glass],
    [chrSteel, glass, chrSteel]
]);

// Wireless Modem Advanced (Ender Modem)
craftingTable.addShaped("vhcc_wireless_modem_advanced", <item:computercraft:wireless_modem_advanced>, [
    [gold, chrSteel, gold],
    [chrSteel, <item:minecraft:ender_eye>, chrSteel],
    [gold, perfectLar, gold]
]);

// Turtle Advanced — direct craft
craftingTable.addShaped("vhcc_turtle_advanced", <item:computercraft:turtle_advanced>, [
    [chrSteel, gold, chrSteel],
    [gold, <item:computercraft:computer_advanced>, gold],
    [chrSteel, <item:minecraft:chest>, chrSteel]
]);

// Turtle Advanced — upgrade from normal turtle
craftingTable.addShaped("vhcc_turtle_advanced_upgrade", <item:computercraft:turtle_advanced>, [
    [chrSteel, gold, chrSteel],
    [gold, <item:computercraft:turtle_normal>, gold],
    [chrSteel, perfectLar, chrSteel]
]);

// Pocket Computer Advanced — direct craft
craftingTable.addShaped("vhcc_pocket_advanced", <item:computercraft:pocket_computer_advanced>, [
    [gold, chrSteel, gold],
    [gold, glass, gold],
    [gold, perfectLar, gold]
]);

// Pocket Computer Advanced — upgrade from normal pocket
craftingTable.addShaped("vhcc_pocket_advanced_upgrade", <item:computercraft:pocket_computer_advanced>, [
    [chrSteel, gold, chrSteel],
    [gold, <item:computercraft:pocket_computer_normal>, gold],
    [chrSteel, perfectLar, chrSteel]
]);
