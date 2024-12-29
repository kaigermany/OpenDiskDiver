package me.kaigermany.opendiskdiver.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.DriveListProvider;
import me.kaigermany.opendiskdiver.data.CopyActivity;
import me.kaigermany.opendiskdiver.data.DriveInfo;
import me.kaigermany.opendiskdiver.reader.ImageFileReader;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.reader.ZipFileReader;
import me.kaigermany.opendiskdiver.utils.OpenFileDialog;
import me.kaigermany.opendiskdiver.utils.SharedText;
import me.kaigermany.opendiskdiver.utils.Utils;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface.Pair;
import me.kaigermany.opendiskdiver.writer.Writer;

public class WindowsUI implements UI {
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
		final int numSlots = drives.size() + SharedText.pseudoSources.length;
		int maxWidth = 0;
		for(DriveInfo di : drives) maxWidth = Math.max(maxWidth, di.name.length());
		for(String s : SharedText.pseudoSources) maxWidth = Math.max(maxWidth, s.length());
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
			for(int i=0; i<SharedText.pseudoSources.length; i++){
				int linePos = drives.size() + i;
				boolean selected = selectedSlot == linePos;
				String foregroundColor = selected ? Screen.BLACK : Screen.WHITE;
				String backgroundColor = selected ? Screen.WHITE : Screen.BLACK;
				screen.write(String.valueOf(linePos), 0, linePos, foregroundColor, backgroundColor);
				//screen.write(toHumanReadableFileSize(di.size), 4, i, foregroundColor, backgroundColor);
				screen.write(SharedText.pseudoSources[i], 12, linePos, foregroundColor, backgroundColor);
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
			case 0: return new ImageFileReader(OpenFileDialog.userOpenFile(SharedText.pseudoSources[0], null));
			case 1: return new ZipFileReader(OpenFileDialog.userOpenFile(SharedText.pseudoSources[1], null));
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

	@Override
	public CopyActivity getCopyDiskActivityHandler() {
		return new CopyActivity(){
			private long lastTimeScreenUpdated = 0;
			
			@Override
			public void onCopy(ReadableSource reader, Writer writer) throws IOException {
				byte[] buf = new byte[1 << 20];
				int maxLen = buf.length / 512;
				long pos = 0;
				long maxPos = reader.numSectors();
				long badSectors = 0;
				
				while(pos < maxPos){
					int numSectorsToRead = (int)Math.min(maxPos - pos, maxLen);
					try{
						
						reader.readSectors(pos, numSectorsToRead, buf, 0);
						writer.write(buf, numSectorsToRead * 512);
						pos += numSectorsToRead;
						
					}catch(IOException blockReadError){
						//block read failed, try single sector read mode
						
						byte[] dummyBuffer = new byte[512];
						byte[] readBuffer = new byte[512];
						for(int offset=0; offset<numSectorsToRead; offset++){
							onUpdateScreen(pos, maxPos, badSectors);
							try{
								reader.readSectors(pos+offset, 1, readBuffer, 0);
								writer.write(readBuffer, 512);
							}catch(IOException sectorReadError){
								badSectors++;
								writer.write(dummyBuffer, 512);
							}
						}
						pos += numSectorsToRead;
						
					}
					onUpdateScreen(pos, maxPos, badSectors);
				}
			}
			
			private void onUpdateScreen(long pos, long maxPos, long badSectors){
				long time = System.currentTimeMillis();
				if(time > lastTimeScreenUpdated){
					lastTimeScreenUpdated = time + 100;
					onUpdateScreenImpl(pos, maxPos, badSectors, false);
					boolean isPaused = false;
					while(ci.hasNextKey()){
						if(ci.readKey().getSecond().toLowerCase().equals("p")){
							isPaused = true;
							break;
						}
					}
					if(isPaused){
						onUpdateScreenImpl(pos, maxPos, badSectors, true);
						while(isPaused){
							try {
								Thread.sleep(100);
							} catch (InterruptedException ignored) {}
							while(ci.hasNextKey()){
								if(ci.readKey().getSecond().toLowerCase().equals("p")){
									isPaused = false;
									break;
								}
							}
						}
					}
				}
			}
			private void onUpdateScreenImpl(long pos, long maxPos, long badSectors, boolean isPaused){
				final int barWidth = 50;
				screen.resize(barWidth + 2, 4);
				screen.write("Location: " + pos + " / " + maxPos + "   " + (Math.floor(10000D * pos / maxPos) / 100) + " %", 0, 0, Screen.WHITE, Screen.BLACK);
				int switchPos = (int)(pos * barWidth / maxPos);
				System.out.println("switchPos="+switchPos);
				screen.writeChar('|', 0, 1, Screen.GREEN, Screen.BLACK);
				screen.writeChar('|', barWidth+1, 1, Screen.GREEN, Screen.BLACK);
				for(int p=0; p<barWidth; p++){
					if(p < switchPos){
						screen.writeChar('#', p+1, 1, Screen.WHITE, Screen.WHITE);
					} else if(p > switchPos){
						screen.writeChar(' ', p+1, 1, Screen.BLACK, Screen.BLACK);
					} else {
						int subPos = (int)(pos * barWidth * 4 / maxPos) % 4;
						System.out.println("subPos="+subPos);
						switch(subPos){
							case 0:screen.writeChar(' ', p+1, 1, Screen.BLACK, Screen.BLACK);break;
							case 1:screen.writeChar(' ', p+1, 1, Screen.BLACK, Screen.DARKGRAY);break;
							case 2:screen.writeChar(' ', p+1, 1, Screen.BLACK, Screen.GRAY);break;
							case 3:screen.writeChar(' ', p+1, 1, Screen.BLACK, Screen.WHITE);break;
						}
					}
				}
				screen.write("Unreadable Sectors: " + badSectors + "   " + (Math.floor((double)badSectors / (double)maxPos) / 10000) + " %",
						0, 2, badSectors == 0 ? Screen.WHITE : "Red", Screen.BLACK);
				if(isPaused){
					screen.write(" ||   PAUSED   [Type 'p' to continue]", 0, 3, Screen.YELLOW, Screen.DARKBLUE);
				} else {
					screen.write("[Type 'p' to pause copy process]", 0, 3, Screen.WHITE, Screen.DARKMAGENTA);
				}
				screen.printText();
			}
		};
	}
	
	public static class MyCopyDataStateContext{
		public long lastUpdateMs;
	}

	@Override
	public void onDiskCopyStateUpdate(DiskCopyState state) {
		MyCopyDataStateContext instance = state.getUiPrivateContext(MyCopyDataStateContext.class);
		if(instance == null){
			instance = new MyCopyDataStateContext();
			state.setUiPrivateContext(instance);
		}
		
		long time = System.currentTimeMillis();
		if(time > instance.lastUpdateMs){
			instance.lastUpdateMs = time + 100;
			onUpdateScreenImpl(state.getCurrentSector(), state.getNumSectors(), state.getUnreadableSectorCount(), false);
			boolean isPaused = false;
			while(ci.hasNextKey()){
				if(ci.readKey().getSecond().toLowerCase().equals("p")){
					isPaused = true;
					break;
				}
			}
			if(isPaused){
				onUpdateScreenImpl(state.getCurrentSector(), state.getNumSectors(), state.getUnreadableSectorCount(), true);
				while(isPaused){
					try {
						Thread.sleep(100);
					} catch (InterruptedException ignored) {}
					while(ci.hasNextKey()){
						if(ci.readKey().getSecond().toLowerCase().equals("p")){
							isPaused = false;
							break;
						}
					}
				}
			}
		}
	}
	
	private void onUpdateScreenImpl(long pos, long maxPos, long badSectors, boolean isPaused){
		final int barWidth = 50;
		screen.resize(barWidth + 2, 4);
		screen.write("Location: " + pos + " / " + maxPos + "   " + (Math.floor(10000D * pos / maxPos) / 100) + " %", 0, 0, Screen.WHITE, Screen.BLACK);
		int switchPos = (int)(pos * barWidth / maxPos);
		System.out.println("switchPos="+switchPos);
		screen.writeChar('|', 0, 1, Screen.GREEN, Screen.BLACK);
		screen.writeChar('|', barWidth+1, 1, Screen.GREEN, Screen.BLACK);
		for(int p=0; p<barWidth; p++){
			if(p < switchPos){
				screen.writeChar('#', p+1, 1, Screen.WHITE, Screen.WHITE);
			} else if(p > switchPos){
				screen.writeChar(' ', p+1, 1, Screen.BLACK, Screen.BLACK);
			} else {
				int subPos = (int)(pos * barWidth * 4 / maxPos) % 4;
				System.out.println("subPos="+subPos);
				switch(subPos){
					case 0:screen.writeChar(' ', p+1, 1, Screen.BLACK, Screen.BLACK);break;
					case 1:screen.writeChar(' ', p+1, 1, Screen.BLACK, Screen.DARKGRAY);break;
					case 2:screen.writeChar(' ', p+1, 1, Screen.BLACK, Screen.GRAY);break;
					case 3:screen.writeChar(' ', p+1, 1, Screen.BLACK, Screen.WHITE);break;
				}
			}
		}
		screen.write("Unreadable Sectors: " + badSectors + "   " + (Math.floor((double)badSectors / (double)maxPos) / 10000) + " %",
				0, 2, badSectors == 0 ? Screen.WHITE : "Red", Screen.BLACK);
		if(isPaused){
			screen.write(" ||   PAUSED   [Type 'p' to continue]", 0, 3, Screen.YELLOW, Screen.DARKBLUE);
		} else {
			screen.write("[Type 'p' to pause copy process]", 0, 3, Screen.WHITE, Screen.DARKMAGENTA);
		}
		screen.printText();
	}
}
