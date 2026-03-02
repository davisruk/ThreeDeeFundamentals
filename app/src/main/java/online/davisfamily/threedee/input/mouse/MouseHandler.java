package online.davisfamily.threedee.input.mouse;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;

public class MouseHandler extends MouseInputAdapter {
	private MouseEventConsumer consumer;
	private MouseEventDetail detail = new MouseEventDetail();
	private Component component;
	private boolean mouseCaptured = false;
	private Robot robot;
	private Cursor defaultCursor;
	private Cursor invisibleCursor;
	private int centreScreenX, centreScreenY;
	private boolean warping = false;
	
	public MouseHandler (MouseEventConsumer consumer, Component component) {
		this.consumer = consumer;
		try {
			robot = new Robot();
		} catch (AWTException e) {
			System.out.println(e.getMessage());
		}
		this.component = component;
		initCursors();
	}
	
	private void initCursors() {
	    defaultCursor = component.getCursor();

	    BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
	    invisibleCursor = Toolkit.getDefaultToolkit()
	            .createCustomCursor(img, new Point(0, 0), "invisible");
	}
	
	@Override
	public void mousePressed(MouseEvent e) {

		if (SwingUtilities.isLeftMouseButton(e)) {
            toggleMouseCapture(component);
        }
    }
	
	private void updateCentre(Component component) {
	    Point p = component.getLocationOnScreen();
	    centreScreenX = p.x + component.getWidth() / 2;
	    centreScreenY = p.y + component.getHeight() / 2;
	}
	
	private void toggleMouseCapture(Component component) {
	    mouseCaptured = !mouseCaptured;
	    System.out.println(String.format("Mouse Capture triggered: %b", mouseCaptured));
	    if (mouseCaptured) {
	        component.setCursor(invisibleCursor);
	        updateCentre(component);
	        warping = true;
	        robot.mouseMove(centreScreenX, centreScreenY);
	    } else {
	        component.setCursor(defaultCursor);
	    }
	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		mouseMoved(e);
	}
/*	
	@Override
    public void mouseMoved(MouseEvent e){
		int x = e.getX();
		int y = e.getY();
		if (!detail.hasOld) {
			detail.hasOld = true;
			detail.oldx = x;
			detail.oldy = y;
		} else {
			detail.oldx = detail.x;
			detail.oldy = detail.y;
		}
		detail.x = x;
		detail.y = y;
		detail.consumed = false;
		consumer.consume(detail);
	}
*/
	@Override
	public void mouseMoved(MouseEvent e) {
	    if (!mouseCaptured) return;

	    int mx = e.getXOnScreen();
	    int my = e.getYOnScreen();

	    // If we just warped, ignore events until we observe the mouse at the centre.
	    if (warping) {
	        if (Math.abs(mx - centreScreenX) <= 1 && Math.abs(my - centreScreenY) <= 1) {
	            warping = false; // warp complete
	        }
	        return;
	    }

	    int dx = mx - centreScreenX;
	    int dy = my - centreScreenY;

	    // If we're already at centre, nothing to do.
	    if (dx == 0 && dy == 0) return;

	    // Consume real delta
	    detail.dx = dx;detail.dy = dy;
	    consumer.consume(detail);

	    // Recenter (update centre first to be safe)
	    updateCentre(component);
	    warping = true;
	    robot.mouseMove(centreScreenX, centreScreenY);
	}
}
