package copilot.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class StatsArithmeticTest {
    @Test public void sharedUpdatesPreserveFilteringAndOverflow() {
        for (int portfolio = -5; portfolio <= 3; portfolio++) {
            for (long value : new long[]{0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                Flip flip = new Flip(); flip.portfolioId = portfolio;
                flip.profit = value; flip.spent = value; flip.taxPaid = value;
                Stats initial = new Stats(Long.MAX_VALUE, Long.MIN_VALUE, 17, Integer.MAX_VALUE);
                Stats added = initial.copy(), subtracted = initial.copy();
                added.addFlip(flip); subtracted.subtractFlip(flip);
                boolean included = portfolio == 0 || portfolio == 1;
                assertEquals(new Stats(included ? initial.profit + value : initial.profit,
                        included ? initial.gross + value : initial.gross,
                        included ? initial.taxPaid + value : initial.taxPaid,
                        included ? initial.flipsMade + 1 : initial.flipsMade), added);
                assertEquals(new Stats(included ? initial.profit - value : initial.profit,
                        included ? initial.gross - value : initial.gross,
                        included ? initial.taxPaid - value : initial.taxPaid,
                        included ? initial.flipsMade - 1 : initial.flipsMade), subtracted);
                added.subtractFlip(flip);
                assertEquals(initial, added);
                added.addFlip(null); added.subtractFlip(null);
                assertEquals(initial, added);
            }
        }
    }
}
