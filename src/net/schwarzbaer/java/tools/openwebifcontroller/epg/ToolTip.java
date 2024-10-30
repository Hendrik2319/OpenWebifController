package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.epg.EPGView.EPGViewEvent;

public abstract class ToolTip {
	private static final Color COLOR_TOOLTIP_BG    = new Color(0xFFFFD0);
	private static final Color COLOR_TOOLTIP_FRAME = Color.BLACK;
	private static final Color COLOR_TOOLTIP_TEXT  = Color.BLACK;
	
	private BufferedImage image;
	private Point pos;
	private final ScheduledExecutorService activator;
	private ScheduledFuture<?> activatorTaskHandle;
	private boolean isActive;
	
	ToolTip() {
		image = null;
		pos = null;
		activator = Executors.newSingleThreadScheduledExecutor();
		activatorTaskHandle = null;
		isActive = false;
	}
	
	protected abstract void repaintView();

	public synchronized void activate() {
		if (activatorTaskHandle!=null) return;
		activatorTaskHandle = activator.schedule(()->{
			synchronized (ToolTip.this) {
				isActive = true;
				activatorTaskHandle = null;
			}
			repaintView();
		}, 2, TimeUnit.SECONDS);
	}

	public synchronized void deactivate() {
		isActive = false;
		if (activatorTaskHandle==null) return;
		activatorTaskHandle.cancel(false);
		activatorTaskHandle = null;
	}

	public synchronized void hide() {
		image=null;
	}

	public synchronized boolean updatePosition(Point point) {
		boolean posChanged = pos==null || !pos.equals(point);
		pos = new Point(point);
		return image!=null && posChanged;
	}

	public void updateContent(Point point, EPGViewEvent event) {
		BufferedImage newImage = event==null ? null : createToolTip(event);
		synchronized (this) {
			image = newImage;
			pos = new Point(point);
		}
	}

	private BufferedImage createToolTip(EPGViewEvent event) {
		Graphics2D g2;
		BufferedImage image;
		Font stdFont, boldFont;
		
		image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		g2 = image.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
		stdFont = g2.getFont();
		boldFont = stdFont.deriveFont(Font.BOLD);
		
		String begin = event.event.begin;
		String end   = event.event.end;
		if (begin==null && event.event.begin_timestamp!=null)
			begin = OpenWebifController.dateTimeFormatter.getTimeStr( event.event.begin_timestamp                          *1000, false, false, false, true, false);
		if (end  ==null && event.event.begin_timestamp!=null && event.event.duration_sec!=null)
			end   = OpenWebifController.dateTimeFormatter.getTimeStr((event.event.begin_timestamp+event.event.duration_sec)*1000, false, false, false, true, false);
		String timeRange = String.format("%s - %s", begin, end);
		String title     = event.event.title;
		String shortdesc = event.event.shortdesc;
		if (shortdesc!=null && (shortdesc.isEmpty() || shortdesc.equals(title)))
			shortdesc = null;
		String[] shortdescStrs = splitLines(shortdesc);
		
		float  stdFontSize =  stdFont.getSize()*1.2f;
		float boldFontSize = boldFont.getSize()*1.2f;
		
		FontRenderContext frc = g2.getFontRenderContext();
		Rectangle2D timeRangeBounds =  stdFont.getStringBounds(timeRange, frc);
		Rectangle2D titleBounds     = boldFont.getStringBounds(title, frc);
		double shortdescWidth = 0;
		for (String str:shortdescStrs) {
			Rectangle2D bounds = stdFont.getStringBounds(str, frc);
			shortdescWidth = Math.max(shortdescWidth, bounds.getWidth());
		}
		
		int borderSpacing = 5;
		int imgWidth  = 2*borderSpacing + (int) Math.ceil( Math.max( Math.max( timeRangeBounds.getWidth(), titleBounds.getWidth() ), shortdescWidth ) );
		int imgHeight = 2*borderSpacing + (int) Math.ceil( stdFontSize + boldFontSize + shortdescStrs.length*stdFontSize );
		int[] baselineOffset = new int[2+shortdescStrs.length];
		baselineOffset[0] = borderSpacing + Math.round( stdFontSize*0.75f );
		baselineOffset[1] = borderSpacing + Math.round( stdFontSize + boldFontSize*0.75f);
		for (int i=0; i<shortdescStrs.length; i++)
			baselineOffset[i+2] = borderSpacing + Math.round(  stdFontSize + boldFontSize + stdFontSize*i + stdFontSize*0.75f);
		
		image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
		g2 = image.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
		stdFont = g2.getFont();
		boldFont = stdFont.deriveFont(Font.BOLD);
		
		g2.setColor(COLOR_TOOLTIP_BG);
		g2.fillRect(0, 0, imgWidth, imgHeight);
		g2.setColor(COLOR_TOOLTIP_FRAME);
		g2.drawRect(0, 0, imgWidth-1, imgHeight-1);
		//g2.drawString("ToolTip Dummy", 10, 20);
		
		g2.setColor(COLOR_TOOLTIP_TEXT);
		g2.setFont(stdFont);
		g2.drawString(timeRange, borderSpacing, baselineOffset[0]);
		g2.setFont(boldFont);
		g2.drawString(title, borderSpacing, baselineOffset[1]);
		if (shortdescStrs.length>0) {
			g2.setFont(stdFont);
			for (int i=0; i<shortdescStrs.length; i++)
				g2.drawString(shortdescStrs[i], borderSpacing, baselineOffset[2+i]);
		}
		
		return image;
	}

	private String[] splitLines(String str) {
		if (str==null) return new String[0];
		Vector<String> lines = new Vector<>();
		String line;
		try (BufferedReader reader = new BufferedReader( new StringReader(str) )) {
			while ( (line=reader.readLine())!=null ) lines.add(line);
		} catch (IOException e) { e.printStackTrace(); }
		return lines.toArray(new String[lines.size()]);
	}

	public void paint(final Graphics2D g2, final int x, final int y, final int width, final int height) {
		BufferedImage image;
		Point pos;
		boolean isActive;
		
		synchronized (this) {
			image = this.image;
			pos = this.pos;
			isActive = this.isActive;
		}
		
		if (image!=null && pos!=null && isActive) {
			int toolTipWidth  = image.getWidth();
			int toolTipHeight = image.getHeight();
			int distToPosX = 15;
			int distToPosY = 10;
			int distToBorder = 10;
			int imgX;
			int imgY;
			
			if (pos.x+distToPosX+toolTipWidth+distToBorder < x+width)
				imgX = pos.x+distToPosX; // right of pos
			else if (x < pos.x-distToPosX-toolTipWidth-distToBorder)
				imgX = pos.x-distToPosX-toolTipWidth; // left of pos
			else if (width < toolTipWidth)
				imgX = x;
			else if (width < toolTipWidth+2*distToBorder)
				imgX = (width-toolTipWidth)/2; // centered
			else if (pos.x < x+width/2)
				imgX = x+width - distToBorder - toolTipWidth; // on right border
			else
				imgX = x+distToBorder; // on left border
			
			if (pos.y+distToPosY+toolTipHeight+distToBorder < y+height)
				imgY = pos.y+distToPosY; // right of pos
			else if (y < pos.y-distToPosY-toolTipHeight-distToBorder)
				imgY = pos.y-distToPosY-toolTipHeight; // left of pos
			else if (height < toolTipHeight )
				imgY = y;
			else if (height < toolTipHeight+2*distToBorder)
				imgY = (height-toolTipHeight)/2; // centered
			else if (pos.y < y+height/2)
				imgY = y+height - distToBorder - toolTipHeight; // on right border
			else
				imgY = y+distToBorder; // on left border
			
			g2.drawImage(image, imgX, imgY, null);
		}
	}
}