package com.flippingcopilot.controller;

import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.model.SuggestionType;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class GePreviousSearchTest {

    @Test
    public void suggestionRefreshDoesNotOverwriteActiveSearchResults() {
        AtomicBoolean widgetRead = new AtomicBoolean();
        Client client = proxy(Client.class, (proxy, method, args) -> {
            if (method.getName().equals("getVarcStrValue")) {
                return "water";
            }
            if (method.getName().equals("getWidget")) {
                widgetRead.set(true);
            }
            return defaultValue(method.getReturnType());
        });

        Suggestion suggestion = new Suggestion();
        suggestion.setType(SuggestionType.BUY);
        suggestion.setItemId(26382);
        SuggestionManager suggestionManager = new SuggestionManager();
        suggestionManager.setSuggestion(suggestion);
        GePreviousSearch previousSearch = new GePreviousSearch(
                suggestionManager, null, null, null, null, client);

        previousSearch.showSuggestedItemInSearch();

        Assert.assertFalse(widgetRead.get());
    }

    @Test
    public void stalePreviousSearchHitboxSelectsVisibleCopilotItem() {
        TestWidgets widgets = new TestWidgets("Copilot item:");
        AtomicReference<Object[]> scriptArguments = new AtomicReference<>();
        Client client = proxy(Client.class, (proxy, method, args) -> {
            if (method.getName().equals("getWidget")) {
                return widgets.parent;
            }
            if (method.getName().equals("runScript")) {
                scriptArguments.set((Object[]) args[0]);
            }
            return defaultValue(method.getReturnType());
        });

        Suggestion suggestion = new Suggestion();
        suggestion.setType(SuggestionType.BUY);
        suggestion.setItemId(12926);
        suggestion.setName("Serpentine helm");
        SuggestionManager suggestionManager = new SuggestionManager();
        suggestionManager.setSuggestion(suggestion);
        GePreviousSearch previousSearch = new GePreviousSearch(
                suggestionManager, null, null, null, null, client);

        AtomicReference<String> menuTarget = new AtomicReference<>();
        MenuEntry menuEntry = menuEntry(widgets.clicked, menuTarget);
        previousSearch.updateCopilotMenuEntry(new MenuEntryAdded(menuEntry));
        MenuOptionClicked click = new MenuOptionClicked(menuEntry);
        previousSearch.handleCopilotMenuClick(click);

        Assert.assertEquals("<col=ff9040>Serpentine helm</col>", menuTarget.get());
        Assert.assertTrue(click.isConsumed());
        Assert.assertArrayEquals(new Object[]{754, 12926, 84}, scriptArguments.get());
    }

    @Test
    public void regularSearchResultIsNotIntercepted() {
        TestWidgets widgets = new TestWidgets("Bowl of water");
        AtomicReference<Object[]> scriptArguments = new AtomicReference<>();
        Client client = proxy(Client.class, (proxy, method, args) -> {
            if (method.getName().equals("getWidget")) {
                return widgets.parent;
            }
            if (method.getName().equals("runScript")) {
                scriptArguments.set((Object[]) args[0]);
            }
            return defaultValue(method.getReturnType());
        });

        Suggestion suggestion = new Suggestion();
        suggestion.setType(SuggestionType.BUY);
        suggestion.setItemId(12926);
        SuggestionManager suggestionManager = new SuggestionManager();
        suggestionManager.setSuggestion(suggestion);
        GePreviousSearch previousSearch = new GePreviousSearch(
                suggestionManager, null, null, null, null, client);

        MenuOptionClicked click = new MenuOptionClicked(menuEntry(widgets.clicked, new AtomicReference<>()));
        previousSearch.handleCopilotMenuClick(click);

        Assert.assertFalse(click.isConsumed());
        Assert.assertNull(scriptArguments.get());
    }

    private MenuEntry menuEntry(Widget widget, AtomicReference<String> target) {
        AtomicReference<MenuEntry> entry = new AtomicReference<>();
        entry.set(proxy(MenuEntry.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getOption":
                    return "Select";
                case "getWidget":
                    return widget;
                case "setTarget":
                    target.set((String) args[0]);
                    return entry.get();
                default:
                    return defaultValue(method.getReturnType());
            }
        }));
        return entry.get();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static class TestWidgets {
        private final Widget parent;
        private final Widget clicked;

        private TestWidgets(String labelText) {
            Widget[] children = new Widget[2];
            AtomicReference<Widget> parentReference = new AtomicReference<>();
            parent = proxy(Widget.class, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getChildren":
                        return children;
                    case "getChild":
                        return children[(int) args[0]];
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
            parentReference.set(parent);
            clicked = proxy(Widget.class, (proxy, method, args) -> {
                if (method.getName().equals("getIndex")) {
                    return 0;
                }
                if (method.getName().equals("getParent")) {
                    return parentReference.get();
                }
                return defaultValue(method.getReturnType());
            });
            Widget label = proxy(Widget.class, (proxy, method, args) ->
                    method.getName().equals("getText") ? labelText : defaultValue(method.getReturnType()));
            children[0] = clicked;
            children[1] = label;
        }
    }
}
