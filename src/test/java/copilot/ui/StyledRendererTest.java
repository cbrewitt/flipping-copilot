package copilot.ui;

import org.junit.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

import static org.junit.Assert.*;

public class StyledRendererTest {
    @Test
    public void preservesSwingDefaultsBeforeStylingEveryCell() {
        JTable table = new JTable(2, 2);
        table.setForeground(Color.BLACK);
        table.setBackground(Color.WHITE);
        table.setSelectionForeground(Color.WHITE);
        table.setSelectionBackground(Color.BLUE);
        DefaultTableCellRenderer original = new DefaultTableCellRenderer();
        UIUtilities.StyledRenderer renderer = new UIUtilities.StyledRenderer() {
            @Override
            protected void style(JTable owner, Object value, boolean selected, int row) {
                assertSame(table, owner);
                assertEquals(original.getText(), getText());
                assertEquals(original.getForeground(), getForeground());
                assertEquals(original.getBackground(), getBackground());
                assertEquals(original.getBorder(), getBorder());
                setText("styled " + row);
            }
        };
        for (boolean selected : new boolean[]{false, true, false}) {
            for (boolean focused : new boolean[]{false, true}) {
                for (int row = 0; row < 2; row++) {
                    Object value = row == 0 ? 100L : null;
                    original.getTableCellRendererComponent(table, value, selected, focused, row, 0);
                    assertSame(renderer, renderer.getTableCellRendererComponent(table, value, selected, focused, row, 0));
                    assertEquals("styled " + row, renderer.getText());
                }
            }
        }
    }
}
