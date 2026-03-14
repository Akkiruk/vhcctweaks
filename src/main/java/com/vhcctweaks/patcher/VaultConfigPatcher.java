package com.vhcctweaks.patcher;

import com.google.gson.*;
import com.vhcctweaks.VHCCTweaks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Patches Vault Hunters config JSON files on startup to add CC:Tweaked entries.
 * This ensures:
 * - computercraft:* is in the vault item/block blacklists
 * - CC:Tweaked has a research gate
 * - CC server config is set up in defaultconfigs
 */
public class VaultConfigPatcher {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CC_WILDCARD = "computercraft:*";

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
            patchServerConfig(configDir);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.warn("Could not patch computercraft server config: {}", e.getMessage());
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
        }

        // Add to BLOCK_BLACKLIST
        if (root.has("BLOCK_BLACKLIST")) {
            JsonArray blocks = root.getAsJsonArray("BLOCK_BLACKLIST");
            if (!arrayContains(blocks, CC_WILDCARD)) {
                blocks.add(CC_WILDCARD);
                changed = true;
                VHCCTweaks.LOGGER.info("Added {} to vault BLOCK_BLACKLIST", CC_WILDCARD);
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
        if (!arrayContains(research, "CC: Tweaked")) {
            research.add("CC: Tweaked");
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            VHCCTweaks.LOGGER.info("Added 'CC: Tweaked' to Handling research group");
        }
    }

    private static void patchServerConfig(Path configDir) throws IOException {
        Path instanceDir = configDir.getParent();

        // 1) Write defaultconfigs for new worlds
        Path defaultConfigs = instanceDir.resolve("defaultconfigs");
        if (!Files.exists(defaultConfigs)) {
            Files.createDirectories(defaultConfigs);
        }
        writeServerConfigIfNeeded(defaultConfigs.resolve("computercraft-server.toml"));

        // 2) Patch existing world serverconfigs so existing worlds get the fix too
        Path savesDir = instanceDir.resolve("saves");
        if (Files.exists(savesDir) && Files.isDirectory(savesDir)) {
            try (var worlds = Files.list(savesDir)) {
                worlds.filter(Files::isDirectory).forEach(worldDir -> {
                    Path worldServerConfig = worldDir.resolve("serverconfig/computercraft-server.toml");
                    if (Files.exists(worldServerConfig)) {
                        try {
                            patchExistingServerConfig(worldServerConfig);
                        } catch (IOException e) {
                            VHCCTweaks.LOGGER.warn("Could not patch server config in {}: {}",
                                    worldDir.getFileName(), e.getMessage());
                        }
                    }
                });
            }
        }
    }

    /**
     * Patches an existing world's computercraft-server.toml to disable HTTP.
     * Only modifies the http.enabled and http.websocket_enabled lines, preserving
     * all other settings the player may have customized.
     */
    private static void patchExistingServerConfig(Path configPath) throws IOException {
        String content = Files.readString(configPath, StandardCharsets.UTF_8);
        if (content.contains("# Patched by VH CC Tweaks")) return; // Already done

        String patched = content;
        // Disable HTTP
        patched = patched.replaceAll("(?m)^(\\s*)enabled\\s*=\\s*true", "$1enabled = false");
        // Disable WebSockets
        patched = patched.replaceAll("(?m)^(\\s*)websocket_enabled\\s*=\\s*true", "$1websocket_enabled = false");

        if (!patched.equals(content)) {
            patched = patched + "\n# Patched by VH CC Tweaks\n";
            Files.writeString(configPath, patched, StandardCharsets.UTF_8);
            VHCCTweaks.LOGGER.info("Disabled HTTP in existing world config: {}", configPath.getFileName());
        }
    }

    private static void writeServerConfigIfNeeded(Path serverConfigPath) throws IOException {
        if (Files.exists(serverConfigPath)) {
            String existing = Files.readString(serverConfigPath, StandardCharsets.UTF_8);
            if (existing.contains("VH CC Tweaks")) {
                return; // Already patched
            }
        }

        String config = """
                # CC:Tweaked server config - managed by VH CC Tweaks mod
                # VH CC Tweaks
                
                [general]
                \tcomputer_space_limit = 1000000
                \tfloppy_space_limit = 125000
                \tmaximum_open_files = 128
                \tdisable_lua51_features = false
                \tdefault_computer_settings = ""
                \tlog_computer_errors = true
                
                [execution]
                \tmax_main_global_time = 10
                \tmax_main_computer_time = 5
                
                [http]
                \t#Disabled: prevents downloading automation scripts from the internet
                \tenabled = false
                \twebsocket_enabled = false
                \tmax_requests = 0
                \tmax_websockets = 0
                
                [peripheral]
                \tcommand_computers = false
                \tmodem_range = 32
                \tmodem_high_altitude_range = 64
                \tmodem_range_during_storm = 16
                \tmodem_high_altitude_range_during_storm = 32
                \tmax_notes_per_tick = 8
                \tmonitor_bandwidth = 10
                
                [turtle]
                \tneed_fuel = true
                \tnormal_fuel_limit = 5000
                \tadvanced_fuel_limit = 50000
                \tturtle_disabled_actions = false
                \tcan_push = true
                """;

        Files.writeString(serverConfigPath, config, StandardCharsets.UTF_8);
        VHCCTweaks.LOGGER.info("Created/updated computercraft-server.toml in defaultconfigs");
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

        if (!styles.has("CC: Tweaked")) {
            JsonObject ccStyle = new JsonObject();
            ccStyle.addProperty("x", 10);
            ccStyle.addProperty("y", 320);
            ccStyle.addProperty("frameType", "RECTANGULAR");
            ccStyle.addProperty("icon", "the_vault:gui/researches/cc_tweaked");
            styles.add("CC: Tweaked", ccStyle);

            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            VHCCTweaks.LOGGER.info("Added 'CC: Tweaked' GUI style to researches_gui_styles.json");
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
            line7.addProperty("text", " research. ");
            desc.add(line7);

            JsonObject line8 = new JsonObject();
            line8.addProperty("text", "HTTP access");
            line8.addProperty("color", "gold");
            desc.add(line8);

            JsonObject line9 = new JsonObject();
            line9.addProperty("text", " has been ");
            desc.add(line9);

            JsonObject line10 = new JsonObject();
            line10.addProperty("text", "disabled");
            line10.addProperty("color", "gold");
            desc.add(line10);

            JsonObject line11 = new JsonObject();
            line11.addProperty("text", " and CC items cannot be brought into the vaults.");
            desc.add(line11);

            descriptions.add("CC: Tweaked", desc);

            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            VHCCTweaks.LOGGER.info("Added 'CC: Tweaked' description to skill_descriptions.json");
        }
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
