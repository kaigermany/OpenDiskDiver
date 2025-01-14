package me.kaigermany.opendiskdiver.gui;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public interface UI {
	//returns a number between 0 .. (entries.length - 1) in any case.
	int cooseFromList(String title, String[] entries);
	
	//user has to enter an output file, File class will be returned.
	File saveAs();
	
	//let the user select a mounted drive.
	ReadableSource cooseSource() throws IOException;
	
	//closes the gui instance.
	void close();
	
	//system can send status updates to GUI over this way.
	void onDiskCopyStateUpdate(DiskCopyState state);
	
	//prints info text to user and should await acknowledge signal before return.
	void showInfo(String[] text);
	
	//allows GUI-specific implementations of a sector-inspector.
	//void sectorInspector(ReadableSource source);
	
	//allows the GUI to order specific pages within a given range.
	void pagedTextViewer(long numPages, Function<Long, String[]> textRequestCallback);
}
