package com.vhcctweaks.client.market;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class UniversalMarketScreen extends Screen {

    private static final int ITEMS_PER_PAGE = 8;
    private static final int OFFERS_PER_PAGE = 6;

    private final PlayerShopsMarketService service = PlayerShopsMarketService.getInstance();

    private EditBox searchField;
    private int lastVersion = -1;
    private int itemPage;
    private int offerPage;
    private String selectedItemId;

    public UniversalMarketScreen() {
        super(new TextComponent("Universal Market"));
    }

    @Override
    protected void init() {
        lastVersion = service.getDataVersion();
        rebuildWidgets();
    }

    public void onMarketDataUpdated() {
        if (this.minecraft != null) {
            this.minecraft.execute(this::rebuildWidgets);
        }
    }

    @Override
    public void tick() {
        if (searchField != null) {
            searchField.tick();
        }
        if (lastVersion != service.getDataVersion()) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        drawBackdrop(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);

        int panelLeft = 18;
        int panelTop = 18;
        int panelWidth = this.width - 36;
        int panelHeight = this.height - 36;
        int itemsLeft = panelLeft + 12;
        int itemsTop = panelTop + 52;
        int itemsWidth = Math.min(240, panelWidth / 2 - 18);
        int offersLeft = itemsLeft + itemsWidth + 16;

        drawString(poseStack, this.font, this.title, panelLeft + 12, panelTop + 2, 0xF3E7C8);
        drawString(poseStack, this.font, new TextComponent(service.getStatusMessage()), panelLeft + 12, panelTop + panelHeight - 18, 0xC7C2B1);

        List<PlayerShopsMarketService.MarketItemGroup> filteredGroups = filteredGroups();
        PlayerShopsMarketService.MarketItemGroup selectedGroup = selectedGroup(filteredGroups);

        drawString(poseStack, this.font, new TextComponent("Items"), itemsLeft, itemsTop - 12, 0xFFFFFF);
        drawString(poseStack, this.font, new TextComponent("Offers"), offersLeft, itemsTop - 12, 0xFFFFFF);

        if (selectedGroup != null) {
            ItemStack stack = selectedGroup.displayStack();
            this.itemRenderer.renderAndDecorateItem(stack, offersLeft, itemsTop + 2);
            drawString(poseStack, this.font, new TextComponent(selectedGroup.displayName()), offersLeft + 20, itemsTop + 4, 0xF3E7C8);
            drawString(poseStack, this.font,
                    new TextComponent(selectedGroup.sellerCount() + " sellers  |  " + selectedGroup.totalStock() + " stock  |  from " + selectedGroup.lowestPrice()),
                    offersLeft + 20, itemsTop + 16, 0xC7C2B1);
        }

        renderItemRows(poseStack, filteredGroups);
        renderOfferRows(poseStack, selectedGroup);

        if (service.isCrawling() && service.getSnapshot().groups().isEmpty()) {
            drawCenteredString(poseStack, this.font, new TextComponent("Building market index..."), this.width / 2, this.height / 2 - 8, 0xFFFFFF);
            drawCenteredString(poseStack, this.font, new TextComponent("This uses the existing PlayerShops data and may take a moment."), this.width / 2, this.height / 2 + 6, 0xC7C2B1);
        } else if (filteredGroups.isEmpty()) {
            drawCenteredString(poseStack, this.font, new TextComponent("No items matched your search."), this.width / 2, this.height / 2, 0xFFFFFF);
        }
    }

    @Override
    public void onClose() {
        service.detachScreen(this);
        Minecraft.getInstance().setScreen(null);
    }

    private void rebuildWidgets() {
        lastVersion = service.getDataVersion();
        String searchText = searchField == null ? "" : searchField.getValue();
        boolean restoreFocus = searchField != null && searchField.isFocused();
        clearWidgets();

        int panelLeft = 18;
        int panelTop = 18;
        int panelWidth = this.width - 36;

        searchField = new EditBox(this.font, panelLeft + 12, panelTop + 16, Math.min(220, panelWidth - 180), 20, new TextComponent("Search items"));
        searchField.setMaxLength(64);
        searchField.setValue(searchText);
        searchField.setResponder(value -> {
            itemPage = 0;
            offerPage = 0;
            ensureSelection();
            rebuildWidgets();
        });
        addRenderableWidget(searchField);
        if (restoreFocus) {
            setFocused(searchField);
            searchField.setFocus(true);
        }

        addRenderableWidget(new Button(panelLeft + panelWidth - 150, panelTop + 14, 64, 20,
            new TextComponent("Refresh"), button -> service.startRefresh()));
        addRenderableWidget(new Button(panelLeft + panelWidth - 78, panelTop + 14, 64, 20,
            new TextComponent("Close"), button -> onClose()));

        addItemButtons();
        addOfferButtons();
    }

    private void drawBackdrop(PoseStack poseStack) {
        int panelLeft = 18;
        int panelTop = 18;
        int panelRight = this.width - 18;
        int panelBottom = this.height - 18;
        fillGradient(poseStack, panelLeft, panelTop, panelRight, panelBottom, 0xF01D1B1A, 0xF02A2723);
        GuiComponent.fill(poseStack, panelLeft, panelTop, panelRight, panelTop + 1, 0xFF6C5B3A);
        GuiComponent.fill(poseStack, panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF6C5B3A);
        GuiComponent.fill(poseStack, panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF6C5B3A);
        GuiComponent.fill(poseStack, panelRight - 1, panelTop, panelRight, panelBottom, 0xFF6C5B3A);

        int itemsLeft = panelLeft + 12;
        int itemsTop = panelTop + 52;
        int panelWidth = this.width - 36;
        int itemsWidth = Math.min(240, panelWidth / 2 - 18);
        int offersLeft = itemsLeft + itemsWidth + 16;
        int offersRight = panelRight - 12;
        int contentBottom = panelBottom - 28;
        fillGradient(poseStack, itemsLeft - 6, itemsTop - 6, itemsLeft + itemsWidth, contentBottom, 0x662A241D, 0x6640342A);
        fillGradient(poseStack, offersLeft - 6, itemsTop - 6, offersRight, contentBottom, 0x662A241D, 0x6640342A);
    }

    private List<PlayerShopsMarketService.MarketItemGroup> filteredGroups() {
        String filter = searchField == null ? "" : searchField.getValue();
        List<PlayerShopsMarketService.MarketItemGroup> filtered = new ArrayList<>();
        for (PlayerShopsMarketService.MarketItemGroup group : service.getSnapshot().groups()) {
            if (group.matches(filter)) {
                filtered.add(group);
            }
        }
        filtered.sort(Comparator.comparingLong(PlayerShopsMarketService.MarketItemGroup::lowestPrice)
                .thenComparing(PlayerShopsMarketService.MarketItemGroup::displayName, String.CASE_INSENSITIVE_ORDER));
        return filtered;
    }

    private void ensureSelection() {
        List<PlayerShopsMarketService.MarketItemGroup> filtered = filteredGroups();
        if (filtered.isEmpty()) {
            selectedItemId = null;
            return;
        }

        for (PlayerShopsMarketService.MarketItemGroup group : filtered) {
            if (group.itemId().equals(selectedItemId)) {
                return;
            }
        }

        selectedItemId = filtered.get(0).itemId();
    }

    private PlayerShopsMarketService.MarketItemGroup selectedGroup(List<PlayerShopsMarketService.MarketItemGroup> filteredGroups) {
        ensureSelection();
        for (PlayerShopsMarketService.MarketItemGroup group : filteredGroups) {
            if (group.itemId().equals(selectedItemId)) {
                return group;
            }
        }
        return filteredGroups.isEmpty() ? null : filteredGroups.get(0);
    }

    private void addItemButtons() {
        List<PlayerShopsMarketService.MarketItemGroup> filteredGroups = filteredGroups();
        ensureSelection();

        int panelLeft = 18;
        int panelTop = 18;
        int panelWidth = this.width - 36;
        int itemsLeft = panelLeft + 12;
        int itemsTop = panelTop + 52;
        int itemsWidth = Math.min(240, panelWidth / 2 - 18);
        int listHeight = 22;

        int itemPages = Math.max(1, (filteredGroups.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        itemPage = Math.max(0, Math.min(itemPage, itemPages - 1));
        int start = itemPage * ITEMS_PER_PAGE;
        int end = Math.min(filteredGroups.size(), start + ITEMS_PER_PAGE);

        for (int index = start; index < end; index++) {
            PlayerShopsMarketService.MarketItemGroup group = filteredGroups.get(index);
            int row = index - start;
            int y = itemsTop + row * listHeight;
            boolean selected = group.itemId().equals(selectedItemId);
            Button button = new Button(itemsLeft, y, itemsWidth - 18, 20,
                    new TextComponent(" "), clicked -> {
                selectedItemId = group.itemId();
                offerPage = 0;
                rebuildWidgets();
            });
            button.active = !selected;
            addRenderableWidget(button);
        }

        addRenderableWidget(new Button(itemsLeft, itemsTop + ITEMS_PER_PAGE * listHeight + 2, 20, 20,
                new TextComponent("<"), button -> {
            itemPage = Math.max(0, itemPage - 1);
            rebuildWidgets();
        }));
        addRenderableWidget(new Button(itemsLeft + itemsWidth - 38, itemsTop + ITEMS_PER_PAGE * listHeight + 2, 20, 20,
                new TextComponent(">"), button -> {
            itemPage = Math.min(itemPages - 1, itemPage + 1);
            rebuildWidgets();
        }));
    }

    private void addOfferButtons() {
        List<PlayerShopsMarketService.MarketItemGroup> filteredGroups = filteredGroups();
        PlayerShopsMarketService.MarketItemGroup group = selectedGroup(filteredGroups);
        if (group == null) {
            return;
        }

        int panelLeft = 18;
        int panelTop = 18;
        int panelWidth = this.width - 36;
        int itemsLeft = panelLeft + 12;
        int itemsWidth = Math.min(240, panelWidth / 2 - 18);
        int offersLeft = itemsLeft + itemsWidth + 16;
        int offersWidth = this.width - 30 - offersLeft;
        int offersTop = panelTop + 82;
        int rowHeight = 24;

        int offerPages = Math.max(1, (group.offers().size() + OFFERS_PER_PAGE - 1) / OFFERS_PER_PAGE);
        offerPage = Math.max(0, Math.min(offerPage, offerPages - 1));
        int start = offerPage * OFFERS_PER_PAGE;
        int end = Math.min(group.offers().size(), start + OFFERS_PER_PAGE);

        for (int index = start; index < end; index++) {
            PlayerShopsMarketService.MarketOffer offer = group.offers().get(index);
            int row = index - start;
            int y = offersTop + row * rowHeight;

            addRenderableWidget(new Button(offersLeft + offersWidth - 48, y, 44, 20,
                    new TextComponent("Buy"), button -> service.purchaseOffer(offer)));
        }

        addRenderableWidget(new Button(offersLeft, offersTop + OFFERS_PER_PAGE * rowHeight + 2, 20, 20,
                new TextComponent("<"), button -> {
            offerPage = Math.max(0, offerPage - 1);
            rebuildWidgets();
        }));
        addRenderableWidget(new Button(offersLeft + 48, offersTop + OFFERS_PER_PAGE * rowHeight + 2, 20, 20,
                new TextComponent(">"), button -> {
            offerPage = Math.min(offerPages - 1, offerPage + 1);
            rebuildWidgets();
        }));
    }

    private void renderItemRows(PoseStack poseStack, List<PlayerShopsMarketService.MarketItemGroup> filteredGroups) {
        int panelLeft = 18;
        int panelTop = 18;
        int panelWidth = this.width - 36;
        int itemsLeft = panelLeft + 12;
        int itemsTop = panelTop + 52;
        int itemsWidth = Math.min(240, panelWidth / 2 - 18);
        int listHeight = 22;
        int itemPages = Math.max(1, (filteredGroups.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        int start = itemPage * ITEMS_PER_PAGE;
        int end = Math.min(filteredGroups.size(), start + ITEMS_PER_PAGE);

        for (int index = start; index < end; index++) {
            PlayerShopsMarketService.MarketItemGroup group = filteredGroups.get(index);
            int row = index - start;
            int y = itemsTop + row * listHeight;
            this.itemRenderer.renderAndDecorateItem(group.displayStack(), itemsLeft + 2, y + 2);
            drawString(poseStack, this.font, new TextComponent(abbreviate(group.displayName(), 16)), itemsLeft + 22, y + 2, 0xF3E7C8);
            drawString(poseStack, this.font, new TextComponent(group.lowestPrice() + "  |  " + group.totalStock()), itemsLeft + 22, y + 12, 0xC7C2B1);
        }

        drawString(poseStack, this.font, new TextComponent((itemPage + 1) + "/" + itemPages), itemsLeft + 92, itemsTop + ITEMS_PER_PAGE * listHeight + 8, 0xC7C2B1);
    }

    private void renderOfferRows(PoseStack poseStack, PlayerShopsMarketService.MarketItemGroup group) {
        if (group == null) {
            return;
        }

        int panelLeft = 18;
        int panelTop = 18;
        int panelWidth = this.width - 36;
        int itemsLeft = panelLeft + 12;
        int itemsWidth = Math.min(240, panelWidth / 2 - 18);
        int offersLeft = itemsLeft + itemsWidth + 16;
        int offersWidth = this.width - 30 - offersLeft;
        int offersTop = panelTop + 82;
        int rowHeight = 24;
        int offerPages = Math.max(1, (group.offers().size() + OFFERS_PER_PAGE - 1) / OFFERS_PER_PAGE);
        int start = offerPage * OFFERS_PER_PAGE;
        int end = Math.min(group.offers().size(), start + OFFERS_PER_PAGE);

        for (int index = start; index < end; index++) {
            PlayerShopsMarketService.MarketOffer offer = group.offers().get(index);
            int row = index - start;
            int y = offersTop + row * rowHeight;
            this.itemRenderer.renderAndDecorateItem(offer.displayStack(), offersLeft, y + 2);
            drawString(poseStack, this.font, new TextComponent(abbreviate(offer.shopName(), 18)), offersLeft + 20, y + 1, 0xF3E7C8);
            drawString(poseStack, this.font, new TextComponent(offer.sellerLabel() + "  |  " + offer.stockCount() + " stock"), offersLeft + 20, y + 11, 0xC7C2B1);
            drawString(poseStack, this.font, new TextComponent(String.valueOf(offer.price())), offersLeft + offersWidth - 94, y + 7, 0xFFF0A8);
        }

        drawString(poseStack, this.font, new TextComponent((offerPage + 1) + "/" + offerPages), offersLeft + 24, offersTop + OFFERS_PER_PAGE * rowHeight + 8, 0xC7C2B1);
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength - 1));
    }
}