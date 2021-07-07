package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import net.schwarzbaer.gui.Canvas;
import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.java.lib.openwebif.RemoteControl;
import net.schwarzbaer.java.lib.openwebif.SystemInfo;

public class RemoteControlPanel extends Canvas {
	private static final long serialVersionUID = 1313654798825197434L;
	
	private RemoteControl remoteControl = null;
	private BufferedImage remoteControlImage = null;
	private RemoteControl.Key[] keys = null;
	private JScrollPane scrollPane = null;
	
	RemoteControlPanel() {
		setToolTipText("RemoteControl");
	}

	@Override
	public String getToolTipText(MouseEvent event) {
		// TODO Auto-generated method stub
		return super.getToolTipText(event);
	}

	@Override
	public Point getToolTipLocation(MouseEvent event) {
		// TODO Auto-generated method stub
		return event.getPoint();
	}

	public JScrollPane createScrollPane() {
		scrollPane = new JScrollPane(this);
		//int incX = scrollPane.getHorizontalScrollBar().getUnitIncrement();
		//int incY = scrollPane.getVerticalScrollBar  ().getUnitIncrement();
		//System.out.printf("RemoteControlPanel.ScrollPane: UnitIncrement = (%d,%d)%n", incX, incY);
		scrollPane.getHorizontalScrollBar().setUnitIncrement(5);
		scrollPane.getVerticalScrollBar  ().setUnitIncrement(5);
		scrollPane.setBorder(null);
		return scrollPane;
	}

	public void initialize(String baseURL, ProgressDialog pd, SystemInfo systemInfo) {
		Consumer<String> progressTaskFcn = taskTitle -> OpenWebifController.setIndeterminateProgressTask(pd, "Remote Control: "+taskTitle);;
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

	private void paintKeys(Graphics g, int x, int y) {
		if (keys!=null) {
			g.setColor(Color.MAGENTA);
			for (RemoteControl.Key key : keys) {
				if (key.shape==null) continue;
				switch (key.shape.type) {
				
				case Circle:
					Point c = key.shape.center;
					int r = key.shape.radius;
					g.drawOval(x+c.x-r, y+c.y-r, 2*r, 2*r);
					break;
					
				case Rect:
					Point c1_ = key.shape.corner1;
					Point c2_ = key.shape.corner2;
					Point c1 = new Point(Math.min(c1_.x,c2_.x), Math.min(c1_.y,c2_.y));
					Point c2 = new Point(Math.max(c1_.x,c2_.x), Math.max(c1_.y,c2_.y));
					g.drawRect(x+c1.x, y+c1.y, c2.x-c1.x, c2.y-c1.y);
					break;
				}
			}
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
