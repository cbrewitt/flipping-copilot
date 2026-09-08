package copilot.model;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class Stats {

    public long profit, gross;
    public long taxPaid;
    public int flipsMade;

    public float calculateRoi() {
        if (gross == 0) { return 0; }
        return (float) (((double) profit) / ((double) gross));
    }

    public Stats copy() { return new Stats(profit, gross, taxPaid, flipsMade); }

    public void add(Stats s) {
        if(s != null) {
            profit += s.profit;
            gross += s.gross;
            taxPaid += s.taxPaid;
            flipsMade += s.flipsMade;
        }
    }

    public void addFlip(Flip flip) { applyFlip(flip, 1); }

    public void subtractFlip(Flip flip) { applyFlip(flip, -1); }

    private void applyFlip(Flip flip, int direction) {
        if (flip == null || !PortfolioId.isInPortfolio(flip.portfolioId)) { return; }
        profit += direction * flip.profit;
        gross += direction * flip.spent;
        taxPaid += direction * flip.taxPaid;
        flipsMade += direction;
    }
}
