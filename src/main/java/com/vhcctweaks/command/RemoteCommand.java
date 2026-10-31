package com.vhcctweaks.command;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.vhcctweaks.ccvault.TransactionLedger;
import com.vhcctweaks.remote.RemoteControl;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.core.ServerContext;
import dan200.computercraft.shared.peripheral.monitor.ServerMonitor;
import dan200.computercraft.shared.peripheral.monitor.TileMonitor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Registers /vhcc pc &lt;seq&gt; &lt;action&gt;, the remote control the client debug tool drives.
 * seq is chosen by the caller and echoed in the reply, which comes back as one private chat line:
 *   [vhcc-rc] {"seq":7,"ok":true,...}
 *
 * Any player may use it.
 *
 * Commands instead of a network channel keep this server-side only: a client without the mod still
 * connects, and the checkClientPacketSafety build rule stays intact.
 */
public class RemoteCommand {

    private static final int READ_PAGE_BYTES = 48 * 1024;
    private static final int LOG_TAIL_BYTES = 4 << 20;
    private static final Pattern LOG_FILTER = Pattern.compile("vhcctweaks|ccvault|computercraft|dan200", Pattern.CASE_INSENSITIVE);

    private interface Action {
        void run(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws Exception;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("vhcc");
        ArgumentBuilder<CommandSourceStack, ?> seq = Commands.argument("seq", IntegerArgumentType.integer(0));

        seq.then(action("ping", RemoteCommand::ping));
        seq.then(action("list", RemoteCommand::list));
        seq.then(Commands.literal("serverlog").then(Commands.argument("lines", IntegerArgumentType.integer(1, 300))
                .executes(guarded("serverlog", RemoteCommand::serverLog))));
        seq.then(Commands.literal("ledger").then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 500)).executes(guarded("ledger", RemoteCommand::ledger)))));
        seq.then(Commands.literal("clear").executes(guarded("clear", RemoteCommand::clearBuffer)));
        seq.then(Commands.literal("buf").then(Commands.argument("data", StringArgumentType.word())
                .executes(guarded("buf", RemoteCommand::appendBuffer))));
        seq.then(Commands.literal("monitor").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(guarded("monitor", RemoteCommand::monitor))));

        seq.then(onComputer("screen", RemoteCommand::screen));
        seq.then(onComputer("peripherals", RemoteCommand::peripherals));
        seq.then(Commands.literal("monitorof").then(id().then(Commands.argument("side", StringArgumentType.word())
                .executes(guarded("monitorof", RemoteCommand::monitorOf)))));
        seq.then(onComputer("reboot", (ctx, p, r) -> controlled(ctx, p).reboot()));
        seq.then(onComputer("shutdown", (ctx, p, r) -> controlled(ctx, p).shutdown()));
        seq.then(onComputer("turnon", (ctx, p, r) -> controlled(ctx, p).turnOn()));
        seq.then(onComputer("terminate", (ctx, p, r) -> controlled(ctx, p).queueEvent("terminate", new Object[0])));
        seq.then(onComputer("exec", RemoteCommand::exec));
        seq.then(Commands.literal("type").then(id().then(Commands.argument("text", StringArgumentType.greedyString())
                .executes(guarded("type", RemoteCommand::type)))));
        seq.then(Commands.literal("paste").then(id().then(Commands.argument("text", StringArgumentType.greedyString())
                .executes(guarded("paste", (ctx, p, r) -> controlled(ctx, p).queueEvent("paste", new Object[]{StringArgumentType.getString(ctx, "text")}))))));
        seq.then(Commands.literal("key").then(id().then(Commands.argument("code", IntegerArgumentType.integer(0, 512))
                .executes(guarded("key", RemoteCommand::key)))));
        seq.then(Commands.literal("touch").then(id().then(Commands.argument("monitor", StringArgumentType.word())
                .then(Commands.argument("x", IntegerArgumentType.integer(1)).then(Commands.argument("y", IntegerArgumentType.integer(1))
                        .executes(guarded("touch", RemoteCommand::touch)))))));
        seq.then(Commands.literal("ls").then(id().then(Commands.argument("path", StringArgumentType.greedyString())
                .executes(guarded("ls", RemoteCommand::listFiles)))));
        seq.then(Commands.literal("read").then(id().then(Commands.argument("offset", IntegerArgumentType.integer(0))
                .then(Commands.argument("path", StringArgumentType.greedyString()).executes(guarded("read", RemoteCommand::readFile))))));
        seq.then(Commands.literal("put").then(id().then(Commands.argument("path", StringArgumentType.greedyString())
                .executes(guarded("put", RemoteCommand::putFile)))));
        seq.then(Commands.literal("rm").then(id().then(Commands.argument("path", StringArgumentType.greedyString())
                .executes(guarded("rm", RemoteCommand::deleteFile)))));

        dispatcher.register(root.then(Commands.literal("pc").then(seq)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> id() {
        return Commands.argument("id", IntegerArgumentType.integer(0));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> action(String name, Action action) {
        return Commands.<CommandSourceStack>literal(name).executes(guarded(name, action));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> onComputer(String name, Action action) {
        return Commands.<CommandSourceStack>literal(name).then(id().executes(guarded(name, action)));
    }

    /** Every action passes through here: a player, logged, and always answered. */
    private static com.mojang.brigadier.Command<CommandSourceStack> guarded(String name, Action action) {
        return ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            JsonObject reply = new JsonObject();
            reply.addProperty("seq", IntegerArgumentType.getInteger(ctx, "seq"));
            try {
                RemoteControl.audit(player, name, describe(ctx));
                action.run(ctx, player, reply);
                reply.addProperty("ok", true);
            } catch (Exception e) {
                reply.addProperty("ok", false);
                reply.addProperty("error", String.valueOf(e.getMessage()));
            }
            RemoteControl.send(player, reply);
            return reply.get("ok").getAsBoolean() ? 1 : 0;
        };
    }

    private static String describe(CommandContext<CommandSourceStack> ctx) {
        String input = ctx.getInput();
        return input.length() > 200 ? input.substring(0, 200) + "... (" + input.length() + " chars)" : input;
    }

    private static ServerComputer computer(CommandContext<CommandSourceStack> ctx) throws IOException {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        ServerComputer computer = RemoteControl.find(ctx.getSource().getServer(), id);
        if (computer == null) throw new IOException("computer #" + id + " is not loaded");
        return computer;
    }

    private static ServerComputer controlled(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws IOException {
        ServerComputer computer = computer(ctx);
        RemoteControl.markControlled(computer.getID(), player.getUUID());
        return computer;
    }

    // ---- reading ----

    private static void ping(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) {
        reply.addProperty("protocol", 1);
    }

    private static void list(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) {
        JsonArray out = new JsonArray();
        MinecraftServer server = ctx.getSource().getServer();
        for (ServerComputer computer : ServerContext.get(server).registry().getComputers()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", computer.getID());
            o.addProperty("label", computer.getLabel());
            o.addProperty("on", computer.isOn());
            o.addProperty("family", computer.getFamily().name());
            o.addProperty("dimension", computer.getLevel().dimension().location().toString());
            BlockPos pos = computer.getPosition();
            o.addProperty("x", pos.getX());
            o.addProperty("y", pos.getY());
            o.addProperty("z", pos.getZ());
            out.add(o);
        }
        reply.add("computers", out);
    }

    private static void screen(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        ServerComputer computer = computer(ctx);
        reply.addProperty("id", computer.getID());
        reply.addProperty("on", computer.isOn());
        reply.add("terminal", RemoteControl.readTerminal(computer.getAPIEnvironment().getTerminal()));
    }

    private static void monitor(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws Exception {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        BlockEntity entity = ctx.getSource().getLevel().getBlockEntity(pos);
        if (!(entity instanceof TileMonitor tile)) throw new IOException("no monitor at that position");
        ServerMonitor monitor = tile.getCachedServerMonitor();
        if (monitor == null || monitor.getTerminal() == null) throw new IOException("that monitor has no terminal yet");
        reply.add("terminal", RemoteControl.readTerminal(monitor.getTerminal()));
    }

    /** What is attached to each side, read in Java so it works on a computer that cannot run anything. */
    private static void peripherals(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        ServerComputer computer = computer(ctx);
        JsonObject out = new JsonObject();
        for (ComputerSide side : ComputerSide.values()) {
            IPeripheral peripheral = computer.getPeripheral(side);
            if (peripheral != null) out.addProperty(side.getName(), peripheral.getType());
        }
        reply.add("sides", out);
    }

    private static void monitorOf(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        ComputerSide side = ComputerSide.valueOfInsensitive(StringArgumentType.getString(ctx, "side"));
        IPeripheral peripheral = side == null ? null : computer(ctx).getPeripheral(side);
        if (peripheral == null || !(peripheral.getTarget() instanceof TileMonitor tile)) throw new IOException("no monitor on that side");
        ServerMonitor monitor = tile.getCachedServerMonitor();
        if (monitor == null || monitor.getTerminal() == null) throw new IOException("that monitor has no terminal yet");
        reply.add("terminal", RemoteControl.readTerminal(monitor.getTerminal()));
    }

    /** The end of the server log, only lines about computers or this mod: the rest can hold player addresses. */
    private static void serverLog(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        Path log = FMLPaths.GAMEDIR.get().resolve("logs").resolve("latest.log");
        if (!Files.isRegularFile(log)) throw new IOException("no server log found");
        byte[] tail;
        try (RandomAccessFile file = new RandomAccessFile(log.toFile(), "r")) {
            long start = Math.max(0, file.length() - LOG_TAIL_BYTES);
            tail = new byte[(int) (file.length() - start)];
            file.seek(start);
            file.readFully(tail);
        }
        List<String> matching = new ArrayList<>();
        for (String line : new String(tail, StandardCharsets.UTF_8).split("\\R")) {
            if (LOG_FILTER.matcher(line).find()) matching.add(line);
        }
        int lines = IntegerArgumentType.getInteger(ctx, "lines");
        JsonArray out = new JsonArray();
        matching.subList(Math.max(0, matching.size() - lines), matching.size()).forEach(out::add);
        reply.add("lines", out);
    }

    private static void ledger(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        String name = StringArgumentType.getString(ctx, "player");
        GameProfile profile = ctx.getSource().getServer().getProfileCache().get(name)
                .orElseThrow(() -> new IOException("unknown player " + name));
        reply.add("entries", new Gson().toJsonTree(new ArrayList<>(TransactionLedger.getHistory(profile.getId(), IntegerArgumentType.getInteger(ctx, "limit")))));
    }

    // ---- input ----

    private static void type(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        ServerComputer computer = controlled(ctx, player);
        for (char c : StringArgumentType.getString(ctx, "text").toCharArray()) {
            computer.queueEvent("char", new Object[]{String.valueOf(c)});
        }
    }

    private static void key(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        ServerComputer computer = controlled(ctx, player);
        int code = IntegerArgumentType.getInteger(ctx, "code");
        computer.queueEvent("key", new Object[]{code, false});
        computer.queueEvent("key_up", new Object[]{code});
    }

    private static void touch(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        controlled(ctx, player).queueEvent("monitor_touch", new Object[]{
                StringArgumentType.getString(ctx, "monitor"),
                IntegerArgumentType.getInteger(ctx, "x"),
                IntegerArgumentType.getInteger(ctx, "y")});
    }

    // ---- uploads and code ----

    private static void clearBuffer(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) {
        RemoteControl.forget(player.getUUID());
    }

    private static void appendBuffer(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        byte[] piece = Base64.getUrlDecoder().decode(StringArgumentType.getString(ctx, "data"));
        reply.addProperty("size", RemoteControl.appendBuffer(player.getUUID(), piece));
    }

    private static void exec(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws Exception {
        ServerComputer computer = controlled(ctx, player);
        byte[] code = RemoteControl.takeBuffer(player.getUUID());
        if (code.length == 0) throw new IOException("nothing uploaded to run");
        RemoteControl.audit(player, "exec-code", "#" + computer.getID() + " " + code.length + " bytes sha256="
                + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code)));
        String request = RemoteControl.beginExec(player, computer.getID());
        computer.queueEvent("vhcc_rc_exec", new Object[]{request, new String(code, StandardCharsets.UTF_8)});
        reply.addProperty("exec", request);
    }

    // ---- files, straight from the computer's folder so this works whatever the computer is doing ----

    private static Path path(CommandContext<CommandSourceStack> ctx) throws IOException {
        return RemoteControl.computerPath(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "id"),
                StringArgumentType.getString(ctx, "path"));
    }

    private static void listFiles(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        Path dir = path(ctx);
        if (!Files.isDirectory(dir)) throw new IOException("not a folder");
        JsonArray out = new JsonArray();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                JsonObject o = new JsonObject();
                o.addProperty("name", p.getFileName().toString());
                o.addProperty("dir", Files.isDirectory(p));
                o.addProperty("size", Files.isDirectory(p) ? 0 : Files.size(p));
                out.add(o);
            }
        }
        reply.add("files", out);
    }

    private static void readFile(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        Path file = path(ctx);
        if (!Files.isRegularFile(file)) throw new IOException("no such file");
        byte[] all = Files.readAllBytes(file);
        int offset = Math.min(IntegerArgumentType.getInteger(ctx, "offset"), all.length);
        int length = Math.min(READ_PAGE_BYTES, all.length - offset);
        reply.addProperty("size", all.length);
        reply.addProperty("offset", offset);
        reply.addProperty("data", Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(all, offset, offset + length)));
    }

    private static void putFile(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        Path file = path(ctx);
        byte[] data = RemoteControl.takeBuffer(player.getUUID());
        // A retried put arrives with the buffer already used. Writing that would empty the file.
        if (data.length == 0) throw new IOException("nothing uploaded to write");
        Files.createDirectories(file.getParent());
        Files.write(file, data);
        reply.addProperty("written", data.length);
    }

    private static void deleteFile(CommandContext<CommandSourceStack> ctx, ServerPlayer player, JsonObject reply) throws IOException {
        Path file = path(ctx);
        if (!Files.isRegularFile(file)) throw new IOException("no such file");
        Files.delete(file);
    }
}
