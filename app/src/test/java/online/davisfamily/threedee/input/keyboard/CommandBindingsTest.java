package online.davisfamily.threedee.input.keyboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.Test;

class CommandBindingsTest {

    @Test
    void shouldRequestSimulationResetWhenAltRIsReleased() {
        JRootPane target = new JRootPane();
        InputState inputState = new InputState();
        CommandBindings.installCommandBindings(target, inputState);
        KeyStroke altRReleased = KeyStroke.getKeyStroke(
                KeyEvent.VK_R,
                KeyEvent.ALT_DOWN_MASK,
                true);
        Object actionKey = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(altRReleased);

        assertNotNull(actionKey);
        Action action = target.getActionMap().get(actionKey);
        assertNotNull(action);
        assertFalse(inputState.consumeSimulationResetRequest());

        action.actionPerformed(new ActionEvent(target, ActionEvent.ACTION_PERFORMED, "simulation.reset"));

        assertTrue(inputState.consumeSimulationResetRequest());
        assertFalse(inputState.consumeSimulationResetRequest());
    }
}
