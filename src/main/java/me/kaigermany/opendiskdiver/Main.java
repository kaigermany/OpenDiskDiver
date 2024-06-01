package me.kaigermany.opendiskdiver;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import me.kaigermany.opendiskdiver.data.DriveInfo;
import me.kaigermany.opendiskdiver.data.ntfs.NtfsNode;
import me.kaigermany.opendiskdiver.data.ntfs.NtfsReader;
import me.kaigermany.opendiskdiver.data.partition.Partition;
import me.kaigermany.opendiskdiver.data.partition.PartitionReader;
import me.kaigermany.opendiskdiver.gui.Screen;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.windows.SelectDriveGui;
import me.kaigermany.opendiskdiver.windows.WindowsDrives;
import me.kaigermany.opendiskdiver.windows.console.ConsoleInterface;

public class Main {
	private static ConsoleInterface ci;
	
	public static void main(String[] args) {
		testNTFS();
		//System.out.println(SelectDriveGui.toHumanReadableFileSize(1072693248));
		ci = new ConsoleInterface();
		Screen screen = new Screen(1, 1, ci);
		DriveInfo drive = new SelectDriveGui(screen, ci).awaitSelection();
		
		try {
			
			
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void testNTFS(){
		int driveIndex = 1;
		int partitionIndex = 1;
		
		try{
			DriveInfo drive = WindowsDrives.listDrives().get(driveIndex);
			ReadableSource source = drive.openReader();
			PartitionReader r = new PartitionReader(source);
			for(Partition p : r.getPartitions()){
				System.out.println(p);
			}
			NtfsReader ntfs = new NtfsReader(r.getPartitions().get(partitionIndex).source);
			/*
			for(String file : ntfs.fileMap.keySet()){
				if(file != null && file.endsWith(".txt")){
					System.out.println(file);
				}
			}*/
			NtfsNode node = ntfs.fileMap.get("Local\\Local\\Atlassian\\SourceTree\\git_local\\mingw32\\share\\doc\\connect\\manual.txt");
			InputStream is = node.openInputStream();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] a = new byte[1024];
			int l;
			while((l = is.read(a)) != -1){
				baos.write(a, 0, l);
			}
			System.out.println(new String(baos.toByteArray()));
		}catch(Exception e){
			e.printStackTrace();
		}
	}

}
