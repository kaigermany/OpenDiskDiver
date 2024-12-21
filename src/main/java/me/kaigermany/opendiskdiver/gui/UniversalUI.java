package me.kaigermany.opendiskdiver.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.DriveListProvider;
import me.kaigermany.opendiskdiver.data.DriveInfo;
import me.kaigermany.opendiskdiver.reader.ImageFileReader;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.reader.ZipFileReader;
import me.kaigermany.opendiskdiver.windows.SelectDriveGui;

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
}
