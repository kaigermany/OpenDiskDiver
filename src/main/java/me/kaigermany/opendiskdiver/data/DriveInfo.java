package me.kaigermany.opendiskdiver.data;

public class DriveInfo {
	public final String name;
	public final String path;
	public final long size;
	
	public DriveInfo(String name, String path, String size){
		this.name = name;
		this.path = path;
		this.size = size.length() == 0 ? -1 : Long.parseLong(size);
	}
	
	@Override
	public String toString() {
		return "{name="+name+",path="+path+",size="+size+"}";
	}
}
