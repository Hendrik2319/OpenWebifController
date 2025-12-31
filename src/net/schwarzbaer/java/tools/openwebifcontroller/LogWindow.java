package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;

import net.schwarzbaer.java.lib.gui.ScrollPosition;
import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.lib.gui.StyledDocumentInterface;
import net.schwarzbaer.java.lib.gui.StyledDocumentInterface.Style;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.MessageResponse;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.LogWindowInterface;

public class LogWindow extends StandardDialog implements LogWindowInterface
{
	private static final long serialVersionUID = -9060941148738931230L;
	
	private static final String STR_NO_RESPONSE = "<No Response>";
	private static final Style STYLE_SUCCESS    = new Style(new Color(0x00BB00), true, false, Style.MONOSPACED);
	private static final Style STYLE_FAILURE    = new Style(Color.RED          , true, false, Style.MONOSPACED);
	private static final Style STYLE_NORESPONSE = new Style(Color.GRAY         , true, false, Style.MONOSPACED);
	private static final Style STYLE_TITLE   = new Style(new Color(0x00ABF3));
	private static final Style STYLE_MESSAGE = null;
	private static final Style STYLE_MESSAGE_HIGHLIGHTED = new Style(new Color(0x0090AD), true, false);
	
	private final StyledDocumentInterface sdi;
	private final JScrollPane contentPane;

	public LogWindow(Window parent, String title)
	{
		super(parent, title, ModalityType.MODELESS, true);
		
		DefaultStyledDocument doc = new DefaultStyledDocument();
		sdi = new StyledDocumentInterface(doc, "LogWindow", null, 12);
		JTextPane textPane = new JTextPane(doc);
		
		contentPane = new JScrollPane(textPane);
		contentPane.setPreferredSize(new Dimension(600, 600));
		
		createGUI(contentPane, OWCTools.createButton("Close", true, e->closeDialog()));
	}
	
	@Override
	public void showMessageResponse(MessageResponse response, String title, String... stringsToHighlight)
	{
		printToDoc(response, title, stringsToHighlight);
		printToSystemOut(response, title);
		
		if (response!=null && !response.result)
		{
			String message = response==null ? STR_NO_RESPONSE : toString(response);
			JOptionPane.showMessageDialog(getParent(), message, title, JOptionPane.WARNING_MESSAGE);
		}
	}
	
	private record FoundStr(int pos, String str)
	{
		static FoundStr getFindFirst(String message, String[] strs)
		{
			int firstPos = message.length();
			String firstStr = null;
			for (String str : strs)
			{
				int pos = message.indexOf(str);
				if (pos>=0 && pos<firstPos)
				{
					firstPos = pos;
					firstStr = str;
				}
			}
			
			if (firstStr == null) return null;
			return new FoundStr(firstPos, firstStr);
		}
	}

	private void printToDoc(MessageResponse response, String title, String[] stringsToHighlight)
	{
		ScrollPosition scrollPos = ScrollPosition.getVertical(contentPane);
		
		if (response==null)
			sdi.append(STYLE_NORESPONSE, "[-------] ");
		else if (response.result)
			sdi.append(STYLE_SUCCESS, "[SUCCESS] ");
		else
			sdi.append(STYLE_FAILURE, "[FAILURE] ");
		
		sdi.append(STYLE_TITLE, "%s: ", title);
		
		String message = response==null ? STR_NO_RESPONSE : response.message;
		
		FoundStr str;
		while ( (str = FoundStr.getFindFirst(message, stringsToHighlight))!=null )
		{
			sdi.append(STYLE_MESSAGE, "%s", message.substring(0, str.pos));
			sdi.append(STYLE_MESSAGE_HIGHLIGHTED, "%s", str.str);
			message = message.substring(str.pos + str.str.length());
		}
		
		if (!message.isEmpty())
			sdi.append(STYLE_MESSAGE, "%s", message);
		sdi.append("%n");
		
		if (scrollPos!=null)
			SwingUtilities.invokeLater(()->{
				scrollPos.setVertical(contentPane);
			});
	}

	private static void printToSystemOut(MessageResponse response, String title)
	{
		System.out.println(title+":");
		if (response!=null)
			response.printTo(System.out, "   ");
		else
			System.out.println(STR_NO_RESPONSE);
	}

	private static String toString(MessageResponse values) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Message: \"%s\"%n", values.message));
		sb.append(String.format("Result: %s%n", values.result));
		return sb.toString();
	}
}
