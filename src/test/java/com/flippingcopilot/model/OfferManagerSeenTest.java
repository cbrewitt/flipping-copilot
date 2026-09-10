package com.flippingcopilot.model;

import com.flippingcopilot.controller.DoesNothingExecutorService;
import com.flippingcopilot.controller.Persistance;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;

import static org.junit.Assert.*;

public class OfferManagerSeenTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private OfferManager manager;

    @Before
    public void setUp() throws Exception {
        Persistance.setUp(tmp.getRoot().getAbsolutePath());
        manager = new OfferManager(new Gson(), new DoesNothingExecutorService());
    }

    private void writeOffer(long hash, int slot, String state) throws Exception {
        writeFile("acc_" + hash + "_" + slot + ".json", "{\"itemId\":26219,\"quantitySold\":0,\"totalQuantity\":2,\"price\":17718861," + "\"spent\":0,\"state\":\"" + state + "\",\"copilotPriceUsed\":true,\"wasCopilotSuggestion\":true}");
    }

    private void writeFile(String name, String content) throws Exception {
        try (FileWriter w = new FileWriter(new File(tmp.getRoot(), name))) {
            w.write(content);
        }
    }

    @Test
    public void theLatestStampWinsAcrossMemoryAndDisk() throws Exception {

        manager.stampSeen(123L, 2000L, null);
        manager.stampSeen(123L, 1000L, null);
        assertEquals(2000L, manager.loadSeen(123L).lastSeen);

        writeFile("acc_123_seen.json", "{\"lastSeen\":5000}");
        assertEquals(5000L, manager.loadSeen(123L).lastSeen);

        manager.stampSeen(123L, 9000L, null);
        writeFile("acc_123_seen.json", "{\"lastSeen\":100}");
        assertEquals(9000L, manager.loadSeen(123L).lastSeen);
    }

    @Test
    public void aStampWithoutANameKeepsTheRecordedOne() {
        manager.stampSeen(123L, 1000L, "Zezima");
        manager.stampSeen(123L, 2000L, null);
        manager.stampSeen(123L, 3000L, "");
        assertEquals("a nameless stamp must not erase the name", "Zezima", manager.loadSeen(123L).name);
    }

    @Test
    public void unknownAccountAndUnparseableFileReadAsNeverSeen() throws Exception {
        assertEquals(0L, manager.loadSeen(999L).lastSeen);
        writeFile("acc_777_seen.json", "{not json");
        assertEquals(0L, manager.loadSeen(777L).lastSeen);
    }


    @Test
    public void returnsRestingOffersJoinedWithSidecar() throws Exception {
        writeOffer(123L, 4, "SELLING");
        manager.stampSeen(123L, 1000L, null);
        OfferManager.EnumerationResult r = manager.listRestingOffers();
        assertEquals(1, r.resting.size());
        OfferManager.RestingOffer ro = r.resting.get(0);
        assertEquals(123L, ro.accountHash);
        assertEquals(4, ro.slot);
        assertEquals(1000L, ro.lastSeen);
        assertEquals(26219, ro.offer.getItemId());
        assertTrue(r.resolvedSlotKeys.contains("123:4"));
    }

    @Test
    public void aClientsOwnSnapshotNeverClobbersAnotherClientsStamps() {
        OfferManager clientB = new OfferManager(new Gson(), new DoesNothingExecutorService());
        manager.stampSeen(123L, 2000L, "Zezima");
        manager.flushSeen();

        // the account moves to a second client, which stamps it forward
        clientB.stampSeen(123L, 6000L, "Zezima");
        clientB.flushSeen();

        // a later write from the first client must not replay its own stale view of the account
        manager.stampSeen(123L, 3000L, "Zezima");
        manager.flushSeen();

        OfferManager fresh = new OfferManager(new Gson(), new DoesNothingExecutorService());
        assertEquals(6000L, fresh.loadSeen(123L).lastSeen);
    }

    @Test
    public void skipsRestingOfferWithoutSidecarEntryAndLeavesSlotKeyUnresolved() throws Exception {
        writeOffer(123L, 4, "SELLING");
        OfferManager.EnumerationResult r = manager.listRestingOffers();
        assertTrue(r.resting.isEmpty());
        assertFalse(r.resolvedSlotKeys.contains("123:4"));
    }

    @Test
    public void nonRestingParsedOfferIsResolvedButNotReturned() throws Exception {
        writeOffer(123L, 3, "SOLD");
        OfferManager.EnumerationResult r = manager.listRestingOffers();
        assertTrue(r.resting.isEmpty());
        assertTrue(r.resolvedSlotKeys.contains("123:3"));
    }

    @Test
    public void ignoresNonOfferFilesNegativeOkAndHashMinusOneExcluded() throws Exception {
        writeOffer(-5L, 0, "BUYING");
        manager.stampSeen(-5L, 1000L, null);
        writeOffer(-1L, 1, "BUYING");
        writeFile("acc_123_seen.json", "{}");
        writeFile("deadbeef_session_data.jsonl", "");
        OfferManager.EnumerationResult r = manager.listRestingOffers();
        assertEquals(1, r.resting.size());
        assertEquals(-5L, r.resting.get(0).accountHash);
    }

    @Test
    public void unparseableOfferFileIsSkippedAndUnresolved() throws Exception {
        writeFile("acc_123_2.json", "{torn");
        writeOffer(123L, 5, "SELLING");
        manager.stampSeen(123L, 1000L, null);
        OfferManager.EnumerationResult r = manager.listRestingOffers();
        assertEquals(1, r.resting.size());
        assertFalse(r.resolvedSlotKeys.contains("123:2"));
        assertTrue(r.resolvedSlotKeys.contains("123:5"));
    }
}
