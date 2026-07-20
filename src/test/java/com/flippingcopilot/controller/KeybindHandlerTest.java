package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import net.runelite.api.Client;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import org.junit.Assert;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;

public class KeybindHandlerTest {

    @Test
    public void registrationFollowsPluginLifecycle() throws Exception {
        KeyManager keyManager = createKeyManager();
        AtomicInteger keyPressCount = new AtomicInteger();
        FlippingCopilotConfig config = new FlippingCopilotConfig() {
            @Override
            public Keybind quickSetKeybind() {
                keyPressCount.incrementAndGet();
                return Keybind.NOT_SET;
            }

            @Override
            public String webhook() {
                return "";
            }
        };
        KeybindHandler handler = new KeybindHandler(keyManager, config, null, null, null, null, null, null, null);
        KeyEvent keyEvent = new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_E, 'E');

        keyManager.processKeyPressed(keyEvent);
        Assert.assertEquals(0, keyPressCount.get());

        handler.register();
        keyManager.processKeyPressed(keyEvent);
        Assert.assertEquals(1, keyPressCount.get());

        handler.unregister();
        keyManager.processKeyPressed(keyEvent);
        Assert.assertEquals(1, keyPressCount.get());

        handler.register();
        keyManager.processKeyPressed(keyEvent);
        Assert.assertEquals(2, keyPressCount.get());
    }

    private KeyManager createKeyManager() throws Exception {
        Constructor<KeyManager> constructor = KeyManager.class.getDeclaredConstructor(Client.class, EventBus.class);
        constructor.setAccessible(true);
        return constructor.newInstance(null, new EventBus());
    }
}
