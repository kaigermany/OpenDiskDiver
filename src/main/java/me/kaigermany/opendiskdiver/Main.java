package me.kaigermany.opendiskdiver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import me.kaigermany.opendiskdiver.data.Reader;
import me.kaigermany.opendiskdiver.data.fat.FatReader;
import me.kaigermany.opendiskdiver.data.ntfs.NtfsReader;
import me.kaigermany.opendiskdiver.data.partition.Partition;
import me.kaigermany.opendiskdiver.data.partition.PartitionReader;
import me.kaigermany.opendiskdiver.datafilesystem.FileEntry;
import me.kaigermany.opendiskdiver.datafilesystem.FileSystem;
import me.kaigermany.opendiskdiver.gui.DiskCopyState;
import me.kaigermany.opendiskdiver.gui.UI;
import me.kaigermany.opendiskdiver.gui.UniversalUI;
import me.kaigermany.opendiskdiver.gui.WindowsUI;
import me.kaigermany.opendiskdiver.probe.Probe;
import me.kaigermany.opendiskdiver.probe.ProbeFunction;
import me.kaigermany.opendiskdiver.probe.ProbeResult;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;
import me.kaigermany.opendiskdiver.utils.Platform;
import me.kaigermany.opendiskdiver.utils.Utils;
import me.kaigermany.opendiskdiver.writer.ImageFileWriter;
import me.kaigermany.opendiskdiver.writer.Writer;
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
				ReadableSource source;
				switch (id) {
					case 0:
						source = ui.cooseSource();
						analyzeSource(source, ui);
						continue;
					case 1:
						source = ui.cooseSource();
						copySource(source, ui);
						continue;
					case 2:
						source = ui.cooseSource();
						//TODO
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
					+ Utils.toHumanReadableFileSize(bytesNeeded) + ", avaliable: "
					+ Utils.toHumanReadableFileSize(freeBytesOnTargetDisk) + " -> missing: "
					+ Utils.toHumanReadableFileSize(bytesNeeded - freeBytesOnTargetDisk) + " - CONTINUE?";
			int answer = ui.cooseFromList(title, new String[]{
					"No",
					"Yes"
			});
			if(answer == 0) return;
		}
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
		}
		writer.create(outFile, source);
		
		//ui.getCopyDiskActivityHandler().onCopy(source, writer);
		copyDisk(source, writer, ui);
		
		writer.close();
	}
	
	public static void copyDisk(ReadableSource reader, Writer writer, UI ui) throws IOException {
		byte[] buf = new byte[1 << 20];
		int maxLen = buf.length / 512;
		long pos = 0;
		long maxPos = reader.numSectors();
		//long badSectors = 0;
		
		DiskCopyState state = new DiskCopyState(maxPos);
		
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
				
				byte[] dummyBuffer = new byte[512];
				byte[] readBuffer = new byte[512];
				for(int offset=0; offset<numSectorsToRead; offset++){
					//onUpdateScreen(pos, maxPos, badSectors);
					state.setCurrentSector(pos + offset);
					ui.onDiskCopyStateUpdate(state);
					try{
						reader.readSectors(pos + offset, 1, readBuffer, 0);
						writer.write(readBuffer, 512);
					}catch(IOException sectorReadError){
						//badSectors++;
						state.incrUnreadableSectorCount();
						writer.write(dummyBuffer, 512);
					}
				}
				pos += numSectorsToRead;
				
			}
			//onUpdateScreen(pos, maxPos, badSectors);
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
	
	private static ReadableSource selectPartition(ReadableSource diskSource, UI ui) throws IOException {
		ArrayList<Partition> partitions = new PartitionReader(diskSource).getPartitions();
		if(partitions.size() == 0){
			return diskSource;
		} else if(partitions.size() == 1){
			return partitions.get(0).source;
		} else {
			
			String[] list = new String[partitions.size()];
			for(int i=0; i<list.length; i++){
				Partition p = partitions.get(i);
				list[i] = "["+p.offset+" .. "+(p.offset+p.len-1)+"   '"+p.name+"']";
			}
			String type = partitions.get(0).isGPT ? "GPT" : "MBR";
			int selected = ui.cooseFromList("Please select a partition: (type: " + type + ")", list);
			return partitions.get(selected).source;
			
		}
	}
	
	private static void analyzeSource(ReadableSource source, UI ui) throws IOException {
		ArrayList<Partition> partitions = new PartitionReader(source).getPartitions();
		if(partitions.size() == 0){
			analyzePartition(source, ui);
			return;
		}
		while(true){
			switch( ui.cooseFromList("Please select a analyze method:", new String[]{
					"Select specific partition",
					"Inspect sectors",
					"Show partition table details",
					"Back"
			}) ){
				case 0:{
					ReadableSource partition = selectPartition(source, ui);
					analyzePartition(partition, ui);
					break;
				}
				case 1: {
					ui.sectorInspector(source);
					break;
				}
				case 2: {
					ArrayList<String> text = new ArrayList<>();
					boolean isGPT = partitions.get(0).isGPT;
					text.add("Partition Format: " + (isGPT ? "GPT" : "MBR"));
					text.add("Sectors available: " + source.numSectors());
					{
						long usedSectors = 1 + (isGPT ? 33*2 : 0);
						for(Partition p : partitions){
							usedSectors += p.len;
						}
						text.add("Sectors used: " + usedSectors);
						text.add("Sectors free: " + (source.numSectors() - usedSectors));
					}
					text.add("##### Disk Layout: #####");
					text.add("offset, size, type");
					if(isGPT){
						text.add("0   +1   Protective MBR Partition Table");
						text.add("1   +33  GPT Partition Table");
					} else {
						text.add("0   +1   MBR Partition Table");
					}
					long pos = isGPT ? 34 : 1;
					long endPos = source.numSectors() - (isGPT ? 33 : 0);
					Iterator<Partition> it = partitions.iterator();
					Partition nextPartition = it.next();
					int partitionIndex = 0;
					while(pos < endPos){
						long nextPartitionOffset = nextPartition != null ? nextPartition.offset : endPos;
						if(pos != nextPartitionOffset){
							long len = nextPartitionOffset - pos;
							text.add(pos + "   +" + len + "   [Free Space]");
							pos = nextPartitionOffset;
							continue;
						}
						System.out.println(nextPartition);
						partitionIndex++;
						text.add(nextPartition.offset + "   +" + nextPartition.len + "   Partition " + partitionIndex);
						pos = nextPartition.offset + nextPartition.len;
						nextPartition = it.hasNext() ? it.next() : null;
					}
					if(isGPT){
						text.add(endPos + "   +33   GPT Backup Table");
					}
					//text.add("findLastValidSector() -> " + findLastValidSector(source));
					ui.showInfo(text.toArray(new String[text.size()]));
					break;
				}
				case 3:{
					return;
				}
			}
		}
	}
	
	

	private static void analyzePartition(ReadableSource source, UI ui) {
		// TODO Auto-generated method stub
		
	}

	private static void listFilesFromSource(ReadableSource source, UI ui) throws IOException {
		PartitionReader r = new PartitionReader(source);
		ArrayList<Partition> partitions = r.getPartitions();
		System.out.println("found partitions: " + partitions);
		Reader reader = null;
		if(partitions.size() > 0){
			ReadableSource partitionSource = selectPartition(source, ui);
			byte[] buffer = new byte[512];
			partitionSource.readSector(0, buffer);
			ProbeResult result = Probe.detectType(buffer);
			System.out.println(result);
			reader = result.getSortedResults().get(0).getReader();
			if(reader != null) {
				reader.read(partitionSource);
			}
			//TODO select partition by user
			/*
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
			*/
		} else {
			System.out.println("no partitions found, try direct type detection...");
			byte[] buffer = new byte[512];
			source.readSector(0, buffer);
			ProbeResult result = Probe.detectType(buffer);
			System.out.println(result);
			reader = result.getSortedResults().get(0).getReader();
			if(reader != null) {
				reader.read(source);
			}
		}
		if(reader != null && reader instanceof FileSystem){
			FileSystem fs = (FileSystem)reader;
			List<FileEntry> files = fs.listFiles();
			StringBuilder sb = new StringBuilder(files.size());
			ArrayList<String> arr = new ArrayList<>(files.size());
			for(FileEntry f : files){
				//System.out.println(f.nameAndPath);
				//TODO check why there sometimes is f.name == null !
				if(f.name == null) continue;
				arr.add(f.nameAndPath);
			}
			arr.sort(new Comparator<String>() {
				@Override
				public int compare(String o1, String o2) {
					int l = Math.min(o1.length(), o2.length());
					for(int i=0; i<l; i++){
						char c1 = o1.charAt(i);
						char c2 = o2.charAt(i);
						if(c1 != c2) return Character.compare(c1, c2);
					}
					return Integer.compare(o1.length(), o2.length());
				}
			});
			for(String s : arr){
				sb.append(s).append("\r\n");
			}
			// dump test:
			try{
				byte[] a = sb.toString().getBytes("UTF-8");
				FileOutputStream fos = new FileOutputStream(new File("H:/dump.txt"));
				fos.write(a);
				fos.close();
			}catch(Exception e){
				e.printStackTrace();
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
