package com.vhcctweaks.util;

import com.vhcctweaks.VHCCTweaks;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The safety net for 2.2.36. A bug behind a Lua function, a tick handler or a mixin hook must never take down a tick
 * or the server: it is logged once per place and turned into nil, "internal error" for Lua, or skipped elsewhere.
 * Also the one definition of a player being here.
 */
public final class Safety {

    public static final String INTERNAL_ERROR = "internal error";

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private Safety() {}

    @FunctionalInterface
    public interface LuaBody<T> {
        T run() throws LuaException;
    }

    /** Runs a Lua function's body. A LuaException is the function's own answer and passes through. */
    public static Object[] lua(String where, LuaBody<Object[]> body) throws LuaException {
        try {
            return body.run();
        } catch (LuaException e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            logOnce(where, e);
            return new Object[]{null, INTERNAL_ERROR};
        }
    }

    /** The same for a body that returns a MethodResult, such as one that waits for the main thread. */
    public static MethodResult luaResult(String where, LuaBody<MethodResult> body) throws LuaException {
        try {
            return body.run();
        } catch (LuaException e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            logOnce(where, e);
            return MethodResult.of(null, INTERNAL_ERROR);
        }
    }

    /** Runs a tick handler or mixin hook body, which must never throw into the game. */
    public static void guard(String where, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            logOnce(where, e);
        }
    }

    public static void logOnce(String where, Throwable e) {
        if (LOGGED.add(where)) {
            VHCCTweaks.LOGGER.error("vhcctweaks: unexpected error in {} (logged once, later ones are counted out silently)", where, e);
        }
    }

    /**
     * A player on THIS server: in its player list, not removed, still connected. A player another server of the
     * network calls online is not here, and neither is one who is leaving.
     */
    public static boolean isHere(ServerPlayer player) {
        return player != null && isHere(true, player.isRemoved(), player.connection != null, player.hasDisconnected());
    }

    /** The rule itself: in this server's player list, not removed, connected, not leaving. */
    static boolean isHere(boolean inThisServersList, boolean removed, boolean connected, boolean disconnected) {
        return inThisServersList && !removed && connected && !disconnected;
    }

    public static ServerPlayer here(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        return isHere(player) ? player : null;
    }

    public static ServerPlayer here(MinecraftServer server, String name) {
        if (server == null || name == null) return null;
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        return isHere(player) ? player : null;
    }
}
