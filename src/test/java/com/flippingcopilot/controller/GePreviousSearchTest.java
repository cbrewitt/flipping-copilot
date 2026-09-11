package com.flippingcopilot.controller;

import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.model.SuggestionType;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class GePreviousSearchTest {
    @Test
    public void refreshLeavesActiveSearchResultsUntouched() {
        Fixture f = new Fixture();
        f.query = "water";
        f.handler.showSuggestedItemInSearch();
        assertEquals(0, f.widgetReads);
    }

    @Test
    public void refreshIgnoresUnavailableInput() {
        Fixture f = new Fixture();
        f.query = null;
        f.handler.showSuggestedItemInSearch();
        assertEquals(0, f.widgetReads);
    }

    @Test
    public void copilotRowCorrectsStaleMenuTargetAndSelection() {
        Fixture f = new Fixture();
        f.handler.updateCopilotMenuEntry(new MenuEntryAdded(f.entry));
        f.handler.handleCopilotMenuClick(f.click);
        assertEquals("<col=ff9040>Serpentine helm</col>", f.target);
        assertTrue(f.click.isConsumed());
        assertArrayEquals(new Object[]{754, 12929, 84}, f.script);
    }

    @Test
    public void ordinaryResultIsNotIntercepted() {
        Fixture f = new Fixture();
        f.label = "Bowl of water";
        f.handler.handleCopilotMenuClick(f.click);
        assertFalse(f.click.isConsumed());
        assertNull(f.script);
    }

    @Test
    public void activeQueryIsNotInterceptedEvenWithStaleCopilotLabel() {
        Fixture f = new Fixture();
        f.query = "water";
        f.handler.handleCopilotMenuClick(f.click);
        assertFalse(f.click.isConsumed());
        assertNull(f.script);
    }

    private static class Fixture {
        String query = "";
        String label = "Copilot item:";
        String target = "<col=ff9040>Ancestral robe top</col>";
        int widgetReads;
        Object[] script;
        Widget parent;
        Widget clicked;
        MenuEntry entry;
        MenuOptionClicked click;
        GePreviousSearch handler;

        Fixture() {
            Widget[] children = new Widget[2];
            parent = proxy(Widget.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "getChildren": return children;
                    case "getChild": return children[(int) a[0]];
                    default: throw new AssertionError(m.getName());
                }
            });
            clicked = proxy(Widget.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "getIndex": return 0;
                    case "getParent": return parent;
                    case "getText": return "";
                    default: throw new AssertionError(m.getName());
                }
            });
            children[0] = clicked;
            children[1] = proxy(Widget.class, (p, m, a) -> {
                if (m.getName().equals("getText")) return label;
                throw new AssertionError(m.getName());
            });
            Client client = proxy(Client.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "getVarcStrValue": return query;
                    case "getWidget": widgetReads++; return parent;
                    case "runScript": script = (Object[]) a[0]; return null;
                    default: throw new AssertionError(m.getName());
                }
            });
            entry = proxy(MenuEntry.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "getOption": return "Select";
                    case "getWidget": return clicked;
                    case "setTarget": target = (String) a[0]; return p;
                    default: throw new AssertionError(m.getName());
                }
            });
            click = new MenuOptionClicked(entry);
            Suggestion suggestion = new Suggestion();
            suggestion.setType(SuggestionType.BUY);
            suggestion.setItemId(12929);
            suggestion.setName("Serpentine helm");
            SuggestionManager manager = new SuggestionManager();
            manager.setSuggestion(suggestion);
            handler = new GePreviousSearch(manager, null, null, null, null, client);
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
