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
 * Patches Vault Hunters config JSON files on startup for vhcctweaks-managed entries.
 * This ensures:
 * - computercraft:* is in the vault item/block blacklists
* - legacy CC:Tweaked and Advanced Peripherals research entries injected by older versions are removed
 */
public class VaultConfigPatcher {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CC_WILDCARD = "computercraft:*";
    private static final String AP_WILDCARD = "advancedperipherals:*";
    private static final String CC_RESEARCH = "CC: Tweaked";
    private static final String AP_RESEARCH = "Advanced Peripherals";
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

        if (removeResearchNamed(researches, CC_RESEARCH)) {
            changed = true;
            VHCCTweaks.LOGGER.info("Removed legacy '{}' research entry", CC_RESEARCH);
        }

        if (removeResearchNamed(researches, AP_RESEARCH)) {
            changed = true;
            VHCCTweaks.LOGGER.info("Removed legacy '{}' research entry", AP_RESEARCH);
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

        boolean changed = false;
        for (String groupName : groups.keySet()) {
            JsonElement groupElement = groups.get(groupName);
            if (!groupElement.isJsonObject()) continue;

            JsonObject group = groupElement.getAsJsonObject();
            if (!group.has("research")) continue;

            JsonArray researchArray = group.getAsJsonArray("research");
            if (removeString(researchArray, CC_RESEARCH)) {
                changed = true;
                VHCCTweaks.LOGGER.info("Removed legacy '{}' from {} research group", CC_RESEARCH, groupName);
            }
            if (removeString(researchArray, AP_RESEARCH)) {
                changed = true;
                VHCCTweaks.LOGGER.info("Removed legacy '{}' from {} research group", AP_RESEARCH, groupName);
            }
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
        if (styles.remove(CC_RESEARCH) != null) {
            guiChanged = true;
            VHCCTweaks.LOGGER.info("Removed legacy '{}' GUI style", CC_RESEARCH);
        }
        if (styles.remove(AP_RESEARCH) != null) {
            guiChanged = true;
            VHCCTweaks.LOGGER.info("Removed legacy '{}' GUI style", AP_RESEARCH);
        }

        if (guiChanged) {
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        }
    }

    private static boolean removeString(JsonArray array, String value) {
        boolean changed = false;
        for (int i = array.size() - 1; i >= 0; i--) {
            JsonElement element = array.get(i);
            if (element.isJsonPrimitive() && value.equals(element.getAsString())) {
                array.remove(i);
                changed = true;
            }
        }
        return changed;
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
        if (descriptions.remove(CC_RESEARCH) != null) {
            descChanged = true;
            VHCCTweaks.LOGGER.info("Removed legacy '{}' description from skill_descriptions.json", CC_RESEARCH);
        }

        if (descriptions.remove(AP_RESEARCH) != null) {
            descChanged = true;
            VHCCTweaks.LOGGER.info("Removed legacy '{}' description from skill_descriptions.json", AP_RESEARCH);
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

    private static boolean removeResearchNamed(JsonArray researches, String name) {
        boolean changed = false;
        for (int i = researches.size() - 1; i >= 0; i--) {
            JsonElement el = researches.get(i);
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("name") && obj.get("name").getAsString().equals(name)) {
                    researches.remove(i);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
