package online.davisfamily.threedee.input.keyboard;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/*
 * Alternative implementation of KeyBindings for Swing
 * Explicitly references KeyEvent VK enumerations
 * rather than relying on strings that contain the key name
 */
public final class KeyBindings {
	public static void installKeyBindings(JComponent target, InputState input) {
		InputMap im = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = target.getActionMap();
		bind(im, am, input, "VK_W", KeyEvent.VK_W);
		bind(im, am, input, "VK_S", KeyEvent.VK_S);
		bind(im, am, input, "VK_A", KeyEvent.VK_A);
		bind(im, am, input, "VK_D", KeyEvent.VK_D);
		bind(im, am, input, "VK_UP", KeyEvent.VK_UP);
		bind(im, am, input, "VK_DOWN", KeyEvent.VK_DOWN);
		
		// the following are not strictly necessary, just here
		// to record the key presses that are bound by commands.
		// commands will still work without these bindings.
		bind(im, am, input, "VK_C", KeyEvent.VK_C); 
		bind(im, am, input, "VK_ALT", KeyEvent.VK_ALT);
		bind(im, am, input, "VK_I", KeyEvent.VK_I);
	}
	
	private static void bind(InputMap im, ActionMap am, InputState input, String keyName, int vk) {
		KeyStroke pressedKey = KeyStroke.getKeyStroke(vk,0,false);
		KeyStroke releasedKey = KeyStroke.getKeyStroke(vk,0,true);
		String pName = keyName + ":pressed";
		String rName = keyName + ":released";
		im.put(pressedKey, pName);
		im.put(releasedKey, rName);
		
		am.put(pName, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				input.setPressed(vk, true);
			}
		});

		am.put(rName, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				input.setPressed(vk, false);
			}
		});
	}
}
