package me.kaigermany.opendiskdiver.datafilesystem;

import java.io.InputStream;

public abstract class FileEntry {
	public final String name;
	public final String nameAndPath;
	public final long size;
	public final long age;
	
	public FileEntry(String name, String nameAndPath, long size, long age){
		this.name = name;
		this.nameAndPath = nameAndPath;
		this.size = size;
		this.age = age;
	}
	
	public abstract InputStream openInputStream();
}
