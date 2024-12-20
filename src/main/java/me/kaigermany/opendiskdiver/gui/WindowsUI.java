package me.kaigermany.opendiskdiver.gui;

import java.io.File;
import java.io.IOException;

import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.OpenFileDialog;
import me.kaigermany.opendiskdiver.windows.SelectDriveGui;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface;

public class WindowsUI implements UI {
	private static ConsoleInterface ci = new ConsoleInterface();
	private static Screen screen = new Screen(1, 1, ci);

	@Override
	public int cooseFromList(String title, String[] entries) {
		return new SelectDriveGui(screen, ci).selectListEntry(title, entries);
	}

	@Override
	public ReadableSource cooseSource() throws IOException {
		return new SelectDriveGui(screen, ci).selectDiskSource();
	}

	@Override
	public void close() {
		screen.close();
	}

	@Override
	public File saveAs() {
		File defaultSelection = new File(".").getAbsoluteFile();
		return OpenFileDialog.userSaveFile("Save as", defaultSelection);
	}
}
