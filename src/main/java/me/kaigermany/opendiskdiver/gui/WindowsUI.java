package me.kaigermany.opendiskdiver.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.DriveListProvider;
import me.kaigermany.opendiskdiver.data.DriveInfo;
import me.kaigermany.opendiskdiver.reader.ImageFileReader;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.reader.ZipFileReader;
import me.kaigermany.opendiskdiver.utils.OpenFileDialog;
import me.kaigermany.opendiskdiver.utils.Utils;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface.Pair;

public class WindowsUI implements UI {
	public static final String[] pseudoSources = new String[]{
			"Select RAW disk image (.img)",
			"Select block-compressed disk image (.zip)"
	};
	
	private static ConsoleInterface ci = new ConsoleInterface();
	private static Screen screen = new Screen(1, 1, ci);

	@Override
	public int cooseFromList(String title, String[] entries) {
		int maxWidth = title.length();
		for(String s : entries) maxWidth = Math.max(maxWidth, s.length());
		screen.resize(maxWidth + 4, entries.length + 1);
		int selectedSlot = 0;
		while (true) {
			screen.write(title, 0, 0, Screen.WHITE, Screen.BLACK);
			for(int i=0; i<entries.length; i++){
				boolean selected = selectedSlot == i;
				String foregroundColor = selected ? Screen.BLACK : Screen.WHITE;
				String backgroundColor = selected ? Screen.WHITE : Screen.BLACK;
				
				screen.write(String.valueOf(i + 1), 0, i + 1, foregroundColor, backgroundColor);
				screen.write(entries[i], 4, i + 1, foregroundColor, backgroundColor);
			}
			
			screen.printText();
			
			Pair<Integer, String> key = ci.readKey();
			System.out.println(key);
			if(key.getFirst() == 13) {
				return selectedSlot;//enter
			}
			if(entries.length > 1) {
				String keyName = key.getSecond();
				if(keyName.equals("RightArrow") || keyName.equals("DownArrow")) {
					selectedSlot = (selectedSlot + 1) % entries.length;
				}
				if(keyName.equals("LeftArrow") || keyName.equals("UpArrow")) {
					selectedSlot = (selectedSlot + entries.length - 1) % entries.length;
				}
			}
		}
	}

	@Override
	public ReadableSource cooseSource() throws IOException {
		ArrayList<DriveInfo> drives = DriveListProvider.listDrives();
		
		System.out.println(drives.toString().replace("}, {", "},\n{"));
		final int numSlots = drives.size() + pseudoSources.length;
		int maxWidth = 0;
		for(DriveInfo di : drives) maxWidth = Math.max(maxWidth, di.name.length());
		for(String s : pseudoSources) maxWidth = Math.max(maxWidth, s.length());
		screen.resize(maxWidth + 12, numSlots);
		int selectedSlot = 0;
		while (true) {
			for(int i=0; i<drives.size(); i++){
				boolean selected = selectedSlot == i;
				String foregroundColor = selected ? Screen.BLACK : Screen.WHITE;
				String backgroundColor = selected ? Screen.WHITE : Screen.BLACK;
				
				DriveInfo di = drives.get(i);
				
				screen.write(String.valueOf(i), 0, i, foregroundColor, backgroundColor);
				screen.write(Utils.toHumanReadableFileSize(di.size), 4, i, foregroundColor, backgroundColor);
				screen.write(di.name, 12, i, foregroundColor, backgroundColor);
			}
			for(int i=0; i<pseudoSources.length; i++){
				int linePos = drives.size() + i;
				boolean selected = selectedSlot == linePos;
				String foregroundColor = selected ? Screen.BLACK : Screen.WHITE;
				String backgroundColor = selected ? Screen.WHITE : Screen.BLACK;
				screen.write(String.valueOf(linePos), 0, linePos, foregroundColor, backgroundColor);
				//screen.write(toHumanReadableFileSize(di.size), 4, i, foregroundColor, backgroundColor);
				screen.write(pseudoSources[i], 12, linePos, foregroundColor, backgroundColor);
			}
			screen.printText();
			
			Pair<Integer, String> key = ci.readKey();
			System.out.println(key);
			if(key.getFirst() == 13) break;//enter
			if(!drives.isEmpty()) {
				String keyName = key.getSecond();
				if(keyName.equals("RightArrow") || keyName.equals("DownArrow")) {
					selectedSlot = (selectedSlot + 1) % numSlots;
				}
				if(keyName.equals("LeftArrow") || keyName.equals("UpArrow")) {
					selectedSlot = (selectedSlot + numSlots - 1) % numSlots;
				}
			}
		}
		
		if(drives.isEmpty()){
			return null;//should be impossible.
		}
		if(selectedSlot < drives.size()){
			return drives.get(selectedSlot).openReader();
		}
		selectedSlot -= drives.size();
		
		switch(selectedSlot){
			case 0: return new ImageFileReader(OpenFileDialog.userOpenFile(pseudoSources[0], null));
			case 1: return new ZipFileReader(OpenFileDialog.userOpenFile(pseudoSources[1], null));
		}
		
		return null;//impossible to reach, but the compiler want to see it here :)
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
