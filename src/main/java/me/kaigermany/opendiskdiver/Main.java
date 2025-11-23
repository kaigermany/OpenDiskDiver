package me.kaigermany.opendiskdiver;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import me.kaigermany.opendiskdiver.analysis.FatEntryScanner;
import me.kaigermany.opendiskdiver.analysis.FileHeaderScanner;
import me.kaigermany.opendiskdiver.analysis.Scanner;
import me.kaigermany.opendiskdiver.data.Reader;
import me.kaigermany.opendiskdiver.data.partition.Partition;
import me.kaigermany.opendiskdiver.data.partition.PartitionReader;
import me.kaigermany.opendiskdiver.datafilesystem.FileSystem;
import me.kaigermany.opendiskdiver.functions.CopyFunction;
import me.kaigermany.opendiskdiver.gui.FileSystemBrowser;
import me.kaigermany.opendiskdiver.gui.UI;
import me.kaigermany.opendiskdiver.gui.UniversalUI;
import me.kaigermany.opendiskdiver.gui.WindowsUI;
import me.kaigermany.opendiskdiver.probe.Probe;
import me.kaigermany.opendiskdiver.probe.ProbeResult;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;
import me.kaigermany.opendiskdiver.utils.DumpUtils;
import me.kaigermany.opendiskdiver.utils.Platform;
import me.kaigermany.opendiskdiver.utils.Utils;

// https://www.grc.com/srrecovery.htm
// http://web.archive.org/web/20190311160549/http://www.forensicswiki.org/wiki/Carving

public class Main {
	public static void main(String[] args) {
		final UI ui = createUI(false);
		while(true){
			int id = ui.cooseFromList("Welcome! Please coose your operation mode:", new String[]{
					"Anaylze a drive",
					"Compare Drives or Images",
					"Exit"
			});
			try{
				switch (id) {
					case 0:
						driveOptions(ui, ui.cooseSource());
						continue;
					case 1:
						{
							ReadableSource source1 = ui.cooseSource();
							ReadableSource source2 = ui.cooseSource();
							ui.showInfo(new String[]{
									"Select an output file for the log-dump."
							}, false);
							File logFile = ui.saveAs();
							if(logFile != null){
								compareDrives(ui, source1, source2, logFile);
							}
						}
						continue;
					case 2:
						ui.close();
						return;
				}
			
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static UI createUI(boolean useNativeUI){
		if(Platform.isWindows() && !useNativeUI){
			return new WindowsUI();
		} else {
			return new UniversalUI();
		}
	}
	
	private static void driveOptions(UI ui, final ReadableSource source) {
		while(true){
			int id = ui.cooseFromList("Please coose your operation mode:", new String[]{
					"Select specific partition",
					"Inspect sectors",
					"Show partition table details",
					"Enter analyzer-mode",
					"Copy drive",
					"Exit"
			});
			try{
				switch (id) {
					case 0:
						ReadableSource partition = selectPartition(source, ui);
						analyzePartition(partition, ui);
						break;
					case 1: {
						//ui.sectorInspector(source);
						ui.pagedTextViewer(source.numSectors(), new Function<Long, String[]>() {
							@Override
							public String[] apply(Long sector) {
								byte[] buffer = new byte[512];
								boolean success;
								try{
									source.readSector(sector.longValue(), buffer);
									success = true;
								}catch(IOException e){
									e.printStackTrace();
									success = false;
								}
								
								String text = "Sector #" + sector.toString() + ":\r\n";
								if(success){
									text += DumpUtils.binaryDumpToString(buffer);
								} else {
									text += "FAILED TO READ SECTOR!";
								}
								
								return text.split("\r\n");
							}
						});
						
						break;
					}
					case 2: {
						ArrayList<String> text = buildPartitionInfoText(source);
						ui.showInfo(text.toArray(new String[text.size()]), true);
						break;
					}
					case 3:{
						diskAnalyzerMode(source, ui);
						break;
					}
					case 4:
						CopyFunction.copySource(source, ui);
						continue;
					case 5:
						return;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private static ArrayList<String> buildPartitionInfoText(ReadableSource source) throws IOException {
		ArrayList<String> text = new ArrayList<>();
		ArrayList<Partition> partitions = new PartitionReader(source).getPartitions();
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
		
		text.add("");
		
		//build text as table
		ArrayList<String[]> tableViewBuilder = new ArrayList<>();
		tableViewBuilder.add(new String[]{"offset", "size", "type"});
		if(isGPT){
			tableViewBuilder.add(new String[]{"0", "+1", "Protective MBR Partition Table"});
			tableViewBuilder.add(new String[]{"1", "+33", "GPT Partition Table"});
		} else {
			tableViewBuilder.add(new String[]{"0", "+1", "MBR Partition Table"});
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
				tableViewBuilder.add(new String[]{String.valueOf(pos), "+" + len, "[Free Space]"});
				pos = nextPartitionOffset;
				continue;
			}
			System.out.println(nextPartition);
			partitionIndex++;
			tableViewBuilder.add(new String[]{String.valueOf(nextPartition.offset),
					"+" + nextPartition.len, "Partition " + partitionIndex});
			pos = nextPartition.offset + nextPartition.len;
			nextPartition = it.hasNext() ? it.next() : null;
		}
		if(isGPT){
			tableViewBuilder.add(new String[]{String.valueOf(endPos), "+33", "GPT Backup Table"});
		}

		
		for(String line : Utils.renderStylizedTable("Disk Layout", tableViewBuilder, null)){
			text.add(line);
		}
		//text.add("findLastValidSector() -> " + findLastValidSector(source));
		return text;
	}
	
	
	private static ReadableSource selectPartition(ReadableSource diskSource, UI ui) throws IOException {
		ArrayList<Partition> partitions = new PartitionReader(diskSource).getPartitions();
		if(partitions.size() == 0){
			ui.cooseFromList("Cant read Partition table.", new String[]{"Select whole drive."});
			return diskSource;
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
	
	
	private static void analyzePartition(ReadableSource partitionSource, UI ui) throws IOException {
		byte[] buffer = new byte[512];
		partitionSource.readSector(0, buffer);
		boolean hasNoFilesystem = Probe.detectType(buffer).getSortedResults().isEmpty();
		buffer = null;
		if(hasNoFilesystem){
			int continueAnalyze = ui.cooseFromList("Unable to detect a valid Filesystem. Enter analyzer-mode?", new String[]{
					"Yes",
					"Abort and return"
			});
			if(continueAnalyze == 1) return;
			diskAnalyzerMode(partitionSource, ui);
		} else {
			listFilesFromSource(partitionSource, ui);
		}
	}

	private static void listFilesFromSource(ReadableSource partitionSource, UI ui) throws IOException {
		byte[] buffer = new byte[512];
		partitionSource.readSector(0, buffer);
		ProbeResult result = Probe.detectType(buffer);
		buffer = null;
		System.out.println(result);
		Reader reader = result.getSortedResults().get(0).getReader();
		if(reader == null) return;
		result = null;
		
		reader.read(partitionSource);
		
		if(reader instanceof FileSystem){
			FileSystem fs = (FileSystem)reader;
			
			FileSystemBrowser.browse(fs, ui);
		}
	}

	private static void diskAnalyzerMode(ReadableSource source, UI ui) throws IOException {
		ui.showInfo(new String[]{
			"begin analyze " + source.numSectors() + " sectors...",
		}, false);
		
		final ArrayList<String> log = new ArrayList<>(1024);
		final boolean[] hasMatchPtr = {false};
		BiConsumer<Scanner, String> logCallback = new BiConsumer<Scanner, String>() {
			@Override
			public void accept(Scanner scanner, String msg) {
				msg = "#" + (log.size() + 1) + " [" + scanner.getClass().getSimpleName() + "] " + msg;
				log.add(msg);
				hasMatchPtr[0] = true;
			}
		};
		ArrayList<Scanner> scannerList = new ArrayList<>();
		scannerList.add(new FatEntryScanner());
		scannerList.add(new FileHeaderScanner());
		
		
		long diskSize = source.numSectors();
		byte[] buffer = new byte[512];
		long lastRedraw = System.currentTimeMillis();
		ArrayList<Long> matchOffsets = new ArrayList<>();
		for(long offset=0; offset<diskSize; offset++){
			source.readSector(offset, buffer);
			
			for(Scanner scanner : scannerList){
				scanner.scan(buffer, offset, logCallback);
			}
			if(hasMatchPtr[0]){
				hasMatchPtr[0] = false;
				matchOffsets.add(offset);
			}
			long t = System.currentTimeMillis();
			if(t - 250 >= lastRedraw){
				lastRedraw = t;
				int maxSize = Math.min(10, log.size());
				String[] consoleMessage = new String[maxSize + 1];
				log.subList(log.size() - maxSize, log.size()).toArray(consoleMessage);
				consoleMessage[maxSize] = "Scanner Location: " + offset + " / " + diskSize + "   " + (Math.floor(10000D * offset / diskSize) / 100) + " %";
				ui.showInfo(consoleMessage, false);
			}
		}
		
		long[] clusterSizeAndOffset = guessClusterSizeAndOffset(matchOffsets);
		System.out.println("clusterSize = " + clusterSizeAndOffset[0]);
		System.out.println("Offset = " + clusterSizeAndOffset[1]);
		
		//restart with the new information:
		log.clear();
		long stepSize = clusterSizeAndOffset[0];
		for(long offset=clusterSizeAndOffset[1]; offset<diskSize; offset+=stepSize){
			source.readSector(offset, buffer);
			
			for(Scanner scanner : scannerList){
				scanner.scan(buffer, offset, logCallback);
			}
			if(hasMatchPtr[0]){
				hasMatchPtr[0] = false;
				matchOffsets.add(offset);
			}
			long t = System.currentTimeMillis();
			if(t - 250 >= lastRedraw){
				lastRedraw = t;
				int maxSize = Math.min(10, log.size());
				String[] consoleMessage = new String[maxSize + 1];
				log.subList(log.size() - maxSize, log.size()).toArray(consoleMessage);
				consoleMessage[maxSize] = "Scanner Location: " + offset + " / " + diskSize + "   " + (Math.floor(10000D * offset / diskSize) / 100) + " %";
				ui.showInfo(consoleMessage, false);
			}
		}
		
		ui.showInfo(log.toArray(new String[log.size()]), true);
	}

	private static long[] guessClusterSizeAndOffset(ArrayList<Long> matchOffsets) {
		//a x a to match every hit against every other:
		HashMap<Long, long[]> ggtResultsScored = new HashMap<>();
		for(int p1=0; p1<matchOffsets.size()-1; p1++){
			for(int p2=p1+1; p2<matchOffsets.size(); p2++){
				long pos1 = matchOffsets.get(p1);
				long pos2 = matchOffsets.get(p2);
				long div = ggt(pos1, pos2);
				ggtResultsScored.computeIfAbsent(div, (k) -> new long[]{0, pos1})[0]++;
			}
		}
		Map.Entry<Long, long[]> best = null;
		for(Map.Entry<Long, long[]> e : ggtResultsScored.entrySet()){
			if(best == null || e.getValue()[0] > best.getValue()[0]){
				best = e;
			}
		}
		
		if(best == null) return null;
		return new long[]{best.getKey(), best.getValue()[1]};
	}
	
	private static long ggt(long a, long b){
		long c = a - b;
		if(a == 0 || b == 0 || c == 0) {
			return 0;
		}
		do{
			c = a;
			a = b;
			b = c % a;
		}while(b != 0);
		return a;
	}
	

	
	private static void compareDrives(UI ui, ReadableSource source1, ReadableSource source2, File logFile) {
		try{
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(logFile), 4096);
			try {
				long cnt1 = source1.numSectors();
				long cnt2 = source2.numSectors();
				bos.write((
						"Source1.numSectors = " + cnt1 + "\r\n"
					+	"Source2.numSectors = " + cnt2 + "\r\n"
					).getBytes(StandardCharsets.UTF_8));
				if (cnt1 != cnt2) {
					bos.write((
							"Source1.numSectors MUST be Source2.numSectors! -> exit."
						).getBytes(StandardCharsets.UTF_8));
					return;
				}
				compareDrivesDetailed(ui, source1, source2, bos);
				//compareDrivesSimple(ui, source1, source2, bos);
			} finally {
				bos.close();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	private static void compareDrivesSimple(UI ui, ReadableSource source1, ReadableSource source2, BufferedOutputStream bos) throws IOException {
		long size = source1.numSectors();
		byte[] sector1 = new byte[512];
		byte[] sector2 = sector1.clone();
		long diffCount = 0;
		long lastRedraw = 0;
		long lastPos = 0;
		boolean isEqualRegion = true;
		for (long pos = 0; pos < size; pos++) {
			long t = System.currentTimeMillis();
			if (t - 250 >= lastRedraw) {
				lastRedraw = t;
				ui.showInfo(new String[] {
					"Scanner Location: " + pos + " / " + size + "   " + (Math.floor(10000D * pos / size) / 100) + " %",
					"diffCount: " + diffCount
				}, false);
			}
			
			source1.readSector(pos, sector1);
			source2.readSector(pos, sector2);
			
			boolean isEqual = Arrays.equals(sector1, sector2);
			if(isEqual != isEqualRegion){
				long last = pos - 1;
				String logMsg;
				if(last < lastPos){
					logMsg = "Single " + (isEqualRegion ? "Equal" : "Invalid") + " sector at " + lastPos + "\r\n";
				} else {
					logMsg = (isEqualRegion ? "Equal" : "Invalid") + " region from " + lastPos + " to " + last + "\r\n";
				}
				bos.write(logMsg.getBytes(StandardCharsets.UTF_8));
				isEqualRegion = isEqual;
				lastPos = pos;
			}
			if(!isEqual){
				diffCount++;
			}
		}
		long last = size - 1;
		String logMsg;
		if(last < lastPos){
			logMsg = "Single " + (isEqualRegion ? "Equal" : "Invalid") + " sector at " + lastPos + "\r\n";
		} else {
			logMsg = (isEqualRegion ? "Equal" : "Invalid") + " region from " + lastPos + " to " + last + "\r\n";
		}
		bos.write(logMsg.getBytes(StandardCharsets.UTF_8));
	}
	
	private static void compareDrivesDetailed(UI ui, ReadableSource source1, ReadableSource source2, BufferedOutputStream bos) throws IOException {
		long size = source1.numSectors();
		byte[] sector1 = new byte[512];
		byte[] sector2 = sector1.clone();
		byte[] xorDiff = sector1.clone();
		long diffCount = 0;
		long lastRedraw = 0;
		for (long pos = 0; pos < size; pos++) {
			long t = System.currentTimeMillis();
			if (t - 250 >= lastRedraw) {
				lastRedraw = t;
				ui.showInfo(new String[] {
					"Scanner Location: " + pos + " / " + size + "   " + (Math.floor(10000D * pos / size) / 100) + " %",
					"diffCount: " + diffCount
				}, false);
			}
			
			source1.readSector(pos, sector1);
			source2.readSector(pos, sector2);
			for(int i=0; i<512; i++) xorDiff[i] = (byte)(sector1[i] ^ sector2[i]);
			if (ByteArrayUtils.isEmptySector(xorDiff)) {
				continue;
			} else {
				int numDifferingBytes = 0;
				for(int i=0; i<512; i++) if(xorDiff[i] != 0) numDifferingBytes++;
				boolean areOnlyBitflips = true;
				for(int i=0; i<512; i++) {
					if(Integer.bitCount(xorDiff[i] & 0xFF) > 1) {
						areOnlyBitflips = false;
						break;
					}
				}
				
				if(numDifferingBytes > 4 && !areOnlyBitflips){
					String d1 = DumpUtils.binaryDumpToString(sector1);
					String d2 = DumpUtils.binaryDumpToString(sector2);
					bos.write((
						"Diff at sector " + pos + ": numDifferingBytes = " + numDifferingBytes + "\r\n"
						+ "----- Source 1: -----\r\n"
						+ d1
						+"\r\n"
						+ "----- Source 2: -----\r\n"
						+ d2
						+"\r\n"
					).getBytes(StandardCharsets.UTF_8));
				} else {
					StringBuilder sb = new StringBuilder(1024);
					if(areOnlyBitflips){
						sb.append("Diff at sector ").append(pos)
							.append(": numDifferingBytes = ").append(numDifferingBytes)
							.append("\r\n  FROM   |    TO    |   XOR    | pos in sector");
						for(int i=0; i<512; i++){
							if(xorDiff[i] != 0){
								int b1 = 256 | (sector1[i] & 0xFF);
								int b2 = 256 | (sector2[i] & 0xFF);
								sb.append("\r\n")
								.append(Integer.toBinaryString(b1).substring(1))
								.append(" | ")
								.append(Integer.toBinaryString(b2).substring(1))
								.append(" | ")
								.append(Integer.toBinaryString(256 | (xorDiff[i] & 0xFF)).substring(1))
								.append(" | ").append(i);
							}
						}
					} else {
						sb.append("Diff at sector ").append(pos)
							.append(": numDifferingBytes = ").append(numDifferingBytes)
							.append("\r\n  FROM   |    TO    |   XOR    | from |  to  | pos in sector");
						for(int i=0; i<512; i++){
							if(xorDiff[i] != 0){
								int b1 = 256 | (sector1[i] & 0xFF);
								int b2 = 256 | (sector2[i] & 0xFF);
								sb.append("\r\n")
								.append(Integer.toBinaryString(b1).substring(1))
								.append(" | ")
								.append(Integer.toBinaryString(b2).substring(1))
								.append(" | ")
								.append(Integer.toBinaryString(256 | (xorDiff[i] & 0xFF)).substring(1))
								.append(" | 0x")
								.append(Integer.toHexString(b1).substring(1).toUpperCase())
								.append(" | 0x")
								.append(Integer.toHexString(b2).substring(1).toUpperCase())
								.append(" | ").append(i);
							}
						}
					}
					bos.write(sb.append("\r\n\r\n").toString().getBytes(StandardCharsets.UTF_8));
				}
				diffCount++;
			}
		}
	}
}
