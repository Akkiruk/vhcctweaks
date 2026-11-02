package com.vhcctweaks.link;

import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.core.asm.PeripheralMethod;

import java.util.Map;

/**
 * The methods an ender modem gains: link, unlink and getLinks, and the peripheral hub methods a wired modem has.
 * Added to ender modems only (PeripheralAPIMixin), so plain wireless modems are unchanged.
 * link and unlink run on the main thread; the rest answer from the computer thread, as a wired modem's do.
 */
public final class HubMethods {

    private HubMethods() {}

    private static final Map<String, PeripheralMethod> METHODS = Map.of(
            "link", (target, context, computer, args) -> {
                int id = args.getInt(0);
                return context.executeMainThreadTask(() -> LinkManager.link(computer, id));
            },
            "unlink", (target, context, computer, args) -> {
                int id = args.getInt(0);
                return context.executeMainThreadTask(() -> new Object[]{LinkManager.unlink(computer, id)});
            },
            "getLinks", (target, context, computer, args) -> MethodResult.of(LinkManager.getLinks(computer)),
            "getNamesRemote", (target, context, computer, args) -> MethodResult.of(LinkManager.getNamesRemote(computer)),
            "isPresentRemote", (target, context, computer, args) ->
                    MethodResult.of(LinkManager.lookup(computer, args.getString(0)) != null),
            "getTypeRemote", (target, context, computer, args) ->
                    MethodResult.of(LinkManager.getTypeRemote(computer, args.getString(0))),
            "hasTypeRemote", (target, context, computer, args) ->
                    MethodResult.of(LinkManager.hasTypeRemote(computer, args.getString(0), args.getString(1))),
            "getMethodsRemote", (target, context, computer, args) ->
                    MethodResult.of(LinkManager.getMethodsRemote(computer, args.getString(0))),
            "callRemote", (target, context, computer, args) -> LinkManager.callRemote(computer, context, args)
    );

    public static void addTo(Map<String, PeripheralMethod> methods) {
        methods.putAll(METHODS);
    }
}
