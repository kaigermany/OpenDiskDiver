package me.kaigermany.opendiskdiver;

import java.util.ArrayList;

import me.kaigermany.opendiskdiver.data.Reader;
import me.kaigermany.opendiskdiver.data.fat.FatReader;
import me.kaigermany.opendiskdiver.data.ntfs.NtfsReader;
import me.kaigermany.opendiskdiver.data.partition.Partition;
import me.kaigermany.opendiskdiver.data.partition.PartitionReader;
import me.kaigermany.opendiskdiver.gui.Screen;
import me.kaigermany.opendiskdiver.probe.Probe;
import me.kaigermany.opendiskdiver.probe.ProbeFunction;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;
import me.kaigermany.opendiskdiver.windows.SelectDriveGui;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface;

public class Main {
	private static ConsoleInterface ci;
	
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
		ci = new ConsoleInterface();
		Screen screen = new Screen(1, 1, ci);
		
		try {
			ReadableSource source = new SelectDriveGui(screen, ci).awaitSelection();
			PartitionReader r = new PartitionReader(source);
			ArrayList<Partition> partitions = r.getPartitions();
			for(Partition p : partitions){
				System.out.println(p);
			}
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
	
}
