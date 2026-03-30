package com.vhcctweaks.client.market;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.mixin.client.playershops.BuyerShopScreenAccessor;
import com.vhcctweaks.mixin.client.playershops.ShopBrowserScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PlayerShopsMarketService {

    private static final PlayerShopsMarketService INSTANCE = new PlayerShopsMarketService();
    private static final String SHOP_BROWSER_SCREEN = "com.dog.playershops.gui.ShopBrowserScreen";
    private static final String BUYER_SCREEN = "com.dog.playershops.gui.BuyerShopScreen";

    private final PlayerShopsBridge bridge = new PlayerShopsBridge();

    private MarketSnapshot snapshot = MarketSnapshot.empty();
    private final Set<Long> discoveredShopIds = new LinkedHashSet<>();
    private final Deque<Long> pendingShopViews = new ArrayDeque<>();
    private final Map<String, MutableItemGroup> groupedOffers = new LinkedHashMap<>();

    private boolean crawling;
    private int dataVersion;
    private int requestedPage;
    private int highestPageSeen;
    private int totalPages = 1;
    private int viewedShopCount;
    private String statusMessage = "Idle";
    private Screen attachedScreen;

    private PlayerShopsMarketService() {
    }

    public static PlayerShopsMarketService getInstance() {
        return INSTANCE;
    }

    public boolean isPlayerShopsAvailable() {
        return bridge.isAvailable();
    }

    public void openMarket(Screen screen) {
        attachedScreen = screen;
        if (!bridge.isAvailable()) {
            statusMessage = "PlayerShops is not installed.";
            return;
        }

        if (!crawling) {
            startRefresh();
        }
    }

    public void detachScreen(Screen screen) {
        if (attachedScreen == screen) {
            attachedScreen = null;
        }
    }

    public void startRefresh() {
        discoveredShopIds.clear();
        pendingShopViews.clear();
        groupedOffers.clear();
        viewedShopCount = 0;
        highestPageSeen = -1;
        requestedPage = 0;
        totalPages = 1;
        crawling = true;
        statusMessage = "Loading shop index...";
        bumpVersion();

        try {
            bridge.requestShopListPage(0, 0, "");
        } catch (RuntimeException exception) {
            fail("Failed to start market crawl", exception);
        }
    }

    public boolean shouldIntercept(Screen screen) {
        if (!crawling || screen == null) {
            return false;
        }

        String className = screen.getClass().getName();
        return SHOP_BROWSER_SCREEN.equals(className) || BUYER_SCREEN.equals(className);
    }

    public void captureInterceptedScreen(Screen screen) {
        if (screen == null) {
            return;
        }

        try {
            String className = screen.getClass().getName();
            if (SHOP_BROWSER_SCREEN.equals(className)) {
                captureShopBrowser(screen);
            } else if (BUYER_SCREEN.equals(className)) {
                captureBuyerShop(screen);
            }
        } catch (Exception exception) {
            fail("Failed to read PlayerShops screen data", exception);
        }
    }

    public MarketSnapshot getSnapshot() {
        return snapshot;
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public boolean isCrawling() {
        return crawling;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void purchaseOffer(MarketOffer offer) {
        Objects.requireNonNull(offer, "offer");

        try {
            bridge.requestPurchase(offer.listingId(), 1);
            statusMessage = "Purchase requested for " + offer.itemDisplayName() + ".";
            bumpVersion();
        } catch (RuntimeException exception) {
            fail("Failed to send purchase request", exception);
        }
    }

    private void captureShopBrowser(Screen screen) {
        ShopBrowserScreenAccessor accessor = (ShopBrowserScreenAccessor) screen;
        Collection<?> shops = accessor.vhcctweaks$getShops();
        int currentPage = accessor.vhcctweaks$getCurrentPage();
        int seenTotalPages = Math.max(1, accessor.vhcctweaks$getTotalPages());

        highestPageSeen = Math.max(highestPageSeen, currentPage);
        totalPages = Math.max(totalPages, seenTotalPages);

        for (Object shopEntry : shops) {
            long shopId = invokeLong(shopEntry, "shopId");
            if (discoveredShopIds.add(shopId)) {
                pendingShopViews.addLast(shopId);
            }
        }

        if (currentPage + 1 < totalPages) {
            requestedPage = currentPage + 1;
            statusMessage = "Loading shops page " + (currentPage + 2) + " of " + totalPages + "...";
            bumpVersion();
            bridge.requestShopListPage(requestedPage, 0, "");
            return;
        }

        requestNextShopView();
    }

    private void captureBuyerShop(Screen screen) {
        BuyerShopScreenAccessor accessor = (BuyerShopScreenAccessor) screen;
        long shopId = accessor.vhcctweaks$getShopId();
        String shopName = accessor.vhcctweaks$getShopName();
        String ownerName = accessor.vhcctweaks$getOwnerName();
        boolean serverOwned = accessor.vhcctweaks$isServerOwned();

        for (Object listingView : accessor.vhcctweaks$getListings()) {
            ItemStack stack = invokeItemStack(listingView, "displayStack");
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId == null) {
                continue;
            }

            String key = itemId.toString();
            MutableItemGroup group = groupedOffers.computeIfAbsent(key, ignored -> new MutableItemGroup(key, stack.copy(), displayNameFor(stack)));
            MarketOffer offer = new MarketOffer(
                    key,
                    stack.copy(),
                    invokeLong(listingView, "listingId"),
                    shopId,
                    shopName,
                    ownerName,
                    serverOwned,
                    invokeString(listingView, "itemDisplayName"),
                    invokeLong(listingView, "price"),
                    invokeInt(listingView, "stockCount"),
                    invokeInt(listingView, "sellMode")
            );
            group.offers.add(offer);
            group.displayName = preferredName(group.displayName, offer.itemDisplayName());
        }

        viewedShopCount++;
        requestNextShopView();
    }

    private void requestNextShopView() {
        while (!pendingShopViews.isEmpty()) {
            long nextShopId = pendingShopViews.removeFirst();
            statusMessage = "Loading shop " + (viewedShopCount + 1) + " of " + discoveredShopIds.size() + "...";
            bumpVersion();
            bridge.requestShopView(nextShopId);
            return;
        }

        finishCrawl();
    }

    private void finishCrawl() {
        List<MarketItemGroup> groups = new ArrayList<>();
        for (MutableItemGroup mutableGroup : groupedOffers.values()) {
            List<MarketOffer> offers = new ArrayList<>(mutableGroup.offers);
            offers.sort(Comparator.comparingLong(MarketOffer::price)
                    .thenComparing(MarketOffer::shopName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(MarketOffer::listingId));
            groups.add(new MarketItemGroup(
                    mutableGroup.itemId,
                    mutableGroup.displayStack,
                    mutableGroup.displayName,
                    Collections.unmodifiableList(offers)
            ));
        }

        groups.sort(Comparator.comparingLong(MarketItemGroup::lowestPrice)
                .thenComparing(MarketItemGroup::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MarketItemGroup::itemId));

        snapshot = new MarketSnapshot(Collections.unmodifiableList(groups), discoveredShopIds.size(), viewedShopCount);
        crawling = false;
        statusMessage = groups.isEmpty()
                ? "No market listings were found."
                : "Loaded " + groups.size() + " items from " + viewedShopCount + " shops.";
        bumpVersion();
    }

    private void fail(String message, Exception exception) {
        VHCCTweaks.LOGGER.error(message, exception);
        crawling = false;
        statusMessage = message + ": " + exception.getMessage();
        bumpVersion();
    }

    private void bumpVersion() {
        dataVersion++;
        if (attachedScreen instanceof UniversalMarketScreen screen && Minecraft.getInstance().screen == screen) {
            screen.onMarketDataUpdated();
        }
    }

    private static String preferredName(String current, String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return current;
    }

    private static String displayNameFor(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    private static long invokeLong(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return ((Number) value).longValue();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to read PlayerShops long value from " + methodName, exception);
        }
    }

    private static int invokeInt(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return ((Number) value).intValue();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to read PlayerShops int value from " + methodName, exception);
        }
    }

    private static String invokeString(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to read PlayerShops string value from " + methodName, exception);
        }
    }

    private static ItemStack invokeItemStack(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to read PlayerShops item stack from " + methodName, exception);
        }
    }

    private static final class MutableItemGroup {
        private final String itemId;
        private final ItemStack displayStack;
        private final List<MarketOffer> offers = new ArrayList<>();
        private String displayName;

        private MutableItemGroup(String itemId, ItemStack displayStack, String displayName) {
            this.itemId = itemId;
            this.displayStack = displayStack;
            this.displayName = displayName;
        }
    }

    public record MarketSnapshot(List<MarketItemGroup> groups, int discoveredShopCount, int crawledShopCount) {
        public static MarketSnapshot empty() {
            return new MarketSnapshot(List.of(), 0, 0);
        }
    }

    public record MarketItemGroup(String itemId, ItemStack displayStack, String displayName, List<MarketOffer> offers) {
        public long lowestPrice() {
            return offers.stream().mapToLong(MarketOffer::price).min().orElse(0L);
        }

        public int totalStock() {
            return offers.stream().mapToInt(MarketOffer::stockCount).sum();
        }

        public int sellerCount() {
            Set<Long> shopIds = new LinkedHashSet<>();
            for (MarketOffer offer : offers) {
                shopIds.add(offer.shopId());
            }
            return shopIds.size();
        }

        public boolean matches(String filter) {
            if (filter == null || filter.isBlank()) {
                return true;
            }
            String normalized = filter.toLowerCase(Locale.ROOT);
            return displayName.toLowerCase(Locale.ROOT).contains(normalized)
                    || itemId.toLowerCase(Locale.ROOT).contains(normalized);
        }
    }

    public record MarketOffer(
            String itemId,
            ItemStack displayStack,
            long listingId,
            long shopId,
            String shopName,
            String ownerName,
            boolean serverOwned,
            String itemDisplayName,
            long price,
            int stockCount,
            int sellMode
    ) {
        public String sellerLabel() {
            return serverOwned ? "Server Shop" : ownerName;
        }
    }
}