package com.flippingcopilot.model;

import org.junit.Test;

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
}
