package copilot.controller;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FilenameHashTest {
    @Test public void hashesPreserveExistingFilenameEncoding() {
        // Fixed SHA-1 vectors generated independently from UTF-8 bytes.
        String[][] cases = {
                {"", "da39a3ee5e6b4b0d3255bfef95601890afd80709"},
                {"abc", "a9993e364706816aba3e25717850c26c9cd0d89d"},
                {"Mixed Name_42", "d75703b928875250d21884340ac84afa63c2d6f0"},
                {"null", "2be88ca4242c76e8253ac62474851065032d6833"},
                {"Rune caf\u00e9 \ud83d\ude42", "03461fe52fff6f13e1887350bde464ea41bd00d7"},
                {"\ud800", "5bab61eb53176449e25c2c82f172b82cb13ffb9d"}
        };
        assertEquals("null", Persistance.hashDisplayName(null));
        for (String[] sample : cases) {
            assertEquals(sample[0], sample[1], Persistance.hashDisplayName(sample[0]));
        }
    }
}
