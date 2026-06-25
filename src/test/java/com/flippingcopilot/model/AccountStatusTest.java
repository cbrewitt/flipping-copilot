package com.flippingcopilot.model;

import org.junit.Test;

import java.util.Collections;

public class AccountStatusTest {
    private static final int ITEM_ID = 4151;

    @Test
    public void testMoreGpNeeded() {
        AccountStatus accountStatus = new AccountStatus();
        assert accountStatus.moreGpNeeded();
    }

    @Test
    public void testNoMoreGpNeeded() {
        AccountStatus accountStatus = new AccountStatus();
        accountStatus.getInventory().add(new RSItem(995, 2000));
        assert !accountStatus.moreGpNeeded();
    }

    @Test
    public void testBuyAndHoldModeDefaultsToTrue() {
        AccountStatus accountStatus = new AccountStatus();
        assert accountStatus.isBuyAndHold();
    }

    @Test
    public void testHasSufficientInventoryForSellSuggestionRequiresInventoryQuantity() {
        AccountStatus accountStatus = new AccountStatus();
        accountStatus.getInventory().add(new RSItem(ITEM_ID, 3));
        accountStatus.setBankAvailable(true);
        accountStatus.setBankInventory(Collections.singletonMap(ITEM_ID, 10));
        Suggestion suggestion = sellSuggestion(5);

        assert !accountStatus.hasSufficientInventoryForSellSuggestion(suggestion);
        assert !accountStatus.isCollectNeeded(suggestion, false);
    }

    @Test
    public void testHasSufficientInventoryForSellSuggestionWhenInventoryQuantityMatches() {
        AccountStatus accountStatus = new AccountStatus();
        accountStatus.getInventory().add(new RSItem(ITEM_ID, 5));
        Suggestion suggestion = sellSuggestion(5);

        assert accountStatus.hasSufficientInventoryForSellSuggestion(suggestion);
    }

    @Test
    public void testEncodeProtoIncludesBuyAndHoldAtField30() {
        AccountStatus accountStatus = new AccountStatus();
        accountStatus.setUncollected(Collections.emptyMap());

        byte[] payload = accountStatus.encodeProto(true, false);

        assert contains(payload, new byte[]{(byte) 0xF0, 0x01, 0x01});
        assert !contains(payload, new byte[]{(byte) 0xE8, 0x01, 0x01});
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static Suggestion sellSuggestion(int quantity) {
        Suggestion suggestion = new Suggestion();
        suggestion.setType(SuggestionType.SELL);
        suggestion.setItemId(ITEM_ID);
        suggestion.setQuantity(quantity);
        return suggestion;
    }
}
