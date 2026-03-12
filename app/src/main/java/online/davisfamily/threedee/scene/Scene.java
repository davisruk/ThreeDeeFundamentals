package online.davisfamily.threedee.scene;

import java.awt.image.BufferedImage;

import online.davisfamily.threedee.input.mouse.MouseEventConsumer;

public interface Scene extends MouseEventConsumer {
	public BufferedImage getImage();
	public void renderFrame(double tSeconds);
}
