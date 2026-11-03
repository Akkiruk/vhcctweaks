package com.vhcctweaks.link;

import com.vhcctweaks.util.Safety;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.core.asm.PeripheralMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * The methods an ender modem gains: link, unlink and getLinks, and the peripheral hub methods a wired modem has.
 * Added to ender modems only (PeripheralAPIMixin), so plain wireless modems are unchanged.
 * link and unlink run on the main thread; the rest answer from the computer thread, as a wired modem's do.
 * Each is behind the safety net: an unexpected error is logged once and answered with nil, "internal error".
 */
public final class HubMethods {

    private HubMethods() {}

    private static final Map<String, PeripheralMethod> METHODS = new HashMap<>();

    private static void add(String name, PeripheralMethod method) {
        METHODS.put(name, (target, context, computer, args) ->
                Safety.luaResult("modem." + name, () -> method.apply(target, context, computer, args)));
    }

    static {
        add("link", (target, context, computer, args) -> {
            int id = args.getInt(0);
            return context.executeMainThreadTask(() -> Safety.lua("modem.link", () -> LinkManager.link(computer, id)));
        });
        add("unlink", (target, context, computer, args) -> {
            int id = args.getInt(0);
            return context.executeMainThreadTask(() -> Safety.lua("modem.unlink", () -> new Object[]{LinkManager.unlink(computer, id)}));
        });
        add("getLinks", (target, context, computer, args) -> MethodResult.of(LinkManager.getLinks(computer)));
        add("getNamesRemote", (target, context, computer, args) -> MethodResult.of(LinkManager.getNamesRemote(computer)));
        add("isPresentRemote", (target, context, computer, args) ->
                MethodResult.of(LinkManager.lookup(computer, args.getString(0)) != null));
        add("getTypeRemote", (target, context, computer, args) ->
                MethodResult.of(LinkManager.getTypeRemote(computer, args.getString(0))));
        add("hasTypeRemote", (target, context, computer, args) ->
                MethodResult.of(LinkManager.hasTypeRemote(computer, args.getString(0), args.getString(1))));
        add("getMethodsRemote", (target, context, computer, args) ->
                MethodResult.of(LinkManager.getMethodsRemote(computer, args.getString(0))));
        add("callRemote", (target, context, computer, args) -> LinkManager.callRemote(computer, context, args));
    }

    public static void addTo(Map<String, PeripheralMethod> methods) {
        methods.putAll(METHODS);
    }
}
