package me.kaigermany.opendiskdiver.gui;

import java.io.File;
import java.io.IOException;

import me.kaigermany.opendiskdiver.data.CopyActivity;
import me.kaigermany.opendiskdiver.reader.ReadableSource;

public interface UI {
	int cooseFromList(String title, String[] entries);

	File saveAs();

	ReadableSource cooseSource() throws IOException;
	
	void close();
	
	CopyActivity getCopyDiskActivityHandler();
}
