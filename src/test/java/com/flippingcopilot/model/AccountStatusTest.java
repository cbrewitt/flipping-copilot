package com.flippingcopilot.model;

import com.google.gson.Gson;
import org.junit.Test;

import java.util.Collections;

public class AccountStatusTest {

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
    public void testToJsonIncludesBuyAndHoldMode() {
        AccountStatus accountStatus = new AccountStatus();
        accountStatus.setUncollected(Collections.emptyMap());

        assert accountStatus.toJson(new Gson(), true, false).get("buy_and_hold").getAsBoolean();
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
}
