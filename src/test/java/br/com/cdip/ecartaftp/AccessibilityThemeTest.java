package br.com.cdip.ecartaftp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Font;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class AccessibilityThemeTest {

    @Test
    void shouldApplyHighContrastToLabelsFieldsAndActions() {
        JPanel root = new JPanel();
        JLabel label = new JLabel("Planilha");
        JTextField field = new JTextField();
        JButton button = new JButton("Processar");
        root.add(label);
        root.add(field);
        root.add(button);

        AccessibilityTheme.apply(root);

        assertEquals(AccessibilityTheme.BACKGROUND, root.getBackground());
        assertEquals(AccessibilityTheme.FOREGROUND, label.getForeground());
        assertEquals(AccessibilityTheme.BACKGROUND, field.getBackground());
        assertEquals(AccessibilityTheme.FOREGROUND, field.getForeground());
        assertEquals(AccessibilityTheme.ACTION_BACKGROUND, field.getCaretColor());
        assertEquals(AccessibilityTheme.ACTION_BACKGROUND, button.getBackground());
        assertEquals(AccessibilityTheme.BACKGROUND, button.getForeground());
        assertEquals(true, button.isFocusPainted());
    }

    @Test
    void shouldScaleFontsFromOriginalSizeWithoutCumulativeDrift() {
        JPanel root = new JPanel();
        JLabel label = new JLabel("Planilha");
        root.add(label);
        Font original = label.getFont();

        AccessibilityTheme.scaleFonts(root, 1.2f);
        assertEquals(original.getSize2D() * 1.2f, label.getFont().getSize2D(), 0.1f);

        AccessibilityTheme.scaleFonts(root, 1.0f);
        assertEquals(original.getSize2D(), label.getFont().getSize2D(), 0.1f);
    }
}
