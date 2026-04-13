package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Vector;

public final class ListenerController
{
	public interface ListenerUser<ListenerType>
	{
		void    addListener(ListenerType listener);
		void removeListener(ListenerType listener);
	}
	
	private final Vector<Runnable> removeTasks;
	
	private ListenerController()
	{
		removeTasks = new Vector<>();
	}
	
	public static ListenerController createFor(Window window)
	{
		ListenerController controller = new ListenerController();
		window.addWindowListener(new WindowAdapter() {
			@Override public void windowClosed(WindowEvent e)
			{
				controller.removeTasks.forEach(Runnable::run);
				controller.removeTasks.clear();
			}
		});
		return controller;
	}
	
	public <ListenerType> void addListener(ListenerUser<ListenerType> user, ListenerType listener)
	{
		user.addListener(listener);
		removeTasks.add(() -> user.removeListener(listener));
	}
}
