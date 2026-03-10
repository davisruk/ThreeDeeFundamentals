package online.davisfamily.threedee.input.keyboard;

import java.awt.event.KeyEvent;
import java.util.BitSet;
import java.util.EnumSet;

public class InputState {
	private final BitSet pressed = new BitSet(512);
	public enum Mode {SHOW_DEBUG_INFO, SHOW_CAMERA_AXES, SHOW_WORLD_AXES, SHOW_WIREFRAME, FILL_MODEL}; 
	private EnumSet<Mode> modes;
	
	public InputState() {
		modes = EnumSet.noneOf(Mode.class);
		modes.add(Mode.FILL_MODEL);
	}
	
	public void setPressed(int vk, boolean isPressed) {
		pressed.set(vk, isPressed);
	}
		
	public boolean isPressed(int vk) {
		return pressed.get(vk);
	}
	
	public boolean w() { return isPressed(KeyEvent.VK_W); }
	public boolean s() { return isPressed(KeyEvent.VK_S); }
	public boolean a() { return isPressed(KeyEvent.VK_A); }
	public boolean d() { return isPressed(KeyEvent.VK_D); }
	public boolean up() { return isPressed(KeyEvent.VK_UP); }
	public boolean down() { return isPressed(KeyEvent.VK_DOWN); }
	public boolean alt() { return isPressed(KeyEvent.VK_ALT); }
		
	public void toggle(Mode mode) {
		if (modes.contains(mode))
			modes.remove(mode);
		else 
			modes.add(mode);
	}
	
	public boolean isSet(Mode m) {
		return modes.contains(m);
	}
}
