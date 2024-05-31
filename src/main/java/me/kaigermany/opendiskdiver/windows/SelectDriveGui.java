package me.kaigermany.opendiskdiver.windows;

import java.util.ArrayList;

import me.kaigermany.opendiskdiver.data.DriveInfo;
import me.kaigermany.opendiskdiver.gui.Screen;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface.Pair;

public class SelectDriveGui {
	private Screen screen;
	private ArrayList<DriveInfo> drives;
	private ConsoleInterface console;
	private int selectedSlot = 0;
	
	public SelectDriveGui(Screen screen, ConsoleInterface console) {
		this.screen = screen;
		this.console = console;
		this.drives = WindowsDrives.listDrives();
		System.out.println(drives);
		screen.resize(50, drives.size());
	}

	public DriveInfo awaitSelection() {
		while (true) {
			drawGui();
			Pair<Integer, String> key = console.readKey();
			System.out.println(key);
			if(key.getFirst() == 13) break;//enter
			if(!drives.isEmpty()) {
				String keyName = key.getSecond();
				if(keyName.equals("RightArrow") || keyName.equals("DownArrow")) {
					selectedSlot = (selectedSlot + 1) % drives.size();
				}
				if(keyName.equals("LeftArrow") || keyName.equals("UpArrow")) {
					selectedSlot = (selectedSlot + drives.size() - 1) % drives.size();
				}
			}
			
		}
		if(drives.isEmpty()){
			return null;
		}
		return drives.get(selectedSlot);
	}
	
	private void drawGui(){
		for(int i=0; i<drives.size(); i++){
			boolean selected = selectedSlot == i;
			String foregroundColor = selected ? Screen.BLACK : Screen.WHITE;
			String backgroundColor = selected ? Screen.WHITE : Screen.BLACK;
			
			DriveInfo di = drives.get(i);
			
			screen.write(String.valueOf(i), 0, i, foregroundColor, backgroundColor);
			screen.write(toHumanReadableFileSize(di.size), 4, i, foregroundColor, backgroundColor);
			screen.write(di.name, 12, i, foregroundColor, backgroundColor);
		}
		screen.printText();
	}
	
	
	

	public static String toHumanReadableFileSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		String[] sizeUnits = new String[] {"B", "KB", "MB", "GB", "TB", "PB", "EB"};
		/*
		String[] units = {
            "byte",
            "kb",
            "mb",
            "gb",
            "tb",
            "pb",
            "eb",
            "zb",
            "yb",
            "bb"
        };
	    */
		int steps = 0;
		long beforeComma = bytes;
		long afterComma = bytes * 1000;
		// float check = bytes;
		
		//bugfix where 1072693248 got interpreted as 1023, but the size limitations forced it to ":23 MB"
		//instead of "1023 MB" but thats too long, so it must be "0.99 GB".
		while (beforeComma /* / 1024 > 0*/ >= 1000) {
			beforeComma /= 1024;
			afterComma /= 1024;
			steps++;
		}

		afterComma = (afterComma - beforeComma * 1000);
		try {
			char[] a = new char[7];
			int wp = 0;
			a[4] = (char) (beforeComma / 100);
			a[5] = (char) ((beforeComma / 10) % 10);
			a[6] = (char) (beforeComma % 10);
			int rp = 4;
			if (a[rp] == 0) {
				rp++;
				if (a[rp] == 0) rp++;
			}
			for (int i = rp; i < 7; i++) {
				a[wp++] = (char) ('0' + a[i]);
			}
			rp -= 4;
			rp = 3 - rp;
			if (rp != 3) {
				a[wp++] = ',';
				for (int i = rp; i < 3; i++) {
					a[wp++] = (char) ('0' + (char) (afterComma / 100) % 10);
					afterComma *= 10;
				}
			}
			a[wp++] = ' ';
			String text = sizeUnits[steps];
			for (int i = 0; i < text.length(); i++) {
				a[wp++] = text.charAt(i);
			}
			return new String(a, 0, wp);
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}
}
