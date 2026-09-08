package copilot.ui.components;

import java.util.function.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.model.*;
import lombok.extern.slf4j.*;

import javax.swing.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.Component;
import java.awt.event.*;
import java.util.List;
import java.util.*;

@Slf4j
public class ItemSearchBox extends JComboBox<ItemIdName> {
    
    private final BiFunction<String, Set<Integer>, List<ItemIdName>> searchFunction;
    private final Consumer<Integer> onItemSelected;
    private final JTextField editorField;
    private String lastSearchText = "";
    private int lastKeyPress;

    public ItemSearchBox(BiFunction<String, Set<Integer>, List<ItemIdName>> searchFunction,
                        Consumer<Integer> onItemSelected) {
        super();
        this.searchFunction = searchFunction; this.onItemSelected = onItemSelected;

        setBackground(DARKER_GRAY_COLOR);
        setForeground(LIGHT_GRAY_COLOR);
        setMaximumRowCount(15);
        setEditable(true);

        ComboBoxEditor customEditor = new BasicComboBoxEditor() {
            @Override
            protected JTextField createEditorComponent() {
                var field = new JTextField();
                field.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5)); field.setBackground(DARKER_GRAY_COLOR);
                field.setForeground(LIGHT_GRAY_COLOR);
                return field;
            }
        };
        setEditor(customEditor);
        
        editorField = (JTextField) getEditor().getEditorComponent();
        editorField.setText(""); editorField.setForeground(Color.GRAY);

        setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                         int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (c instanceof JLabel && value instanceof ItemIdName) {
                    JLabel label = (JLabel) c;
                    ItemIdName item = (ItemIdName) value;
                    label.setText(item.name); label.setBackground(isSelected ? BRAND_ORANGE : DARK_GRAY_COLOR);
                    label.setForeground(isSelected ? Color.WHITE : LIGHT_GRAY_COLOR);
                    label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
                }
                return c;
            }
        });
        setupListeners();
    }
    
    private void setupListeners() {
        editorField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (editorField.getText().isEmpty()) {
                    editorField.setText(""); editorField.setForeground(LIGHT_GRAY_COLOR);
                }
            }
            
            @Override
            public void focusLost(FocusEvent e) {
                if (editorField.getText().isEmpty()) { editorField.setForeground(Color.GRAY); }
            }
        });

        addActionListener(e -> {
            if (e.getSource() instanceof JComboBox && e.getModifiers() != 0) { onSelectedItem(); }
        });

        editorField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    hidePopup();
                    editorField.transferFocus();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (lastKeyPress == KeyEvent.VK_DOWN || lastKeyPress == KeyEvent.VK_UP || lastKeyPress == KeyEvent.VK_ENTER) {
                        onSelectedItem();
                    } else {
                        performSearch();
                    }
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (!isPopupVisible() && getItemCount() > 0) { showPopup(); }
                }
                lastKeyPress = e.getKeyCode();
            }
        });

        editorField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (editorField.getText().isEmpty()) { editorField.setForeground(LIGHT_GRAY_COLOR); }
            }
        });
    }

    private void onSelectedItem() {
        if(getSelectedItem() instanceof ItemIdName) {
            ItemIdName selected = (ItemIdName) getSelectedItem();
            log.debug("Item selected: {} (ID: {})", selected.name, selected.itemId);
            editorField.setText(selected.name); editorField.setForeground(LIGHT_GRAY_COLOR);
            onItemSelected.accept(selected.itemId);
            hidePopup();
        }
    }

    public void setItem(ItemIdName i) {
        setSelectedItem(i);
        onSelectedItem();
    }
    
    private void performSearch() {
        String searchText = editorField.getText();
        lastSearchText = searchText;
        updateDropdown(searchFunction.apply(searchText, null));
    }
    
    private void updateDropdown(List<ItemIdName> searchResults) {
        removeAllItems();
        for (ItemIdName item : searchResults) { addItem(item); }
        if (!searchResults.isEmpty() && editorField.hasFocus()) {
            showPopup();
            if (!editorField.getText().equals(lastSearchText)) { editorField.setText(lastSearchText); }
        } else {
            hidePopup();
        }
    }

    @Override
    public boolean requestFocusInWindow() { return editorField.requestFocusInWindow(); }

    public void clear() { editorField.setText(""); editorField.setForeground(Color.GRAY); }
}