package me.kaigermany.opendiskdiver;

import me.kaigermany.opendiskdiver.data.DriveInfo;
import me.kaigermany.opendiskdiver.gui.Screen;
import me.kaigermany.opendiskdiver.windows.SelectDriveGui;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface;

public class Main {
	private static ConsoleInterface ci;
	
	public static void main(String[] args) {
		ci = new ConsoleInterface();
		Screen screen = new Screen(1, 1, ci);
		DriveInfo drive = new SelectDriveGui(screen, ci).awaitSelection();
		
	}

}
