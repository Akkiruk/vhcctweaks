package com.vhcctweaks.link;

import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.handler.ComputerPlacementTracker;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.network.wired.IWiredElement;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.peripheral.modem.ModemPeripheral;
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemElement;
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemPeripheral;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemPeripheral;
import dan200.computercraft.shared.pocket.core.PocketServerComputer;
import dan200.computercraft.shared.util.LuaUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Linked ender modems. A computer calls link(id) on its ender modem and gets that computer's peripherals as
 * "&lt;id&gt;:&lt;name&gt;", as if wired in.
 *
 * Links belong to one ender modem on one computer, the way a wired modem's remote peripherals do, so they end when
 * that modem detaches: on reboot, shutdown, unload, or when the modem is taken off. They are never saved.
 *
 * Threads: link, unlink and every attach or detach caused by the world run on the server thread. Lua's lookups run
 * on computer threads and only read concurrent maps. A link's peripherals change only while holding its hub's lock.
 * Nothing here scans every computer: load, unload and peripheral changes arrive through mixins, and a sweep once a
 * second walks only the links that exist.
 */
public final class LinkManager {

    /** One ender modem's view for one computer. */
    static final class Hub {
        final IComputerAccess host;
        final int hostId;
        final Map<Integer, Link> links = new ConcurrentHashMap<>();
        boolean closed;

        Hub(IComputerAccess host, int hostId) {
            this.host = host;
            this.hostId = hostId;
        }
    }

    /** A link to one computer. While it is not live its last peripherals are kept, so calls fail with "not loaded". */
    static final class Link {
        final Hub hub;
        final int targetId;
        volatile boolean live;
        final Map<String, LinkedPeripheral> attached = new ConcurrentHashMap<>();
        final Map<String, LinkedPeripheral> ghosts = new ConcurrentHashMap<>();

        Link(Hub hub, int targetId) {
            this.hub = hub;
            this.targetId = targetId;
        }
    }

    private static final int SWEEP_TICKS = 20;

    private static final Map<IComputerAccess, Hub> HUBS = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<Hub>> HUBS_BY_HOST = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<Link>> LINKS_BY_TARGET = new ConcurrentHashMap<>();
    private static final Set<Integer> DIRTY = ConcurrentHashMap.newKeySet();
    private static volatile boolean wiredDirty;
    private static int sweepCountdown = SWEEP_TICKS;

    private LinkManager() {}

    public static void reset() {
        HUBS.clear();
        HUBS_BY_HOST.clear();
        LINKS_BY_TARGET.clear();
        DIRTY.clear();
        wiredDirty = false;
    }

    // ===== Lua: link, unlink, getLinks (link and unlink run on the main thread) =====

    static Object[] link(IComputerAccess host, int targetId) {
        int hostId = host.getID();
        Hub existing = HUBS.get(host);
        if (existing != null && existing.links.containsKey(targetId)) return new Object[]{true};

        ServerComputer target = ComputerIndex.get(targetId);
        String refusal = LinkRules.refusalForNewLink(false, countLinks(hostId),
                ModConfig.LINKS_MAX_PER_COMPUTER.get(), rules(ComputerIndex.get(hostId), target));
        if (refusal != null) return new Object[]{false, refusal};

        Hub hub = openHub(host, hostId);
        synchronized (hub) {
            if (hub.closed) return new Object[]{false, LinkRules.NOT_LOADED};
            if (hub.links.containsKey(targetId)) return new Object[]{true};
            Link link = new Link(hub, targetId);
            hub.links.put(targetId, link);
            LINKS_BY_TARGET.computeIfAbsent(targetId, id -> ConcurrentHashMap.newKeySet()).add(link);
            activate(link, target);
        }
        return new Object[]{true};
    }

    static boolean unlink(IComputerAccess host, int targetId) {
        Hub hub = HUBS.get(host);
        if (hub == null) return false;
        synchronized (hub) {
            Link link = hub.links.remove(targetId);
            if (link == null) return false;
            forget(link);
        }
        return true;
    }

    static List<Integer> getLinks(IComputerAccess host) {
        Hub hub = HUBS.get(host);
        if (hub == null) return Collections.emptyList();
        List<Integer> ids = new ArrayList<>(hub.links.keySet());
        Collections.sort(ids);
        return ids;
    }

    // ===== Lua: the peripheral hub methods, like a wired modem's =====

    static List<String> getNamesRemote(IComputerAccess host) {
        Hub hub = HUBS.get(host);
        if (hub == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (Link link : hub.links.values()) {
            for (LinkedPeripheral peripheral : link.attached.values()) names.add(peripheral.name);
        }
        Collections.sort(names);
        return names;
    }

    /** An attached peripheral, or the last one known under that name while its computer is not loaded. */
    static LinkedPeripheral lookup(IComputerAccess host, String name) {
        int id = LinkRules.computerIdOf(name);
        if (id < 0) return null;
        Hub hub = HUBS.get(host);
        if (hub == null) return null;
        Link link = hub.links.get(id);
        if (link == null) return null;
        String local = LinkRules.localPart(name);
        LinkedPeripheral peripheral = link.attached.get(local);
        return peripheral != null ? peripheral : link.ghosts.get(local);
    }

    static Object[] getTypeRemote(IComputerAccess host, String name) {
        LinkedPeripheral peripheral = lookup(host, name);
        return peripheral == null ? null : LuaUtil.consArray(peripheral.getType(), peripheral.getAdditionalTypes());
    }

    static Object[] hasTypeRemote(IComputerAccess host, String name, String type) {
        LinkedPeripheral peripheral = lookup(host, name);
        if (peripheral == null) return null;
        return new Object[]{peripheral.getType().equals(type) || peripheral.getAdditionalTypes().contains(type)};
    }

    static Object[] getMethodsRemote(IComputerAccess host, String name) {
        LinkedPeripheral peripheral = lookup(host, name);
        return peripheral == null ? null : new Object[]{new ArrayList<>(peripheral.getMethodNames())};
    }

    static MethodResult callRemote(IComputerAccess host, ILuaContext context, IArguments arguments) throws LuaException {
        String name = arguments.getString(0);
        String method = arguments.getString(1);
        int id = LinkRules.computerIdOf(name);
        Hub hub = HUBS.get(host);
        Link link = hub == null || id < 0 ? null : hub.links.get(id);
        if (link == null) throw new LuaException("No peripheral: " + name);
        if (!link.live) throw new LuaException("computer " + id + " is not loaded");
        LinkedPeripheral peripheral = link.attached.get(LinkRules.localPart(name));
        if (peripheral == null) throw new LuaException("No peripheral: " + name);
        return peripheral.call(context, method, arguments.drop(2));
    }

    // ===== Finding peripherals by name, for pushItems and pullItems =====

    /** A linked peripheral this computer can name, or null. Used for every computer access the computer has. */
    public static IPeripheral findRemote(int hostId, String name) {
        int id = LinkRules.computerIdOf(name);
        if (id < 0) return null;
        Set<Hub> hubs = HUBS_BY_HOST.get(hostId);
        if (hubs == null) return null;
        String local = LinkRules.localPart(name);
        for (Hub hub : hubs) {
            Link link = hub.links.get(id);
            if (link == null || !link.live) continue;
            LinkedPeripheral peripheral = link.attached.get(local);
            if (peripheral != null) return peripheral.peripheral;
        }
        return null;
    }

    /** Every linked peripheral this computer can name. */
    public static void addRemotes(int hostId, Map<String, IPeripheral> into) {
        Set<Hub> hubs = HUBS_BY_HOST.get(hostId);
        if (hubs == null) return;
        for (Hub hub : hubs) {
            for (Link link : hub.links.values()) {
                if (!link.live) continue;
                for (LinkedPeripheral peripheral : link.attached.values()) into.putIfAbsent(peripheral.name, peripheral.peripheral);
            }
        }
    }

    public static boolean hasRemotes(int hostId) {
        return HUBS_BY_HOST.containsKey(hostId);
    }

    /** What a linked peripheral can push to: the linking computer's own peripherals, its wired networks, and its links. */
    static IPeripheral findFor(Hub hub, String name) {
        IPeripheral remote = findRemote(hub.hostId, name);
        if (remote != null) return remote;
        IPeripheral side = hub.host.getAvailablePeripheral(name);
        if (side != null) return side;
        for (IPeripheral peripheral : hub.host.getAvailablePeripherals().values()) {
            if (!(peripheral instanceof WiredModemPeripheral wired)) continue;
            IWiredElement element = wired.getNode().getElement();
            if (!(element instanceof WiredModemElement modem)) continue;
            Map<String, IPeripheral> network = modem.getRemotePeripherals();
            synchronized (network) {
                IPeripheral found = network.get(name);
                if (found != null) return found;
            }
        }
        return null;
    }

    static Map<String, IPeripheral> availableTo(Hub hub) {
        Map<String, IPeripheral> all = new HashMap<>(hub.host.getAvailablePeripherals());
        for (IPeripheral peripheral : new ArrayList<>(all.values())) {
            if (!(peripheral instanceof WiredModemPeripheral wired)) continue;
            IWiredElement element = wired.getNode().getElement();
            if (!(element instanceof WiredModemElement modem)) continue;
            Map<String, IPeripheral> network = modem.getRemotePeripherals();
            synchronized (network) {
                network.forEach(all::putIfAbsent);
            }
        }
        addRemotes(hub.hostId, all);
        return Collections.unmodifiableMap(all);
    }

    // ===== Hooks from the mixins =====

    /** An ender modem left a computer: its links end, with peripheral_detach for each peripheral. */
    public static void onHubDetached(IComputerAccess host) {
        if (HUBS.isEmpty()) return;
        Hub hub;
        synchronized (HUBS) {
            hub = HUBS.remove(host);
            if (hub == null) return;
            Set<Hub> hubs = HUBS_BY_HOST.get(hub.hostId);
            if (hubs != null) {
                hubs.remove(hub);
                if (hubs.isEmpty()) HUBS_BY_HOST.remove(hub.hostId);
            }
        }
        synchronized (hub) {
            hub.closed = true;
            for (Iterator<Link> it = hub.links.values().iterator(); it.hasNext(); ) {
                Link link = it.next();
                it.remove();
                forget(link);
            }
        }
    }

    static void onTargetLoaded(int computerId) {
        if (LINKS_BY_TARGET.containsKey(computerId)) DIRTY.add(computerId);
    }

    /** Runs on the server thread before the computer goes, so its peripherals detach while they still exist. */
    static void onTargetUnloaded(int computerId) {
        Set<Link> links = LINKS_BY_TARGET.get(computerId);
        if (links == null) return;
        for (Link link : links) {
            synchronized (link.hub) {
                deactivate(link);
            }
        }
    }

    /** A computer's own peripherals changed. Cheap when nobody links to it. */
    public static void onSidesChanged(int computerId) {
        if (LINKS_BY_TARGET.containsKey(computerId)) DIRTY.add(computerId);
    }

    /** A wired network changed somewhere. Wired modems do not know their computers, so every live link is refreshed. */
    public static void onWiredChanged() {
        if (!LINKS_BY_TARGET.isEmpty()) wiredDirty = true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (LINKS_BY_TARGET.isEmpty()) {
            if (!DIRTY.isEmpty()) DIRTY.clear();
            wiredDirty = false;
            return;
        }

        boolean sweep = --sweepCountdown <= 0;
        if (sweep) sweepCountdown = SWEEP_TICKS;

        if (sweep || wiredDirty) {
            wiredDirty = false;
            DIRTY.clear();
            for (Integer id : new ArrayList<>(LINKS_BY_TARGET.keySet())) update(id, sweep);
        } else if (!DIRTY.isEmpty()) {
            for (Iterator<Integer> it = DIRTY.iterator(); it.hasNext(); ) {
                int id = it.next();
                it.remove();
                update(id, false);
            }
        }
    }

    /**
     * Brings every link to one computer up to date. Rules are checked again on the sweep and whenever a link is not
     * live, so a pocket computer handed to someone else, or a computer moved to another dimension, stops being usable.
     */
    private static void update(int targetId, boolean checkRules) {
        Set<Link> links = LINKS_BY_TARGET.get(targetId);
        if (links == null) return;
        ServerComputer target = ComputerIndex.get(targetId);
        for (Link link : links) {
            synchronized (link.hub) {
                if (link.hub.closed || link.hub.links.get(targetId) != link) continue;
                if (target == null) {
                    deactivate(link);
                } else if (link.live && !checkRules) {
                    refresh(link, target);
                } else if (rules(ComputerIndex.get(link.hub.hostId), target) == null) {
                    if (link.live) refresh(link, target); else activate(link, target);
                } else {
                    deactivate(link);
                }
            }
        }
    }

    // ===== Internals. Callers hold the link's hub lock. =====

    private static Hub openHub(IComputerAccess host, int hostId) {
        synchronized (HUBS) {
            Hub hub = HUBS.get(host);
            if (hub == null) {
                hub = new Hub(host, hostId);
                HUBS.put(host, hub);
                HUBS_BY_HOST.computeIfAbsent(hostId, id -> ConcurrentHashMap.newKeySet()).add(hub);
            }
            return hub;
        }
    }

    private static int countLinks(int hostId) {
        Set<Hub> hubs = HUBS_BY_HOST.get(hostId);
        if (hubs == null) return 0;
        int count = 0;
        for (Hub hub : hubs) count += hub.links.size();
        return count;
    }

    private static void activate(Link link, ServerComputer target) {
        link.ghosts.clear();
        link.live = true;
        refresh(link, target);
    }

    private static void deactivate(Link link) {
        if (!link.live) return;
        link.live = false;
        for (Iterator<Map.Entry<String, LinkedPeripheral>> it = link.attached.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, LinkedPeripheral> entry = it.next();
            it.remove();
            entry.getValue().detach();
            link.ghosts.put(entry.getKey(), entry.getValue());
        }
    }

    /** Unlinked for good: detach everything and drop the link from the target index. */
    private static void forget(Link link) {
        link.live = false;
        for (Iterator<LinkedPeripheral> it = link.attached.values().iterator(); it.hasNext(); ) {
            LinkedPeripheral peripheral = it.next();
            it.remove();
            peripheral.detach();
        }
        link.ghosts.clear();
        LINKS_BY_TARGET.computeIfPresent(link.targetId, (id, set) -> {
            set.remove(link);
            return set.isEmpty() ? null : set;
        });
    }

    private static void refresh(Link link, ServerComputer target) {
        Map<String, IPeripheral> view = viewOf(target);
        for (Iterator<Map.Entry<String, LinkedPeripheral>> it = link.attached.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, LinkedPeripheral> entry = it.next();
            IPeripheral now = view.get(entry.getKey());
            IPeripheral was = entry.getValue().peripheral;
            if (now == was || (now != null && now.equals(was))) continue;
            it.remove();
            entry.getValue().detach();
        }
        for (Map.Entry<String, IPeripheral> entry : view.entrySet()) {
            if (link.attached.containsKey(entry.getKey())) continue;
            LinkedPeripheral peripheral = new LinkedPeripheral(link, entry.getKey(), entry.getValue());
            link.attached.put(entry.getKey(), peripheral);
            peripheral.attach();
        }
    }

    /**
     * The remote computer's peripherals under its own names: its sides, and everything on its wired networks.
     * Modems are left out, so a linked computer's links and networks are never passed on.
     */
    static Map<String, IPeripheral> viewOf(ServerComputer computer) {
        Map<String, IPeripheral> view = new HashMap<>();
        for (ComputerSide side : ComputerSide.values()) {
            IPeripheral peripheral = computer.getPeripheral(side);
            if (peripheral == null) continue;
            if (peripheral instanceof WiredModemPeripheral wired) {
                addWired(view, wired);
            } else if (!(peripheral instanceof ModemPeripheral)) {
                view.put(side.getName(), peripheral);
            }
        }
        return view;
    }

    private static void addWired(Map<String, IPeripheral> view, WiredModemPeripheral wired) {
        Object[] local = wired.getNameLocal();
        String localName = local == null || local.length == 0 ? null : (String) local[0];
        IWiredElement element = wired.getNode().getElement();
        if (!(element instanceof WiredModemElement modem)) return;
        Map<String, IPeripheral> network = modem.getRemotePeripherals();
        synchronized (network) {
            for (Map.Entry<String, IPeripheral> entry : network.entrySet()) {
                if (entry.getKey().equals(localName) || entry.getValue() instanceof ModemPeripheral) continue;
                view.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    private static String rules(ServerComputer host, ServerComputer target) {
        if (target == null) return LinkRules.NOT_LOADED;
        boolean sameDimension = host != null && host.getLevel() != null && target.getLevel() != null
                && host.getLevel().dimension().equals(target.getLevel().dimension());
        return LinkRules.refusal(true, ownerOf(host), ownerOf(target), hasEnderModem(target), sameDimension);
    }

    /** A placed computer's owner is whoever placed it. A pocket computer is owned by the player holding it, if anyone. */
    static UUID ownerOf(ServerComputer computer) {
        if (computer == null) return null;
        if (computer instanceof PocketServerComputer pocket) {
            return pocket.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer) ? player.getUUID() : null;
        }
        return ComputerPlacementTracker.getOwner(computer.getID());
    }

    static boolean hasEnderModem(ServerComputer computer) {
        for (ComputerSide side : ComputerSide.values()) {
            if (computer.getPeripheral(side) instanceof WirelessModemPeripheral modem && modem.isInterdimensional()) return true;
        }
        return false;
    }
}
