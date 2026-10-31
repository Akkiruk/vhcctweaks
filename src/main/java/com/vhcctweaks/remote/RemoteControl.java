package com.vhcctweaks.remote;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.config.ModConfig;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.core.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets an allowed admin drive any loaded computer through /vhcc pc. Every call is written to an audit log.
 *
 * Security model:
 * - Off on a dedicated server until remoteControl.enabled is set and the player is on remoteControl.allowedPlayers.
 *   The owner of a singleplayer world is always allowed.
 * - Remote input does not count as a player interaction, so it cannot open or refresh a CCVault session.
 * - A computer that was driven remotely refuses CCVault transfers for anyone but the controller until it reboots.
 */
public final class RemoteControl {

    /** Replies start with this so a client can pick them out of chat. */
    public static final String REPLY_PREFIX = "[vhcc-rc] ";

    private static final int MAX_BUFFER_BYTES = 1 << 20;
    private static final int MAX_REPLY_CHARS = 64 * 1024;
    private static final int MAX_PENDING = 32;
    private static final long EXEC_TIMEOUT_MS = 60_000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Map<Integer, Set<UUID>> controllers = new ConcurrentHashMap<>();
    private static final Map<UUID, ByteArrayOutputStream> buffers = new ConcurrentHashMap<>();
    private static final Map<String, PendingExec> pending = new ConcurrentHashMap<>();
    private static Path auditFile;

    private RemoteControl() {}

    public static void init(Path dataDir) {
        Path dir = dataDir.resolve("remote");
        try {
            Files.createDirectories(dir);
            auditFile = dir.resolve("audit.log");
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("Remote control: could not create the audit log folder", e);
        }
    }

    // ---- access ----

    public static boolean isAllowed(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null && !server.isDedicatedServer() && server.isSingleplayerOwner(player.getGameProfile())) {
            return true;
        }
        if (!ModConfig.REMOTE_CONTROL_ENABLED.get()) return false;
        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().getName();
        for (String entry : ModConfig.REMOTE_CONTROL_ALLOWED_PLAYERS.get()) {
            if (entry.equalsIgnoreCase(uuid) || entry.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Refusing to write the audit line refuses the action: nothing here may run unlogged. */
    public static void audit(ServerPlayer player, String action, String detail) throws IOException {
        if (auditFile == null) throw new IOException("audit log is not available");
        String line = Instant.now() + " " + player.getGameProfile().getName() + " " + player.getUUID()
                + " " + action + " " + detail.replace('\n', ' ').replace('\r', ' ') + System.lineSeparator();
        Files.writeString(auditFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    // ---- computers ----

    public static ServerComputer find(MinecraftServer server, int id) {
        ServerComputer found = null;
        for (ServerComputer computer : ServerContext.get(server).registry().getComputers()) {
            if (computer.getID() != id) continue;
            if (computer.isOn()) return computer;
            found = computer;
        }
        return found;
    }

    public static JsonObject readTerminal(Terminal terminal) {
        JsonObject out = new JsonObject();
        out.addProperty("w", terminal.getWidth());
        out.addProperty("h", terminal.getHeight());
        out.addProperty("cx", terminal.getCursorX());
        out.addProperty("cy", terminal.getCursorY());
        out.addProperty("colour", terminal.isColour());
        JsonArray text = new JsonArray();
        JsonArray fg = new JsonArray();
        JsonArray bg = new JsonArray();
        for (int y = 0; y < terminal.getHeight(); y++) {
            text.add(terminal.getLine(y).toString());
            fg.add(terminal.getTextColourLine(y).toString());
            bg.add(terminal.getBackgroundColourLine(y).toString());
        }
        out.add("lines", text);
        out.add("fg", fg);
        out.add("bg", bg);
        return out;
    }

    /** Resolves a path inside computer #id's folder. Anything that climbs out of it is refused. */
    public static Path computerPath(MinecraftServer server, int id, String relative) throws IOException {
        Path root = ServerContext.get(server).storageDir().resolve("computer").resolve(Integer.toString(id)).toAbsolutePath().normalize();
        String clean = relative.replace('\\', '/').replaceAll("^/+", "");
        Path path = root.resolve(clean).normalize();
        if (!path.startsWith(root)) throw new IOException("path leaves the computer");
        return path;
    }

    // ---- CCVault guard ----

    public static void markControlled(int computerId, UUID controller) {
        controllers.computeIfAbsent(computerId, id -> ConcurrentHashMap.newKeySet()).add(controller);
    }

    public static void clearControlled(int computerId) {
        controllers.remove(computerId);
    }

    public static boolean blocksTransfer(int computerId, UUID player) {
        Set<UUID> set = controllers.getOrDefault(computerId, Collections.emptySet());
        return !set.isEmpty() && !set.contains(player);
    }

    // ---- upload buffer: chat lines are short, so anything long arrives in pieces ----

    public static int appendBuffer(UUID player, byte[] piece) throws IOException {
        ByteArrayOutputStream buffer = buffers.computeIfAbsent(player, p -> new ByteArrayOutputStream());
        if (buffer.size() + piece.length > MAX_BUFFER_BYTES) throw new IOException("upload is larger than 1 MB");
        buffer.write(piece);
        return buffer.size();
    }

    public static byte[] takeBuffer(UUID player) {
        ByteArrayOutputStream buffer = buffers.remove(player);
        return buffer == null ? new byte[0] : buffer.toByteArray();
    }

    public static void forget(UUID player) {
        buffers.remove(player);
    }

    // ---- exec: the Roger supervisor on the computer runs the code and answers through vhcc.rcReply ----

    public static String beginExec(ServerPlayer player, int computerId) throws IOException {
        long now = System.currentTimeMillis();
        pending.values().removeIf(p -> now - p.startedAt > EXEC_TIMEOUT_MS);
        if (pending.size() >= MAX_PENDING) throw new IOException("too many requests waiting for an answer");
        byte[] random = new byte[16];
        SECURE_RANDOM.nextBytes(random);
        String id = HexFormat.of().formatHex(random);
        pending.put(id, new PendingExec(player.getUUID(), computerId, now));
        return id;
    }

    /** Called on the computer thread. Only an answer to a request this computer was sent is accepted. */
    public static boolean completeExec(int computerId, String id, String result) {
        PendingExec request = pending.get(id);
        if (request == null || request.computerId != computerId) return false;
        pending.remove(id);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;
        String text = result.length() > MAX_REPLY_CHARS ? result.substring(0, MAX_REPLY_CHARS) : result;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(request.player);
            if (player == null) return;
            JsonObject reply = new JsonObject();
            reply.addProperty("exec", id);
            reply.addProperty("result", text);
            reply.addProperty("truncated", text.length() < result.length());
            send(player, reply);
        });
        return true;
    }

    public static void send(ServerPlayer player, JsonObject reply) {
        player.sendMessage(new TextComponent(REPLY_PREFIX + reply).withStyle(ChatFormatting.DARK_GRAY), Util.NIL_UUID);
    }

    private record PendingExec(UUID player, int computerId, long startedAt) {}
}
