package br.com.cdip.ecartaftp;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.JViewport;

final class AccessibilityTheme {
    private static final String ORIGINAL_FONT_KEY = "accessibility.originalFont";

    static final Color BACKGROUND = Color.BLACK;
    static final Color FOREGROUND = Color.WHITE;
    static final Color ACTION_BACKGROUND = Color.YELLOW;

    private AccessibilityTheme() {
    }

    static void apply(Container root) {
        style(root);
        for (Component child : root.getComponents()) {
            style(child);
            if (child instanceof Container container) {
                apply(container);
            }
        }
    }

    static void scaleFonts(Container root, float scale) {
        scaleFont(root, scale);
        for (Component child : root.getComponents()) {
            scaleFont(child, scale);
            if (child instanceof Container container) {
                scaleFonts(container, scale);
            }
        }
    }

    private static void scaleFont(Component component, float scale) {
        if (!(component instanceof JComponent swingComponent) || component.getFont() == null) {
            return;
        }
        Object storedFont = swingComponent.getClientProperty(ORIGINAL_FONT_KEY);
        if (storedFont == null) {
            storedFont = component.getFont();
            swingComponent.putClientProperty(ORIGINAL_FONT_KEY, storedFont);
        }
        Font originalFont = (Font) storedFont;
        component.setFont(originalFont.deriveFont(originalFont.getSize2D() * scale));
    }

    private static void style(Component component) {
        if (component instanceof JPanel || component instanceof JViewport) {
            component.setBackground(BACKGROUND);
            if (component instanceof JPanel panel) {
                panel.setOpaque(true);
            }
        } else if (component instanceof JLabel) {
            component.setForeground(FOREGROUND);
        } else if (component instanceof JButton button) {
            button.setBackground(ACTION_BACKGROUND);
            button.setForeground(BACKGROUND);
            button.setOpaque(true);
            button.setFocusPainted(true);
            button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(FOREGROUND, 2),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
            ));
        } else if (component instanceof JTextField field) {
            field.setBackground(BACKGROUND);
            field.setForeground(FOREGROUND);
            field.setCaretColor(ACTION_BACKGROUND);
            field.setBorder(BorderFactory.createLineBorder(FOREGROUND, 2));
        } else if (component instanceof JTextArea area) {
            area.setBackground(BACKGROUND);
            area.setForeground(FOREGROUND);
            area.setCaretColor(ACTION_BACKGROUND);
        } else if (component instanceof JComboBox<?> comboBox) {
            comboBox.setBackground(BACKGROUND);
            comboBox.setForeground(FOREGROUND);
            comboBox.setBorder(BorderFactory.createLineBorder(FOREGROUND, 2));
        } else if (component instanceof JScrollPane scrollPane) {
            scrollPane.setBackground(BACKGROUND);
            scrollPane.setBorder(BorderFactory.createLineBorder(FOREGROUND, 2));
        }
    }
}
