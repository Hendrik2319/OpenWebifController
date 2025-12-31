package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.Vector;
import java.util.function.Consumer;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.MessageResponse;
import net.schwarzbaer.java.lib.gui.Canvas;
import net.schwarzbaer.java.lib.gui.ProgressView;
import net.schwarzbaer.java.lib.openwebif.RemoteControl;
import net.schwarzbaer.java.lib.openwebif.RemoteControl.Key;
import net.schwarzbaer.java.lib.openwebif.SystemInfo;

public class RemoteControlPanel extends Canvas implements MouseListener, MouseMotionListener, MouseWheelListener {
	private static final long serialVersionUID = 1313654798825197434L;
	
	private static final Color COLOR_KEY = new Color(0x80808080,true);
	private static final Color COLOR_HOVEREDKEY = Color.YELLOW;
	private static final String TEXT_NOKEY = "<none>";
	
	private final OpenWebifController main;
	private final Vector<KeyPressListener> keyPressListeners = new Vector<>();
	private RemoteControl remoteControl = null;
	private BufferedImage remoteControlImage = null;
	private RemoteControl.Key[] keys = null;
	private JScrollPane scrollPane = null;
	private RemoteControl.Key hoveredKey = null;
	private Point lastMousePoint = null;
	
	RemoteControlPanel(OpenWebifController main) {
		this.main = main;
		setToolTipText(TEXT_NOKEY);
		addMouseListener(this);
		addMouseMotionListener(this);
		//addMouseWheelListener(this);
	}
	
	public interface KeyPressListener {
		void keyWasPressed();
	}
	
	public void    addKeyPressListener(KeyPressListener kpl) { keyPressListeners.   add(kpl); }
	public void removeKeyPressListener(KeyPressListener kpl) { keyPressListeners.remove(kpl); }

	public JScrollPane createScrollPane() {
		scrollPane = new JScrollPane(this);
		//int incX = scrollPane.getHorizontalScrollBar().getUnitIncrement();
		//int incY = scrollPane.getVerticalScrollBar  ().getUnitIncrement();
		//System.out.printf("RemoteControlPanel.ScrollPane: UnitIncrement = (%d,%d)%n", incX, incY);
		scrollPane.getHorizontalScrollBar().setUnitIncrement(5);
		scrollPane.getVerticalScrollBar  ().setUnitIncrement(5);
		scrollPane.setBorder(null);
		scrollPane.addMouseWheelListener(this);
		return scrollPane;
	}

	private void printMouseEvent(MouseEvent e, String eventType) {
		//System.out.printf("M(%10s,%d,%d) is %sconsumed%n", eventType.trim(), e.getX(), e.getY(), e.isConsumed() ? "" : "NOT ");
	}

	@Override public void mouseDragged   (MouseEvent      e) { updateHoveredKey(e.getPoint()); repaint(); printMouseEvent(e,"Dragged   "); }
	@Override public void mouseMoved     (MouseEvent      e) { updateHoveredKey(e.getPoint()); repaint(); printMouseEvent(e,"Moved     "); }
	@Override public void mouseClicked   (MouseEvent      e) { updateHoveredKey(e.getPoint()); repaint(); printMouseEvent(e,"Clicked   "); clickKey(); }
	@Override public void mousePressed   (MouseEvent      e) { updateHoveredKey(e.getPoint()); repaint(); printMouseEvent(e,"Pressed   "); }
	@Override public void mouseReleased  (MouseEvent      e) { updateHoveredKey(e.getPoint()); repaint(); printMouseEvent(e,"Released  "); }
	@Override public void mouseEntered   (MouseEvent      e) { updateHoveredKey(e.getPoint()); repaint(); printMouseEvent(e,"Entered   "); }
	@Override public void mouseExited    (MouseEvent      e) { hoveredKey = null;              repaint(); printMouseEvent(e,"Exited    "); }
//	@Override public void mouseWheelMoved(MouseWheelEvent e) { lastMousePoint=null; updateHoveredKey(e.getPoint()); repaint(); printMouseEvent(e,"WheelMoved"); }
	@Override public void mouseWheelMoved(MouseWheelEvent e) { lastMousePoint=null; hoveredKey = null;              repaint(); printMouseEvent(e,"WheelMoved"); }

	private void clickKey() {
		if (hoveredKey==null) return;
		String baseURL = main.getBaseURL();
		if (baseURL==null) return;
		MessageResponse response = remoteControl.sendKeyPress(baseURL,hoveredKey,null);
		main.showMessageResponse(response, "RemoteControl.sendKeyPress");
		for (KeyPressListener kpl:keyPressListeners)
			kpl.keyWasPressed();
	}

	@Override
	public String getToolTipText(MouseEvent event) {
		Point p = event.getPoint();
		//System.out.printf("getToolTipText( %d,%d )%n", p.x,p.y);
		updateHoveredKey(p);
		if (hoveredKey==null) return TEXT_NOKEY;
		return String.format("%s (%s)", hoveredKey.title, hoveredKey.keyCode);
	}

	private void updateHoveredKey(Point p) {
		
		if (lastMousePoint!=null && lastMousePoint.x==p.x && lastMousePoint.y==p.y)
			return;
		
		lastMousePoint = p;
		
		if (hoveredKey!=null && !isOverKey(p,hoveredKey))
			hoveredKey = null;
		
		if (hoveredKey==null && keys!=null) {
			for (RemoteControl.Key key : keys) {
				if (isOverKey(p,key)) {
					hoveredKey = key;
					//System.out.printf("Hovered Key: %s%n", hoveredKey);
					break;
				}
			}
		}
	}

	private boolean isOverKey(Point p, Key key) {
		if (p==null) return false;
		if (key==null) return false;
		if (key.shape==null) return false;
		
		switch (key.shape.type) {
		case Circle:
			return key.shape.center.distanceSq(p) <= key.shape.radius*key.shape.radius;
			
		case Rect:
			Point c1 = key.shape.corner1;
			Point c2 = key.shape.corner2;
			return p.x>=c1.x && p.y>=c1.y  &&  p.x<=c2.x && p.y<=c2.y;
		}
		
		return false;
	}

	@Override
	public Point getToolTipLocation(MouseEvent event) {
		Point p = event.getPoint();
		return new Point(p.x+16,p.y);
	}

	public void initialize(String baseURL, ProgressView pd, SystemInfo systemInfo) {
		Consumer<String> progressTaskFcn = taskTitle -> OWCTools.setIndeterminateProgressTask(pd, "Remote Control: "+taskTitle);
		remoteControl = systemInfo==null ? null : new RemoteControl(systemInfo);
		if (remoteControl!=null) {
			remoteControlImage = remoteControl.getRemoteControlImage(baseURL, progressTaskFcn);
			keys = remoteControl.getKeys(baseURL, progressTaskFcn);
		} else {
			remoteControlImage = null;
			keys = null;
		}
		
		if (remoteControlImage != null) {
			int width = remoteControlImage.getWidth();
			int height = remoteControlImage.getHeight();
			SwingUtilities.invokeLater(()->{
				setPreferredSize(width, height);
				if (scrollPane!=null) {
					scrollPane.setPreferredSize(new Dimension(width+20, height));
					scrollPane.revalidate();
				}
			});
		}
	}
	
	@Override
	protected void paintCanvas(Graphics g, int x, int y, int width, int height) {
		configureGraphics(g);
		// g.setClip(x, y, width, height);
		paintImage(g, x, y);
		paintKeys(g, x, y);
	}

	private void configureGraphics(Graphics g) {
		if (g instanceof Graphics2D) {
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		}
	}

	private void paintKeys(Graphics g, int x0, int y0) {
		if (keys!=null) {
			g.setColor(COLOR_KEY);
			for (RemoteControl.Key key : keys)
				if (key!=hoveredKey)
					paintKey(g, x0, y0, key);
			if (hoveredKey!=null) {
				g.setColor(COLOR_HOVEREDKEY);
				paintKey(g, x0, y0, hoveredKey);
			}
		}
	}

	private void paintKey(Graphics g, int x0, int y0, RemoteControl.Key key) {
		if (key.shape==null) return;
		
		switch (key.shape.type) {
		
		case Circle:
			Point c = key.shape.center;
			int r = key.shape.radius;
			g.drawOval(x0+c.x-r, y0+c.y-r, 2*r, 2*r);
			break;
			
		case Rect:
			Point c1 = key.shape.corner1;
			Point c2 = key.shape.corner2;
			g.drawRect(x0+c1.x, y0+c1.y, c2.x-c1.x, c2.y-c1.y);
			break;
		}
	}
	
	private void paintImage(Graphics g, int x, int y) {
		if (remoteControlImage!=null)
			g.drawImage(remoteControlImage, x, y, null);
	}

	@SuppressWarnings("unused")
	private void paintTestRects(Graphics g) {
		int width = getWidth();
		int height = getHeight();
		for (int i=0; i<4; i++) {
			g.setColor(Color.GREEN);
			g.drawRect(i*2,i*2,width-i*4,height-i*4);
			g.setColor(Color.RED);
			g.drawRect(i*2+1,i*2+1,width-i*4-2,height-i*4-2);
		}
	}

}
