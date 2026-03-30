package com.vhcctweaks.client.market;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class PlayerShopsBridge {

    private static final String PACKET_HANDLER_CLASS = "com.dog.playershops.network.PacketHandler";
    private static final String LIST_PACKET_CLASS = "com.dog.playershops.network.packet.RequestShopListPacket";
    private static final String VIEW_PACKET_CLASS = "com.dog.playershops.network.packet.RequestShopViewPacket";
    private static final String BUY_PACKET_CLASS = "com.dog.playershops.network.packet.PurchaseRequestPacket";

    private Object packetChannel;
    private Constructor<?> listRequestCtor;
    private Constructor<?> viewRequestCtor;
    private Constructor<?> buyRequestCtor;
    private Method sendPacketMethod;

    boolean isAvailable() {
        return ModList.get().isLoaded("playershops");
    }

    void requestShopListPage(int page, int sortMode, String searchQuery) {
        try {
            ensureReady();
            Object packet = listRequestCtor.newInstance(page, sortMode, searchQuery == null ? "" : searchQuery);
            sendPacketMethod.invoke(packetChannel, packet);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to request PlayerShops shop list", exception);
        }
    }

    void requestShopView(long shopId) {
        try {
            ensureReady();
            Object packet = viewRequestCtor.newInstance(shopId, false);
            sendPacketMethod.invoke(packetChannel, packet);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to request PlayerShops shop view", exception);
        }
    }

    void requestPurchase(long listingId, int quantity) {
        try {
            ensureReady();
            Object packet = buyRequestCtor.newInstance(listingId, quantity);
            sendPacketMethod.invoke(packetChannel, packet);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to request PlayerShops purchase", exception);
        }
    }

    private void ensureReady() throws ReflectiveOperationException {
        if (packetChannel != null) {
            return;
        }

        Class<?> packetHandlerClass = Class.forName(PACKET_HANDLER_CLASS);
        Field channelField = packetHandlerClass.getDeclaredField("CHANNEL");
        packetChannel = channelField.get(null);
        sendPacketMethod = packetChannel.getClass().getMethod("sendToServer", Object.class);

        Class<?> listPacketClass = Class.forName(LIST_PACKET_CLASS);
        listRequestCtor = listPacketClass.getConstructor(int.class, int.class, String.class);

        Class<?> viewPacketClass = Class.forName(VIEW_PACKET_CLASS);
        viewRequestCtor = viewPacketClass.getConstructor(long.class, boolean.class);

        Class<?> buyPacketClass = Class.forName(BUY_PACKET_CLASS);
        buyRequestCtor = buyPacketClass.getConstructor(long.class, int.class);
    }
}