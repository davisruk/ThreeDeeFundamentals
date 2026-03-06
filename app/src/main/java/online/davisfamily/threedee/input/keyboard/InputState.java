package online.davisfamily.threedee.input.keyboard;

import java.awt.event.KeyEvent;
import java.util.BitSet;

public class InputState {
	private final BitSet pressed = new BitSet(512);
	
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
}
