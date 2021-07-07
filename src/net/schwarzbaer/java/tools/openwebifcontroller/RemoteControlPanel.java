package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
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

	public JScrollPane createScrollPane() {
		return scrollPane = new JScrollPane(this);
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
				//setSize(width, height);
				setPreferredSize(width, height);
				if (scrollPane!=null) {
					//scrollPane.setPreferredSize(new Dimension(width, height));
					scrollPane.revalidate();
				}
			});
		}
	}

	@Override
	protected void paintCanvas(Graphics g, int x, int y, int width, int height) {
		if (g instanceof Graphics2D) {
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		}
		
		// g.setClip(x, y, width, height);
		
		if (remoteControlImage!=null)
			g.drawImage(remoteControlImage, x, y, null);
		
		if (keys!=null) {
			g.setColor(Color.MAGENTA);
			for (RemoteControl.Key key : keys) {
				if (key.shape==null) continue;
				switch (key.shape.type) {
				
				case Circle:
					Point c = key.shape.center;
					int r = key.shape.radius;
					g.drawOval(c.x-r, c.y-r, 2*r, 2*r);
					break;
					
				case Rect:
					Point c1_ = key.shape.corner1;
					Point c2_ = key.shape.corner2;
					Point c1 = new Point(Math.min(c1_.x,c2_.x), Math.min(c1_.y,c2_.y));
					Point c2 = new Point(Math.max(c1_.x,c2_.x), Math.max(c1_.y,c2_.y));
					g.drawRect(c1.x, c1.y, c2.x-c1.x, c2.y-c1.y);
					break;
				}
			}
		}
	}

}
