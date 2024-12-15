package me.kaigermany.opendiskdiver.windows;

import java.io.IOException;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.data.DriveInfo;
import me.kaigermany.opendiskdiver.gui.Screen;
import me.kaigermany.opendiskdiver.reader.ImageFileReader;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.reader.ZipFileReader;
import me.kaigermany.opendiskdiver.utils.OpenFileDialog;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface.Pair;

public class SelectDriveGui {
	public static final String[] pseudoSources = new String[]{
			"Select RAW disk image (.img)",
			"Select block-compressed disk image (.zip)"
	};
	
	private Screen screen;
	private ConsoleInterface console;
	private int selectedSlot = 0;
	private int numSlots;
	
	public SelectDriveGui(Screen screen, ConsoleInterface console) {
		this.screen = screen;
		this.console = console;
		
	}

	public ReadableSource selectDiskSource() throws IOException {
		ArrayList<DriveInfo> drives = WindowsDrives.listDrives();
		
		System.out.println(drives.toString().replace("}, {", "},\n{"));
		numSlots = drives.size() + pseudoSources.length;
		int maxWidth = 0;
		for(DriveInfo di : drives) maxWidth = Math.max(maxWidth, di.name.length());
		for(String s : pseudoSources) maxWidth = Math.max(maxWidth, s.length());
		screen.resize(maxWidth + 12, numSlots);
		
		while (true) {
			drawGui(drives);
			Pair<Integer, String> key = console.readKey();
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
	
	private void drawGui(ArrayList<DriveInfo> drives){
		for(int i=0; i<drives.size(); i++){
			boolean selected = selectedSlot == i;
			String foregroundColor = selected ? Screen.BLACK : Screen.WHITE;
			String backgroundColor = selected ? Screen.WHITE : Screen.BLACK;
			
			DriveInfo di = drives.get(i);
			
			screen.write(String.valueOf(i), 0, i, foregroundColor, backgroundColor);
			screen.write(toHumanReadableFileSize(di.size), 4, i, foregroundColor, backgroundColor);
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
	}
	
	public int selectListEntry(String title, String[] list){
		int maxWidth = title.length();
		for(String s : list) maxWidth = Math.max(maxWidth, s.length());
		screen.resize(maxWidth + 4, list.length + 1);
		int selectedSlot = 0;
		while (true) {
			screen.write(title, 0, 0, Screen.WHITE, Screen.BLACK);
			for(int i=0; i<list.length; i++){
				boolean selected = selectedSlot == i;
				String foregroundColor = selected ? Screen.BLACK : Screen.WHITE;
				String backgroundColor = selected ? Screen.WHITE : Screen.BLACK;
				
				screen.write(String.valueOf(i + 1), 0, i + 1, foregroundColor, backgroundColor);
				screen.write(list[i], 4, i + 1, foregroundColor, backgroundColor);
			}
			
			screen.printText();
			
			Pair<Integer, String> key = console.readKey();
			System.out.println(key);
			if(key.getFirst() == 13) {
				return selectedSlot;//enter
			}
			if(list.length > 1) {
				String keyName = key.getSecond();
				if(keyName.equals("RightArrow") || keyName.equals("DownArrow")) {
					selectedSlot = (selectedSlot + 1) % list.length;
				}
				if(keyName.equals("LeftArrow") || keyName.equals("UpArrow")) {
					selectedSlot = (selectedSlot + list.length - 1) % list.length;
				}
			}
		}
	}
	
	public static String toHumanReadableFileSize(long bytes){
		if(bytes < 1024){
			return bytes + " B";
		}
		String[] sizeUnits = new String[] {"B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB", "BB"};

        int steps = 0;
        long beforeComma = bytes;
        long afterComma = bytes * 1000;
        
        while(beforeComma / 1000 > 0) {
            beforeComma /= 1024;
            afterComma /= 1024;
            steps++;
        }
        
        afterComma = (afterComma - beforeComma * 1000);
        
        try{
	        char[] a = new char[7];
	        int wp = 0;
	        a[4] = (char)(beforeComma / 100);
	        a[5] = (char)((beforeComma / 10) % 10);
	        a[6] = (char)(beforeComma % 10);
	        int rp = 4;
	        if(a[rp] == 0) {
	        	rp++;
	            if(a[rp] == 0) rp++;
	        }
	        for(int i=rp; i<7; i++){
	        	a[wp++] = (char)('0' + a[i]);
	        }
	        rp -= 4;
	        rp = 3 - rp;
	        if(rp != 3){
	        	a[wp++] = ',';
		        for(int i=rp; i<3; i++){
		        	a[wp++] = (char)('0' + (char)(afterComma / 100) % 10);
		        	afterComma *= 10;
		        }
	        }
        	a[wp++] = ' ';
	        String text = sizeUnits[steps];
	        for(int i=0; i<text.length(); i++) {
	        	a[wp++] = text.charAt(i);
	        }
	        return new String(a, 0, wp);
        }catch(Exception e){
        	e.printStackTrace();
        	return "";
        }
	}
	
}
