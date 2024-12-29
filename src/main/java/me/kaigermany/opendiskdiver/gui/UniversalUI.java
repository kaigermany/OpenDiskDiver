package me.kaigermany.opendiskdiver.gui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.DriveListProvider;
import me.kaigermany.opendiskdiver.data.CopyActivity;
import me.kaigermany.opendiskdiver.data.DriveInfo;
import me.kaigermany.opendiskdiver.reader.ImageFileReader;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.reader.ZipFileReader;
import me.kaigermany.opendiskdiver.utils.SharedText;
import me.kaigermany.opendiskdiver.utils.Utils;
import me.kaigermany.opendiskdiver.writer.Writer;

public class UniversalUI implements UI {
	@Override
	public int cooseFromList(String title, String[] entries) {
		System.out.println();
		System.out.println(title);
		return CmdGui.listSelectBlocking(entries);
	}

	@Override
	public ReadableSource cooseSource() throws IOException {
		ArrayList<DriveInfo> drives = DriveListProvider.listDrives();
		String[] list = new String[drives.size() + 2];
		for(int i=0; i<drives.size(); i++){
			DriveInfo drive = drives.get(i);
			list[i] = Utils.toHumanReadableFileSize(drive.size) + " \t " + drive.name;
		}
		int selectImgIndex = list.length - 2;
		int selectZipIndex = list.length - 1;
		list[selectImgIndex] = SharedText.pseudoSources[0];
		list[selectZipIndex] = SharedText.pseudoSources[1];
		int index = CmdGui.listSelectBlocking(list);
		if(index == selectImgIndex){
			File file = CmdGui.askForFilePathBlocking();
			return new ZipFileReader(file);
		} else if(index == selectZipIndex) {
			File file = CmdGui.askForFilePathBlocking();
			return new ImageFileReader(file);
		} else {
			return drives.get(index).openReader();
		}
	}

	@Override
	public void close() {
		System.out.println("Thanks for using OpenDiskDiver :)");
	}

	@Override
	public File saveAs() {
		System.out.println("Please enter a output file path:");
		try{
			String line = CmdGui.readLine().trim();
			return new File(line);
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}
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
					lastTimeScreenUpdated = time + 1000;
					System.out.println("Location: " + pos + " / " + maxPos + " \t " + (Math.floor(10000D * pos / maxPos) / 100) + " %");
					System.out.println("Unreadable Sectors: " + badSectors + " \t " + (Math.floor((double)badSectors / (double)maxPos) / 10000) + " %");
					System.out.println("[Type 'pause' to pause copy process]");
				}
			}
		};
	}
	
	public static class MyCopyDataStateContext{
		public long lastUpdateMs;
		public ByteArrayOutputStream baos = new ByteArrayOutputStream();
	}
	
	private static boolean readOrTimeout(InputStream is, ByteArrayOutputStream baos) {
		try{
			while(is.available() != 0){
				int chr = is.read();
				if(chr == -1) return false;
				if(chr == '\n') return true;
				baos.write(chr);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}
	
	@Override
	public void onDiskCopyStateUpdate(DiskCopyState state) {
		MyCopyDataStateContext instance = state.getUiPrivateContext(MyCopyDataStateContext.class);
		if(instance == null){
			instance = new MyCopyDataStateContext();
			state.setUiPrivateContext(instance);
		}
		String prompt = null;
		if(readOrTimeout(System.in, instance.baos)){
			instance.lastUpdateMs = 0;
			prompt = new String(instance.baos.toByteArray(), StandardCharsets.UTF_8);
			instance.baos.reset();
			prompt = prompt.trim();
		}
		long time = System.currentTimeMillis();
		if(time > instance.lastUpdateMs){
			instance.lastUpdateMs = time + 1000;
			onUpdateScreen(state.getCurrentSector(), state.getNumSectors(), state.getUnreadableSectorCount(), false);

			if(prompt != null && prompt.equalsIgnoreCase("pause")){
				while(true){
					onUpdateScreen(state.getCurrentSector(), state.getNumSectors(), state.getUnreadableSectorCount(), true);
					prompt = readNextLine(System.in);
					if(prompt.equalsIgnoreCase("pause")){
						break;
					}
				}
			}
		}
	}
	
	private String readNextLine(InputStream is) {
		try{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int chr;
			while((chr = is.read()) != -1){
				baos.write(chr);
				if(chr == '\n') return new String(baos.toByteArray(), StandardCharsets.UTF_8).trim();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}

	private void onUpdateScreen(long pos, long maxPos, long badSectors, boolean isPaused){
		System.out.println("Location: " + pos + " / " + maxPos + " \t " + (Math.floor(10000D * pos / maxPos) / 100) + " %");
		System.out.println("Unreadable Sectors: " + badSectors + " \t " + (Math.floor((double)badSectors / (double)maxPos) / 10000) + " %");
		if(isPaused){
			System.out.println("[Type 'pause' to continue copy process]");
		} else {
			System.out.println("[Type 'pause' to pause copy process]");
		}
	}
}
