package me.kaigermany.opendiskdiver.functions;

import java.io.File;
import java.io.IOException;

import me.kaigermany.opendiskdiver.gui.DiskCopyState;
import me.kaigermany.opendiskdiver.gui.UI;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.Utils;
import me.kaigermany.opendiskdiver.writer.ImageFileWriter;
import me.kaigermany.opendiskdiver.writer.Writer;
import me.kaigermany.opendiskdiver.writer.ZipFileWriter;

public class CopyFunction {
	public static void copySource(ReadableSource source, UI ui) throws IOException {
		int type = ui.chooseFromList("Please select an output file type:", new String[]{
				".img - raw disk image",
				".zip - zip file with blocks of compressed sectors",
				"Abort"
		});
		Writer writer = null;
		switch(type){
			case 0: {
				writer = new ImageFileWriter();
				break;
			}
			case 1: {
				writer = new ZipFileWriter();
				break;
			}
			case 2: return;
		}
		
		File outFile = null;
		while(true){
			outFile = ui.saveAs();
			if(outFile.exists()){
				int answer = ui.chooseFromList("File already exists!", new String[]{
						"Rename it",
						"continue and override it",
						"Abort"
				});
				if(answer == 0) continue;
				if(answer == 2) outFile = null;
				break;
			} else {
				break;
			}
		}
		if(outFile == null) return;
		
		long freeBytesOnTargetDisk = getFreeSpaceOnDisk(outFile);
		long bytesNeeded = source.numSectors() * 512;
		if(freeBytesOnTargetDisk < bytesNeeded){
			String title = "Warning: not enough space: expected: " 
					+ Utils.toHumanReadableFileSize(bytesNeeded) + ", avaliable: "
					+ Utils.toHumanReadableFileSize(freeBytesOnTargetDisk) + " -> missing: "
					+ Utils.toHumanReadableFileSize(bytesNeeded - freeBytesOnTargetDisk) + " - CONTINUE?";
			int answer = ui.chooseFromList(title, new String[]{
					"No",
					"Yes"
			});
			if(answer == 0) return;
		}
		
		writer.create(outFile, source);
		try {
			copyDisk(source, writer, ui);
		} finally {
			writer.close();
		}
	}
	
	public static void copyDisk(ReadableSource reader, Writer writer, UI ui) throws IOException {
		DiskCopyState state = new DiskCopyState(reader.numSectors());
		try{
			byte[] buf = new byte[1 << 20];
			int maxLen = buf.length / 512;
			long pos = 0;
			long maxPos = reader.numSectors();
			while(pos < maxPos){
				int numSectorsToRead = (int)Math.min(maxPos - pos, maxLen);
				try{
					
					reader.readSectors(pos, numSectorsToRead, buf, 0);
					writer.write(buf, numSectorsToRead * 512);
					pos += numSectorsToRead;

					state.setCurrentSector(pos);
					ui.onDiskCopyStateUpdate(state);
				}catch(IOException blockReadError){
					//block read failed, try single sector read mode
					//byte[] dummyBuffer = new byte[512];
					byte[] readBuffer = new byte[512];
					for(int offset=0; offset<numSectorsToRead; offset++){
						state.setCurrentSector(pos + offset);
						ui.onDiskCopyStateUpdate(state);
						try{
							reader.readSectors(pos + offset, 1, readBuffer, 0);
							writer.write(readBuffer, 512);
						}catch(IOException sectorReadError){
							//state.markSectorAsUnreadable(pos + offset);
							state.incrUnreadableSectorCount();
							//writer.write(dummyBuffer, 512);
							writer.writePlaceholderSector();
						}
					}
					pos += numSectorsToRead;
				}
			}
		} finally {
			ui.showInfo(new String[]{
					"finished writing " + reader.numSectors() + " sectors.",
					state.getUnreadableSectorCount() == 0 ? "No invalid sectors detected." : ("Found " + state.getUnreadableSectorCount() + " invalid sectors!")
			}, true);
		}
	}
	
	private static long getFreeSpaceOnDisk(File fileOnDisk){
		File root = fileOnDisk;
		while(fileOnDisk != null){
			root = fileOnDisk;
			fileOnDisk = fileOnDisk.getParentFile();
		}
		return root.getUsableSpace();
	}
}
