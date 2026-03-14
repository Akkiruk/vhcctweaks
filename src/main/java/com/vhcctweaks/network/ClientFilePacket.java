package com.vhcctweaks.network;

import com.vhcctweaks.VHCCTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Client-bound packet for file operations. Sent from server to the player
 * who last interacted with a CC computer. The client writes files to its
 * local {@code vhcc_data/} folder with full sandbox validation.
 */
public class ClientFilePacket {
    public static final byte OP_WRITE = 0;
    public static final byte OP_APPEND = 1;
    public static final byte OP_MAKE_DIR = 2;
    public static final byte OP_DELETE = 3;

    private static final Pattern SAFE_SEGMENT =
            Pattern.compile("^[a-zA-Z0-9_\\-][a-zA-Z0-9_\\-.]{0,127}$");
    private static final int MAX_PATH_DEPTH = 16;

    private final byte operation;
    private final String path;
    private final String content; // null for makeDir / delete

    public ClientFilePacket(byte operation, String path, String content) {
        this.operation = operation;
        this.path = path;
        this.content = content;
    }

    // ---- encode / decode ----

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(operation);
        buf.writeUtf(path, 2048);
        buf.writeBoolean(content != null);
        if (content != null) {
            buf.writeUtf(content, 1_048_576);
        }
    }

    public static ClientFilePacket decode(FriendlyByteBuf buf) {
        byte op = buf.readByte();
        String p = buf.readUtf(2048);
        String c = buf.readBoolean() ? buf.readUtf(1_048_576) : null;
        return new ClientFilePacket(op, p, c);
    }

    // ---- client-side handler ----

    public static void handle(ClientFilePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleOnClient(packet));
        ctx.get().setPacketHandled(true);
    }

    private static void handleOnClient(ClientFilePacket packet) {
        try {
            Path rootDir = FMLPaths.GAMEDIR.get().resolve("vhcc_data");
            Path target = validateAndResolve(rootDir, packet.path);

            switch (packet.operation) {
                case OP_WRITE:
                    Files.createDirectories(target.getParent());
                    Files.writeString(target,
                            packet.content != null ? packet.content : "",
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    break;
                case OP_APPEND:
                    Files.createDirectories(target.getParent());
                    Files.writeString(target,
                            packet.content != null ? packet.content : "",
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                    break;
                case OP_MAKE_DIR:
                    Files.createDirectories(target);
                    break;
                case OP_DELETE:
                    if (Files.exists(target) && !Files.isDirectory(target)) {
                        Files.delete(target);
                    }
                    break;
                default:
                    VHCCTweaks.LOGGER.warn("vhcc: unknown client file operation: {}", packet.operation);
            }
        } catch (Exception e) {
            VHCCTweaks.LOGGER.warn("vhcc client file operation failed: {}", e.getMessage());
        }
    }

    // ---- path validation (mirrors server-side checks) ----

    private static Path validateAndResolve(Path rootDir, String path) throws Exception {
        if (path == null || path.isEmpty()) {
            throw new Exception("empty path");
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")) {
            throw new Exception("absolute path rejected");
        }
        String[] segments = normalized.split("/");
        if (segments.length > MAX_PATH_DEPTH) {
            throw new Exception("path too deep");
        }
        for (String seg : segments) {
            if (seg.isEmpty()) continue;
            if (seg.equals("..") || seg.equals(".")) {
                throw new Exception("path traversal rejected");
            }
            if (!SAFE_SEGMENT.matcher(seg).matches()) {
                throw new Exception("invalid path segment: " + seg);
            }
        }
        Path resolved = rootDir.resolve(normalized).normalize();
        if (!resolved.startsWith(rootDir.normalize())) {
            throw new Exception("path escapes sandbox");
        }
        return resolved;
    }
}
