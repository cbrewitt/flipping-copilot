package copilot.controller;
import java.util.regex.*;
final class ReferenceTooltipParser {
    public boolean isItemSelling(String text) {
        text = text.replaceAll("<br>", " ").trim();
        Pattern pattern = Pattern.compile("^(Buying|Selling): (.+) (\\d{1,3}(?:,\\d{3})*|\\d+) / (\\d{1,3}(?:,\\d{3})*|\\d+)( Profit: -?[\\d,]+ gp?)?$");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String action = matcher.group(1);
            return action.equals("Selling");
        } else {
            return false; // or handle as you wish
        }
    }

    public String getItemNameFromTooltipText(String text) {
        text = text.replaceAll("<br>", " ").trim();
        Pattern pattern = Pattern.compile("^(Buying|Selling): (.+) (\\d{1,3}(?:,\\d{3})*|\\d+) / (\\d{1,3}(?:,\\d{3})*|\\d+)( Profit: -?[\\d,]+ gp?)?$");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) { return matcher.group(2); } else {
            return null; // or handle as you wish
        }
    }

}
