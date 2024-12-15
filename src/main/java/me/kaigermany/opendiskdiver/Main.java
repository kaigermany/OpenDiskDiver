package me.kaigermany.opendiskdiver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.data.DriveInfo;
import me.kaigermany.opendiskdiver.data.Reader;
import me.kaigermany.opendiskdiver.data.fat.FatEntryFinder;
import me.kaigermany.opendiskdiver.data.fat.FatReader;
import me.kaigermany.opendiskdiver.data.ntfs.NtfsReader;
import me.kaigermany.opendiskdiver.data.partition.Partition;
import me.kaigermany.opendiskdiver.data.partition.PartitionReader;
import me.kaigermany.opendiskdiver.gui.CmdGui;
import me.kaigermany.opendiskdiver.gui.Screen;
import me.kaigermany.opendiskdiver.probe.Probe;
import me.kaigermany.opendiskdiver.probe.ProbeFunction;
import me.kaigermany.opendiskdiver.probe.ProbeResult;
import me.kaigermany.opendiskdiver.reader.ImageFileReader;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.reader.ZipFileReader;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;
import me.kaigermany.opendiskdiver.utils.Platform;
import me.kaigermany.opendiskdiver.windows.SelectDriveGui;
import me.kaigermany.opendiskdiver.windows.WindowsDrives;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface;
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
		UI ui = createUI();
		int id = ui.cooseFromList("Welcome! Please coose your operation mode:", new String[]{
				"Anaylzer Mode",	//call classic parsers.
				"Copy Mode",		//allow disk copy tasks.
				"Recovery Mode"		//specialized algorithms that try to recover as much as possible out of the given data.
		});
		try{
			ReadableSource source = null;
			switch (id) {
			case 0:
				source = ui.cooseSource();
				break;

			default:
				break;
			}
			
			
			
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
			
			
			
			//ImageFileWriter.write(partitions.get(0).source, new File("H:\\partition0.img"));
			//ZipFileWriter.write(partitions.get(0).source, new File("H:\\partition0.img.zip"), (1 << 20) / 512);
			//ImageFileWriter.write(partitions.get(0).source, new File("D:\\temp\\partition0.img"));
			//ImageFileWriter.write(partitions.get(2).source, new File("D:\\temp\\partition2.img"));
			//ZipFileWriter.write(source, diskImageZip, (1 << 20) / 512);
			//ImageFileWriter.write(source, diskImageFile);
			
			/*
			for(Partition p : r.getPartitions()){
			//{Partition p = r.getPartitions().get(1);
				byte[] buffer = new byte[512];
				p.source.readSector(0, buffer);
				ProbeResult result = Probe.detectType(buffer);
				System.out.println(result);
				
				Reader reader = result.getSortedResults().get(0).getReader();
				if(reader != null) reader.read(p.source);
				/ *
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
				* /
				
			}
			*/
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static UI createUI(){
		if(Platform.isWindows() &false){
			return new WindowsUI();
		} else {
			return new UniversalUI();
		}
	}
	
	public static interface UI {
		int cooseFromList(String title, String[] entries);

		ReadableSource cooseSource() throws IOException;
	}
	
	public static class WindowsUI implements UI {
		private static ConsoleInterface ci = new ConsoleInterface();
		private static Screen screen = new Screen(1, 1, ci);

		@Override
		public int cooseFromList(String title, String[] entries) {
			return new SelectDriveGui(screen, ci).selectListEntry(title, entries);
		}

		@Override
		public ReadableSource cooseSource() throws IOException {
			return new SelectDriveGui(screen, ci).selectDiskSource();
		}
		
	}
	
	public static class UniversalUI implements UI {
		@Override
		public int cooseFromList(String title, String[] entries) {
			System.out.println();
			System.out.println(title);
			return CmdGui.listSelectBlocking(entries);
		}

		@Override
		public ReadableSource cooseSource() throws IOException {
			ArrayList<DriveInfo> drives = WindowsDrives.listDrives();
			String[] list = new String[drives.size() + 2];
			for(int i=0; i<drives.size(); i++){
				DriveInfo drive = drives.get(i);
				list[i] = SelectDriveGui.toHumanReadableFileSize(drive.size) + " \t " + drive.name;
			}
			int selectImgIndex = list.length - 2;
			int selectZipIndex = list.length - 1;
			list[selectImgIndex] = SelectDriveGui.pseudoSources[0];
			list[selectZipIndex] = SelectDriveGui.pseudoSources[1];
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
	}
}
