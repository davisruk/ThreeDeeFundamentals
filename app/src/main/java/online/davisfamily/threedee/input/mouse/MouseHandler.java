package online.davisfamily.threedee.input.mouse;

import java.awt.event.MouseEvent;

import javax.swing.event.MouseInputAdapter;

public class MouseHandler extends MouseInputAdapter {
	private MouseEventConsumer consumer;
	private MouseEventDetail detail = new MouseEventDetail();

	public MouseHandler (MouseEventConsumer consumer) {
		this.consumer = consumer;
	}
	
	@Override
    public void mouseMoved(MouseEvent e){
		detail.oldx = detail.x;
		detail.x = e.getX();
		detail.oldy = detail.y;
		detail.y = e.getY();
		detail.consumed = false;
		consumer.consume(detail);
	}
}
