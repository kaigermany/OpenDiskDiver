package me.kaigermany.opendiskdiver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.data.Reader;
import me.kaigermany.opendiskdiver.data.fat.FatReader;
import me.kaigermany.opendiskdiver.data.ntfs.NtfsReader;
import me.kaigermany.opendiskdiver.data.partition.Partition;
import me.kaigermany.opendiskdiver.data.partition.PartitionReader;
import me.kaigermany.opendiskdiver.gui.UI;
import me.kaigermany.opendiskdiver.gui.UniversalUI;
import me.kaigermany.opendiskdiver.gui.WindowsUI;
import me.kaigermany.opendiskdiver.probe.Probe;
import me.kaigermany.opendiskdiver.probe.ProbeFunction;
import me.kaigermany.opendiskdiver.probe.ProbeResult;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;
import me.kaigermany.opendiskdiver.utils.Platform;
import me.kaigermany.opendiskdiver.windows.SelectDriveGui;
import me.kaigermany.opendiskdiver.writer.ImageFileWriter;
import me.kaigermany.opendiskdiver.writer.ZipFileWriter;

public class Main {
	
	
	static{
		Probe.regiterProbeTester(new ProbeFunction() {
			@Override
			public String getName() { return "NTFS"; }

			@Override
			public Reader getReader() { return new NtfsReader(); }
			
			@Override
			public float probe(byte[] sampleData) throws Throwable {
				long Signature = ByteArrayUtils.read64(sampleData, 3);
				return Signature == 0x202020205346544EL ? 1 : 0;
			}
		});
		
		Probe.regiterProbeTester(new ProbeFunction() {
			@Override
			public String getName() { return "FAT"; }

			@Override
			public Reader getReader() { return new FatReader(); }
			
			@Override
			public float probe(byte[] sampleData) throws Throwable {
				return FatReader.isFatFormated(sampleData) ? 0.99F : 0;
			}
		});
		
		Probe.regiterProbeTester(new ProbeFunction() {
			@Override
			public String getName() { return "___EMPTY___"; }

			@Override
			public Reader getReader() { return null; }
			
			@Override
			public float probe(byte[] sampleData) throws Throwable {
				return ByteArrayUtils.isEmptySector(sampleData) ? 1 : 0;
			}
		});
		
	}
	
	public static void main(String[] args) {
		final UI ui = createUI(false);
		while(true){
			int id = ui.cooseFromList("Welcome! Please coose your operation mode:", new String[]{
					"Anaylzer Mode",	//call classic parsers.
					"Copy Mode",		//allow disk copy tasks.
					"Recovery Mode",	//specialized algorithms that try to recover as much as possible out of the given data.
					"Exit"
			});
			try{
				ReadableSource source = null;
				switch (id) {
					case 0:
						source = ui.cooseSource();
						analyzeSource(source);
						continue;
					case 1:
						source = ui.cooseSource();
						copySource(source, ui);
						continue;
					case 2:
						source = ui.cooseSource();
						continue;
					case 3:
						ui.close();
						return;
				}
			
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private static void copySource(ReadableSource source, UI ui) throws IOException {
		int type = ui.cooseFromList("Please select an output file type:", new String[]{
				".img - raw disk image",
				".zip - zip file with blocks of compressed sectors",
				"Abbort"
		});
		if(type == 2) return;
		
		File outFile = null;
		while(true){
			outFile = ui.saveAs();
			if(outFile.exists()){
				int answer = ui.cooseFromList("File already exists!", new String[]{
						"Rename it",
						"continue and override it",
						"Abbort"
				});
				if(answer == 0) continue;
				if(answer == 2) outFile = null;
				break;
			} else {
				break;
			}
		}
		if(outFile == null) return;
		
		long freeBytesOnTargetDisk = getFreeSpaceOnDisk(outFile);//.getFreeSpace();//outFile.getUsableSpace();
		long bytesNeeded = source.numSectors() * 512;
		if(freeBytesOnTargetDisk < bytesNeeded){
			String title = "Warning: not enough space: expected: " 
					+ SelectDriveGui.toHumanReadableFileSize(bytesNeeded) + ", avaliable: "
					+ SelectDriveGui.toHumanReadableFileSize(freeBytesOnTargetDisk) + " -> missing: "
					+ SelectDriveGui.toHumanReadableFileSize(bytesNeeded - freeBytesOnTargetDisk) + " - CONTINUE?";
			int answer = ui.cooseFromList(title, new String[]{
					"No",
					"Yes"
			});
			if(answer == 0) return;
		}
		
		switch(type){
			case 0: {
				ImageFileWriter.write(source, outFile);
				break;
			}
			case 1: {
				ZipFileWriter.write(source, outFile, (1 << 20) / 512);
				break;
			}
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
	
	private static void analyzeSource(ReadableSource source) throws IOException {
		PartitionReader r = new PartitionReader(source);
		ArrayList<Partition> partitions = r.getPartitions();
		System.out.println("found partitions: " + partitions);
		if(partitions.size() > 0){
			for(Partition p : partitions){
				System.out.println(p);
				byte[] buffer = new byte[512];
				p.source.readSector(0, buffer);
				ProbeResult result = Probe.detectType(buffer);
				System.out.println(result);
				Reader reader = result.getSortedResults().get(0).getReader();
				if(reader != null) {
					reader.read(p.source);
					break;
				}
			}
		} else {
			System.out.println("no partitions found, try direct type detection...");
			byte[] buffer = new byte[512];
			source.readSector(0, buffer);
			ProbeResult result = Probe.detectType(buffer);
			System.out.println(result);
			Reader reader = result.getSortedResults().get(0).getReader();
			if(reader != null) {
				reader.read(source);
			}
		}
		/*
		if(reader instanceof NtfsReader){
			NtfsReader ntfs = (NtfsReader)reader;
			ntfs.read(p.source);
			for(String file : ntfs.fileMap.keySet()){
				//defender control
				if(file != null && file.equalsIgnoreCase("defender")){
					System.out.println(file);
				}
			}
		}
		*/
		
		/*
		PartitionReader r = new PartitionReader(diskSource);
		System.out.println(r.getPartitions());
		
		ArrayList<FatReader.FileEntry> entries = FatEntryFinder.scanReader(diskSource);
		System.out.println(entries.toString().replace("}, {", "},\n{"));
		
		FatReader fr = new FatReader();
		fr.read(r.getPartitions().get(0).source);
		 */
	}
	
	public static UI createUI(boolean useNativeUI){
		if(Platform.isWindows() && !useNativeUI){
			return new WindowsUI();
		} else {
			return new UniversalUI();
		}
	}
}
