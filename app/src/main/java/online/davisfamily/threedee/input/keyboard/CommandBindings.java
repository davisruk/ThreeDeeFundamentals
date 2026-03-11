package online.davisfamily.threedee.input.keyboard;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import online.davisfamily.threedee.input.keyboard.InputState.Mode;

public class CommandBindings {

	public static void installCommandBindings (JComponent target, InputState is) {
		InputMap im = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = target.getActionMap();

		bindToggle(Mode.SHOW_CAMERA_AXES, im, am, KeyEvent.VK_C, KeyEvent.ALT_DOWN_MASK, true, is);
		bindToggle(Mode.SHOW_DEBUG_INFO, im, am, KeyEvent.VK_I, KeyEvent.ALT_DOWN_MASK, true, is);
		bindToggle(Mode.SHOW_WORLD_AXES, im, am, KeyEvent.VK_X, KeyEvent.ALT_DOWN_MASK, true, is);
		bindToggle(Mode.SHOW_WIREFRAME, im, am, KeyEvent.VK_L, KeyEvent.ALT_DOWN_MASK, true, is);
		bindToggle(Mode.FILL_MODEL, im, am, KeyEvent.VK_M, KeyEvent.ALT_DOWN_MASK, true, is);
		bindToggle(Mode.SHOW_GRID, im, am, KeyEvent.VK_G, KeyEvent.ALT_DOWN_MASK, true, is);
	}
	
	
	private static void bindToggle(Mode mode, InputMap im, ActionMap am, int vk, int modifiers, boolean onRelease, InputState is) {
		bindCommand (im, am, "toggle." + mode, vk, modifiers, true, () -> is.toggle(mode));
	}
	
	private static void bindCommand(InputMap im, ActionMap am, String name, int vk, int modifiers, boolean onRelease, Runnable action) {
		
		im.put(KeyStroke.getKeyStroke(vk, modifiers, onRelease), name);
		am.put(name, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		});
	}
}
