package com.vhcctweaks.patcher;

import com.google.gson.*;
import com.vhcctweaks.VHCCTweaks;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Patches Vault Hunters config JSON files on startup to add CC:Tweaked entries.
 * This ensures:
 * - computercraft:* is in the vault item/block blacklists
 * - CC:Tweaked has a research gate
 */
public class VaultConfigPatcher {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CC_WILDCARD = "computercraft:*";
    private static final String AP_WILDCARD = "advancedperipherals:*";
    private static final String[] MANAGED_CRAFTTWEAKER_SCRIPTS = {"ComputerCraft.zs", "AdvancedPeripherals.zs"};
    private static final int MONITOR_MAX_WIDTH = 16;
    private static final int MONITOR_MAX_HEIGHT = 16;

    public static void patchIfNeeded(Path configDir) {
        try {
            patchVaultBlacklists(configDir);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.warn("Could not patch vault_general.json: {}", e.getMessage());
        }
        try {
            patchResearches(configDir);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.warn("Could not patch researches.json: {}", e.getMessage());
        }
        try {
            patchResearchGroups(configDir);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.warn("Could not patch researches_groups.json: {}", e.getMessage());
        }
        try {
            patchResearchGuiStyles(configDir);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.warn("Could not patch researches_gui_styles.json: {}", e.getMessage());
        }
        try {
            patchSkillDescriptions(configDir);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.warn("Could not patch skill_descriptions.json: {}", e.getMessage());
        }
        try {
            patchAPConfigs(configDir);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.warn("Could not patch Advanced Peripherals configs: {}", e.getMessage());
        }
    }

    public static void patchGameConfigsIfNeeded(Path gameDir) {
        try {
            patchComputerCraftConfigs(gameDir);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.warn("Could not patch ComputerCraft configs: {}", e.getMessage());
        }
        try {
            syncCraftTweakerScripts(gameDir);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.warn("Could not sync CraftTweaker scripts: {}", e.getMessage());
        }
    }

    private static void patchVaultBlacklists(Path configDir) throws IOException {
        Path path = configDir.resolve("the_vault/vault_general.json");
        if (!Files.exists(path)) {
            VHCCTweaks.LOGGER.info("vault_general.json not found, skipping blacklist patch");
            return;
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
        boolean changed = false;

        // Add to ITEM_BLACKLIST
        if (root.has("ITEM_BLACKLIST")) {
            JsonArray items = root.getAsJsonArray("ITEM_BLACKLIST");
            if (!arrayContains(items, CC_WILDCARD)) {
                items.add(CC_WILDCARD);
                changed = true;
                VHCCTweaks.LOGGER.info("Added {} to vault ITEM_BLACKLIST", CC_WILDCARD);
            }
            if (!arrayContains(items, AP_WILDCARD)) {
                items.add(AP_WILDCARD);
                changed = true;
                VHCCTweaks.LOGGER.info("Added {} to vault ITEM_BLACKLIST", AP_WILDCARD);
            }
        }

        // Add to BLOCK_BLACKLIST
        if (root.has("BLOCK_BLACKLIST")) {
            JsonArray blocks = root.getAsJsonArray("BLOCK_BLACKLIST");
            if (!arrayContains(blocks, CC_WILDCARD)) {
                blocks.add(CC_WILDCARD);
                changed = true;
                VHCCTweaks.LOGGER.info("Added {} to vault BLOCK_BLACKLIST", CC_WILDCARD);
            }
            if (!arrayContains(blocks, AP_WILDCARD)) {
                blocks.add(AP_WILDCARD);
                changed = true;
                VHCCTweaks.LOGGER.info("Added {} to vault BLOCK_BLACKLIST", AP_WILDCARD);
            }
        }

        if (changed) {
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        }
    }

    private static void patchResearches(Path configDir) throws IOException {
        Path path = configDir.resolve("the_vault/researches.json");
        if (!Files.exists(path)) {
            VHCCTweaks.LOGGER.info("researches.json not found, skipping research patch");
            return;
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
        boolean changed = false;

        if (!root.has("MOD_RESEARCHES")) return;
        JsonArray researches = root.getAsJsonArray("MOD_RESEARCHES");

        // Add CC:Tweaked research gate
        if (!hasResearchNamed(researches, "CC: Tweaked")) {
            JsonObject ccResearch = new JsonObject();
            JsonArray modIds = new JsonArray();
            modIds.add("computercraft");
            ccResearch.add("modIds", modIds);

            JsonObject restrictions = new JsonObject();
            JsonObject restricts = new JsonObject();
            restricts.addProperty("HITTABILITY", false);
            restricts.addProperty("BLOCK_INTERACTABILITY", true);
            restricts.addProperty("USABILITY", true);
            restricts.addProperty("CRAFTABILITY", true);
            restricts.addProperty("ENTITY_INTERACTABILITY", false);
            restrictions.add("restricts", restricts);
            ccResearch.add("restrictions", restrictions);

            ccResearch.addProperty("name", "CC: Tweaked");
            ccResearch.addProperty("cost", 2);
            ccResearch.addProperty("usesKnowledge", true);
            researches.add(ccResearch);
            changed = true;
            VHCCTweaks.LOGGER.info("Added 'CC: Tweaked' research (cost 2)");
        }

        // Add Advanced Peripherals research gate
        if (!hasResearchNamed(researches, "Advanced Peripherals")) {
            JsonObject apResearch = new JsonObject();
            JsonArray apModIds = new JsonArray();
            apModIds.add("advancedperipherals");
            apResearch.add("modIds", apModIds);

            JsonObject apRestrictions = new JsonObject();
            JsonObject apRestricts = new JsonObject();
            apRestricts.addProperty("HITTABILITY", false);
            apRestricts.addProperty("BLOCK_INTERACTABILITY", true);
            apRestricts.addProperty("USABILITY", true);
            apRestricts.addProperty("CRAFTABILITY", true);
            apRestricts.addProperty("ENTITY_INTERACTABILITY", false);
            apRestrictions.add("restricts", apRestricts);
            apResearch.add("restrictions", apRestrictions);

            apResearch.addProperty("name", "Advanced Peripherals");
            apResearch.addProperty("cost", 2);
            apResearch.addProperty("usesKnowledge", true);
            researches.add(apResearch);
            changed = true;
            VHCCTweaks.LOGGER.info("Added 'Advanced Peripherals' research (cost 2)");
        }

        if (changed) {
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        }
    }

    private static void patchResearchGroups(Path configDir) throws IOException {
        Path path = configDir.resolve("the_vault/researches_groups.json");
        if (!Files.exists(path)) {
            VHCCTweaks.LOGGER.info("researches_groups.json not found, skipping group patch");
            return;
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();

        if (!root.has("groups")) return;
        JsonObject groups = root.getAsJsonObject("groups");
        if (!groups.has("Handling")) return;

        JsonObject handling = groups.getAsJsonObject("Handling");
        if (!handling.has("research")) return;

        JsonArray research = handling.getAsJsonArray("research");
        boolean changed = false;
        if (!arrayContains(research, "CC: Tweaked")) {
            research.add("CC: Tweaked");
            changed = true;
            VHCCTweaks.LOGGER.info("Added 'CC: Tweaked' to Handling research group");
        }
        if (!arrayContains(research, "Advanced Peripherals")) {
            research.add("Advanced Peripherals");
            changed = true;
            VHCCTweaks.LOGGER.info("Added 'Advanced Peripherals' to Handling research group");
        }
        if (changed) {
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        }
    }



    private static void patchResearchGuiStyles(Path configDir) throws IOException {
        Path path = configDir.resolve("the_vault/researches_gui_styles.json");
        if (!Files.exists(path)) {
            VHCCTweaks.LOGGER.info("researches_gui_styles.json not found, skipping GUI style patch");
            return;
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();

        if (!root.has("styles")) return;
        JsonObject styles = root.getAsJsonObject("styles");

        boolean guiChanged = false;
        if (!styles.has("CC: Tweaked")) {
            JsonObject ccStyle = new JsonObject();
            ccStyle.addProperty("x", 10);
            ccStyle.addProperty("y", 320);
            ccStyle.addProperty("frameType", "RECTANGULAR");
            ccStyle.addProperty("icon", "the_vault:gui/researches/cc_tweaked");
            styles.add("CC: Tweaked", ccStyle);
            guiChanged = true;
            VHCCTweaks.LOGGER.info("Added 'CC: Tweaked' GUI style to researches_gui_styles.json");
        }

        if (!styles.has("Advanced Peripherals")) {
            JsonObject apStyle = new JsonObject();
            apStyle.addProperty("x", 110);
            apStyle.addProperty("y", 320);
            apStyle.addProperty("frameType", "RECTANGULAR");
            apStyle.addProperty("icon", "the_vault:gui/researches/advanced_peripherals");
            styles.add("Advanced Peripherals", apStyle);
            guiChanged = true;
            VHCCTweaks.LOGGER.info("Added 'Advanced Peripherals' GUI style to researches_gui_styles.json");
        }

        if (guiChanged) {
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        }
    }

    private static void patchSkillDescriptions(Path configDir) throws IOException {
        Path path = configDir.resolve("the_vault/skill_descriptions.json");
        if (!Files.exists(path)) {
            VHCCTweaks.LOGGER.info("skill_descriptions.json not found, skipping description patch");
            return;
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();

        if (!root.has("descriptions")) return;
        JsonObject descriptions = root.getAsJsonObject("descriptions");

        boolean descChanged = false;
        if (!descriptions.has("CC: Tweaked")) {
            JsonArray desc = new JsonArray();

            JsonObject line1 = new JsonObject();
            line1.addProperty("text", "Unlocks the ");
            desc.add(line1);

            JsonObject line2 = new JsonObject();
            line2.addProperty("text", "CC: Tweaked ");
            line2.addProperty("color", "yellow");
            desc.add(line2);

            JsonObject line3 = new JsonObject();
            line3.addProperty("text", "mod!\n\nThis mod adds programmable computers and turtle robots to " +
                    "the game! Write Lua programs to automate tasks, control redstone, " +
                    "build monitoring systems and much more. Turtles can mine, move and " +
                    "interact with the world on your behalf. Some features of CC: Tweaked, such as ");
            desc.add(line3);

            JsonObject line4 = new JsonObject();
            line4.addProperty("text", "turtle autocrafting");
            line4.addProperty("color", "gold");
            desc.add(line4);

            JsonObject line5 = new JsonObject();
            line5.addProperty("text", ", requires the ");
            desc.add(line5);

            JsonObject line6 = new JsonObject();
            line6.addProperty("text", "Automatic Genius");
            line6.addProperty("color", "aqua");
            desc.add(line6);

            JsonObject line7 = new JsonObject();
            line7.addProperty("text", " research. CC items cannot be brought into the vaults.");
            desc.add(line7);

            descriptions.add("CC: Tweaked", desc);
            descChanged = true;
            VHCCTweaks.LOGGER.info("Added 'CC: Tweaked' description to skill_descriptions.json");
        }

        if (!descriptions.has("Advanced Peripherals")) {
            JsonArray apDesc = new JsonArray();

            JsonObject ap1 = new JsonObject();
            ap1.addProperty("text", "Unlocks the ");
            apDesc.add(ap1);

            JsonObject ap2 = new JsonObject();
            ap2.addProperty("text", "Advanced Peripherals ");
            ap2.addProperty("color", "yellow");
            apDesc.add(ap2);

            JsonObject ap3 = new JsonObject();
            ap3.addProperty("text", "mod!\n\nThis mod adds powerful peripheral blocks for ");
            apDesc.add(ap3);

            JsonObject ap4 = new JsonObject();
            ap4.addProperty("text", "CC: Tweaked");
            ap4.addProperty("color", "aqua");
            apDesc.add(ap4);

            JsonObject ap5 = new JsonObject();
            ap5.addProperty("text", "! Build player detectors, environment scanners, " +
                    "chat boxes, energy monitors, redstone integrators and more. " +
                    "Several AP features have been ");
            apDesc.add(ap5);

            JsonObject ap6 = new JsonObject();
            ap6.addProperty("text", "disabled");
            ap6.addProperty("color", "gold");
            apDesc.add(ap6);

            JsonObject ap7 = new JsonObject();
            ap7.addProperty("text", " for balance: ");
            apDesc.add(ap7);

            JsonObject ap8 = new JsonObject();
            ap8.addProperty("text", "chunk loading");
            ap8.addProperty("color", "gold");
            apDesc.add(ap8);

            JsonObject ap9 = new JsonObject();
            ap9.addProperty("text", ", ");
            apDesc.add(ap9);

            JsonObject ap10 = new JsonObject();
            ap10.addProperty("text", "ore scanning");
            ap10.addProperty("color", "gold");
            apDesc.add(ap10);

            JsonObject ap10b = new JsonObject();
            ap10b.addProperty("text", ", ");
            apDesc.add(ap10b);

            JsonObject ap10c = new JsonObject();
            ap10c.addProperty("text", "block reading");
            ap10c.addProperty("color", "gold");
            apDesc.add(ap10c);

            JsonObject ap10d = new JsonObject();
            ap10d.addProperty("text", ", ");
            apDesc.add(ap10d);

            JsonObject ap10e = new JsonObject();
            ap10e.addProperty("text", "inventory management");
            ap10e.addProperty("color", "gold");
            apDesc.add(ap10e);

            JsonObject ap11 = new JsonObject();
            ap11.addProperty("text", ", ");
            apDesc.add(ap11);

            JsonObject ap12 = new JsonObject();
            ap12.addProperty("text", "teleportation");
            ap12.addProperty("color", "gold");
            apDesc.add(ap12);

            JsonObject ap12b = new JsonObject();
            ap12b.addProperty("text", ", ");
            apDesc.add(ap12b);

            JsonObject ap12c = new JsonObject();
            ap12c.addProperty("text", "animal capture");
            ap12c.addProperty("color", "gold");
            apDesc.add(ap12c);

            JsonObject ap12d = new JsonObject();
            ap12d.addProperty("text", " and ");
            apDesc.add(ap12d);

            JsonObject ap12e = new JsonObject();
            ap12e.addProperty("text", "message spoofing");
            ap12e.addProperty("color", "gold");
            apDesc.add(ap12e);

            JsonObject ap13 = new JsonObject();
            ap13.addProperty("text", ". Slime chunk detection is ");
            apDesc.add(ap13);

            JsonObject ap14 = new JsonObject();
            ap14.addProperty("text", "blocked");
            ap14.addProperty("color", "gold");
            apDesc.add(ap14);

            JsonObject ap15 = new JsonObject();
            ap15.addProperty("text", " to protect the world seed. Overpowered automata have an increased ");
            apDesc.add(ap15);

            JsonObject ap16 = new JsonObject();
            ap16.addProperty("text", "break chance");
            ap16.addProperty("color", "gold");
            apDesc.add(ap16);

            JsonObject ap17 = new JsonObject();
            ap17.addProperty("text", ". AP items cannot be brought into the vaults.");
            apDesc.add(ap17);

            descriptions.add("Advanced Peripherals", apDesc);
            descChanged = true;
            VHCCTweaks.LOGGER.info("Added 'Advanced Peripherals' description to skill_descriptions.json");
        }

        if (descChanged) {
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        }
    }

    // --- Advanced Peripherals Config Patching ---

    private static void patchAPConfigs(Path configDir) throws IOException {
        Path apDir = configDir.resolve("Advancedperipherals");
        if (!Files.exists(apDir)) {
            VHCCTweaks.LOGGER.info("Advancedperipherals config dir not found, skipping AP config patches");
            return;
        }

        // Patch peripherals.toml
        Path peripheralsToml = apDir.resolve("peripherals.toml");
        if (Files.exists(peripheralsToml)) {
            String content = Files.readString(peripheralsToml, StandardCharsets.UTF_8);
            if (!content.contains("# Patched by VH CC Tweaks")) {
                content = patchTomlBool(content, "enableChunkyTurtle", false);
                content = patchTomlBool(content, "enableBlockReader", false);
                content = patchTomlBool(content, "enableInventoryManager", false);
                content = patchTomlBool(content, "enableGeoScanner", false);
                content = patchTomlBool(content, "disablePocketFuelConsumption", false);
                content += "\n# Patched by VH CC Tweaks\n";
                Files.writeString(peripheralsToml, content, StandardCharsets.UTF_8);
                VHCCTweaks.LOGGER.info("Patched AP peripherals.toml (disabled Chunky Turtle, Block Reader, Inventory Manager, Geo Scanner; enabled pocket fuel)");
            }
        }

        // Patch metaphysics.toml
        Path metaphysicsToml = apDir.resolve("metaphysics.toml");
        if (Files.exists(metaphysicsToml)) {
            String content = Files.readString(metaphysicsToml, StandardCharsets.UTF_8);
            if (!content.contains("# Patched by VH CC Tweaks")) {
                content = patchTomlBool(content, "enableEndAutomataCore", false);
                content = patchTomlBool(content, "enableHusbandryAutomataCore", false);
                content = patchTomlDouble(content, "overpoweredAutomataBreakChance", 0.05);
                content += "\n# Patched by VH CC Tweaks\n";
                Files.writeString(metaphysicsToml, content, StandardCharsets.UTF_8);
                VHCCTweaks.LOGGER.info("Patched AP metaphysics.toml (disabled End Automata, Husbandry Automata, increased break chance to 5%%)");
            }
        }

        // Patch world.toml
        Path worldToml = apDir.resolve("world.toml");
        if (Files.exists(worldToml)) {
            String content = Files.readString(worldToml, StandardCharsets.UTF_8);
            if (!content.contains("# Patched by VH CC Tweaks")) {
                content = patchTomlBool(content, "givePlayerBookOnJoin", false);
                content += "\n# Patched by VH CC Tweaks\n";
                Files.writeString(worldToml, content, StandardCharsets.UTF_8);
                VHCCTweaks.LOGGER.info("Patched AP world.toml (disabled book on join)");
            }
        }
    }

    private static void patchComputerCraftConfigs(Path gameDir) throws IOException {
        Set<Path> configPaths = new LinkedHashSet<>();
        configPaths.add(gameDir.resolve("defaultconfigs").resolve("computercraft-server.toml"));
        configPaths.add(gameDir.resolve("serverconfig").resolve("computercraft-server.toml"));

        Path dedicatedWorldDir = resolveDedicatedWorldDir(gameDir);
        if (dedicatedWorldDir != null) {
            configPaths.add(dedicatedWorldDir.resolve("serverconfig").resolve("computercraft-server.toml"));
        }

        for (Path path : configPaths) {
            patchComputerCraftMonitorTomlUnchecked(path);
        }

        Path savesDir = gameDir.resolve("saves");
        if (!Files.isDirectory(savesDir)) {
            VHCCTweaks.LOGGER.info("No saves directory found, skipping integrated-world ComputerCraft config patches");
            return;
        }

        try (var saves = Files.list(savesDir)) {
            saves.filter(Files::isDirectory)
                    .map(saveDir -> saveDir.resolve("serverconfig").resolve("computercraft-server.toml"))
                    .forEach(VaultConfigPatcher::patchComputerCraftMonitorTomlUnchecked);
        }
    }

    private static Path resolveDedicatedWorldDir(Path gameDir) {
        Path serverProperties = gameDir.resolve("server.properties");
        if (!Files.exists(serverProperties)) {
            Path defaultWorldDir = gameDir.resolve("world");
            return Files.isDirectory(defaultWorldDir) ? defaultWorldDir : null;
        }

        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(serverProperties, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            VHCCTweaks.LOGGER.warn("Could not read {}: {}", serverProperties, e.getMessage());
            return gameDir.resolve("world");
        }

        String levelName = properties.getProperty("level-name", "world").trim();
        if (levelName.isEmpty()) {
            levelName = "world";
        }

        return gameDir.resolve(levelName);
    }

    private static void syncCraftTweakerScripts(Path gameDir) throws IOException {
        Path scriptsDir = gameDir.resolve("scripts");
        Files.createDirectories(scriptsDir);

        for (String scriptName : MANAGED_CRAFTTWEAKER_SCRIPTS) {
            syncCraftTweakerScript(scriptsDir, scriptName);
        }
    }

    private static void syncCraftTweakerScript(Path scriptsDir, String scriptName) throws IOException {
        String resourcePath = "/vhcctweaks-scripts/" + scriptName;
        try (InputStream stream = VaultConfigPatcher.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                VHCCTweaks.LOGGER.warn("Bundled CraftTweaker script {} not found in jar", resourcePath);
                return;
            }

            String bundledContent = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Path targetPath = scriptsDir.resolve(scriptName);
            String existingContent = Files.exists(targetPath)
                    ? Files.readString(targetPath, StandardCharsets.UTF_8)
                    : null;

            if (!bundledContent.equals(existingContent)) {
                Files.writeString(targetPath, bundledContent, StandardCharsets.UTF_8);
                VHCCTweaks.LOGGER.info("Synced CraftTweaker script {} to {}", scriptName, targetPath);
            }
        }
    }

    private static void patchComputerCraftMonitorTomlUnchecked(Path path) {
        try {
            patchComputerCraftMonitorToml(path);
        } catch (IOException e) {
            VHCCTweaks.LOGGER.warn("Could not patch {}: {}", path, e.getMessage());
        }
    }

    private static void patchComputerCraftMonitorToml(Path path) throws IOException {
        if (!Files.exists(path)) return;

        String content = Files.readString(path, StandardCharsets.UTF_8);
        String patched = patchMonitorSizeSection(content, MONITOR_MAX_WIDTH, MONITOR_MAX_HEIGHT);

        if (!patched.equals(content)) {
            Files.writeString(path, patched, StandardCharsets.UTF_8);
            VHCCTweaks.LOGGER.info("Raised ComputerCraft monitor max size to {}x{} in {}", MONITOR_MAX_WIDTH, MONITOR_MAX_HEIGHT, path);
        }
    }

    private static String patchMonitorSizeSection(String content, int width, int height) {
        String[] lines = content.split("\\r?\\n", -1);
        String lineSeparator = content.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder result = new StringBuilder(content.length() + 32);
        boolean inMonitorSection = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inMonitorSection = "[term_sizes.monitor]".equals(trimmed);
            } else if (inMonitorSection) {
                line = patchMonitorSetting(line, "width", width);
                line = patchMonitorSetting(line, "height", height);
            }

            if (i > 0) result.append(lineSeparator);
            result.append(line);
        }

        return result.toString();
    }

    private static String patchMonitorSetting(String line, String key, int value) {
        Pattern pattern = Pattern.compile("^(\\s*)" + Pattern.quote(key) + "\\s*=\\s*\\d+(\\s*(?:#.*)?)$");
        Matcher matcher = pattern.matcher(line);
        if (!matcher.matches()) return line;
        return matcher.group(1) + key + " = " + value + matcher.group(2);
    }

    private static String patchTomlBool(String content, String key, boolean value) {
        return content.replaceAll("(?m)^(\\s*)" + key + "\\s*=\\s*(true|false)",
                "$1" + key + " = " + value);
    }

    private static String patchTomlDouble(String content, String key, double value) {
        return content.replaceAll("(?m)^(\\s*)" + key + "\\s*=\\s*[\\d.]+",
                "$1" + key + " = " + value);
    }

    // --- Helpers ---

    private static boolean arrayContains(JsonArray array, String value) {
        for (JsonElement el : array) {
            if (el.isJsonPrimitive() && el.getAsString().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasResearchNamed(JsonArray researches, String name) {
        for (JsonElement el : researches) {
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("name") && obj.get("name").getAsString().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
