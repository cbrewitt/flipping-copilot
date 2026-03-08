package com.flippingcopilot.controller;

import com.flippingcopilot.model.FlipStatus;
import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.model.OsrsLoginManager;
import com.flippingcopilot.rs.CopilotLoginRS;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InventoryPortfolioService {

    public static final int COINS_ITEM_ID = 995;
    public static final int PLATINUM_TOKENS_ITEM_ID = 13204;

    private final ItemManager itemManager;
    private final FlipManager flipManager;
    private final OsrsLoginManager osrsLoginManager;
    private final CopilotLoginRS copilotLoginRS;

    public int toUnnotedItemId(int itemId) {
        ItemComposition item = itemManager.getItemComposition(itemId);
        if (item.getNote() != -1 && item.getLinkedNoteId() > 0) {
            return item.getLinkedNoteId();
        }
        return itemId;
    }

    public boolean isCurrencyItem(int unnotedItemId) {
        return unnotedItemId == COINS_ITEM_ID || unnotedItemId == PLATINUM_TOKENS_ITEM_ID;
    }

    public Integer getActiveAccountId() {
        String displayName = osrsLoginManager.getPlayerDisplayName();
        if (displayName == null) {
            return null;
        }
        Integer accountId = copilotLoginRS.get().getAccountId(displayName);
        if (accountId == null || accountId == -1) {
            return null;
        }
        return accountId;
    }

    public FlipV2 getOpenFlip(int unnotedItemId, Integer accountId) {
        if (accountId == null) {
            return null;
        }
        FlipV2 flip = flipManager.getLastFlipByItemId(accountId, unnotedItemId);
        if (flip == null || flip.getStatus() == FlipStatus.FINISHED) {
            return null;
        }
        return flip;
    }

    public boolean isInPortfolio(int unnotedItemId, Integer accountId) {
        return getOpenFlip(unnotedItemId, accountId) != null;
    }
}
