package me.kaigermany.opendiskdiver.datafilesystem;

import java.io.InputStream;

public abstract class FileEntry {
	public final String name;
	public final String nameAndPath;
	public final long size;
	public final long age;
	public final boolean isDeleted;
	
	public FileEntry(String name, String nameAndPath, long size, long age, boolean isDeleted){
		this.name = name;
		this.nameAndPath = nameAndPath;
		this.size = size;
		this.age = age;
		this.isDeleted = isDeleted;
	}
	
	public abstract InputStream openInputStream();
}
