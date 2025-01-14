package me.kaigermany.opendiskdiver.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Function;

import me.kaigermany.opendiskdiver.DriveListProvider;
import me.kaigermany.opendiskdiver.data.DriveInfo;
import me.kaigermany.opendiskdiver.reader.ImageFileReader;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.reader.ZipFileReader;
import me.kaigermany.opendiskdiver.utils.OpenFileDialog;
import me.kaigermany.opendiskdiver.utils.SharedText;
import me.kaigermany.opendiskdiver.utils.Utils;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface.Pair;

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

	@Override
	public void showInfo(String[] text) {
		String lastLineText = "Press any key to return.";
		int maxWidth = lastLineText.length();
		for(String s : text) maxWidth = Math.max(maxWidth, s.length());
		screen.resize(maxWidth + 2, text.length + 3);

		screen.writeChar('+', 0, 0, Screen.WHITE, Screen.BLACK);
		screen.writeChar('+', maxWidth + 1, 0, Screen.WHITE, Screen.BLACK);
		for(int i=0; i<maxWidth; i++) screen.writeChar('-', i + 1, 0, Screen.WHITE, Screen.BLACK);
		screen.writeChar('+', 0, text.length + 1, Screen.WHITE, Screen.BLACK);
		screen.writeChar('+', maxWidth + 1, text.length + 1, Screen.WHITE, Screen.BLACK);
		for(int i=0; i<maxWidth; i++) screen.writeChar('-', i + 1, text.length + 1, Screen.WHITE, Screen.BLACK);
		for(int i=0; i<text.length; i++) {
			screen.writeChar('|', 0, i + 1, Screen.WHITE, Screen.BLACK);
			screen.writeChar('|', maxWidth + 1, i + 1, Screen.WHITE, Screen.BLACK);
			screen.write(text[i], 1, i + 1, Screen.WHITE, Screen.BLACK);
		}
		screen.write(lastLineText, 0, text.length + 2, Screen.WHITE, Screen.BLACK);
		screen.printText();
		ci.readKey();
	}
	
	@Override
	//public void sectorInspector(ReadableSource source) {
	public void pagedTextViewer(long numPages, Function<Long, String[]> textRequestCallback){
		long lastPage = 0;
		
		String[] lastText = textRequestCallback.apply(Long.valueOf(0));
		
		StringBuilder numberInputBuffer = new StringBuilder(16);
		while(true){
			ArrayList<String> text = new ArrayList<>();
			text.add("Enter a number between 0 and " + (numPages - 1) + " : " + numberInputBuffer + "_");
			text.add("Use Arrow keys to navigate or press ESC to return.");

			for(String s : lastText) text.add(s);
			
			int maxWidth = 0;
			for(String s : text) maxWidth = Math.max(maxWidth, s.length());
			screen.resize(maxWidth, text.size());
			for(int i=0; i<text.size(); i++) {
				screen.write(text.get(i), 0, i, Screen.WHITE, Screen.BLACK);
			}
			screen.printText();
			
			long currentPage = lastPage;
			Pair<Integer, String> key = ci.readKey();
			if(key.getFirst() == 27){//ESC
				return;
			} else if(key.getSecond().equals("RightArrow") || key.getSecond().equals("DownArrow")){
				currentPage = lastPage + 1;
			} else if(key.getSecond().equals("LeftArrow") || key.getSecond().equals("UpArrow")){
				currentPage = lastPage - 1;
			} else if(key.getFirst() >= '0' && key.getFirst() <= '9'){
				char chr = (char)key.getFirst().intValue();
				numberInputBuffer.append(chr);
			} else if(key.getFirst() == 8){//Backspace
				numberInputBuffer.deleteCharAt(numberInputBuffer.length() - 1);
			} else if(key.getFirst() == 13){//Enter
				String numText = numberInputBuffer.toString();
				numberInputBuffer.setLength(0);
				try{
					currentPage = Long.parseLong(numText);
				}catch(Exception e){
					e.printStackTrace();
				}
			}
			
			if(currentPage >= 0 && currentPage < numPages && currentPage != lastPage){
				lastPage = currentPage;
				lastText = textRequestCallback.apply(Long.valueOf(currentPage));
			}
		}
	}
}
