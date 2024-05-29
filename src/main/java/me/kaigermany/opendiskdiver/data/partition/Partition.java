package me.kaigermany.opendiskdiver.data.partition;

public class Partition {
	public final boolean isGPT;
	public final long offset, len;// defined as sector (512 byte) , this is NOT A BYTE OFFSET!
	public final int type;
	public final String name;

	public Partition(int offset, int len, int type) {
		isGPT = type == 238 && len == -1;
		this.offset = offset;
		this.len = len;
		this.type = type;
		this.name = null;
	}

	public Partition(long offset, long len, String name) {
		this.isGPT = true;
		this.offset = offset;
		this.len = len;
		this.type = -1;
		this.name = name;
	}

	@Override
	public String toString() {
		return "[offset: " + offset + ",\t len: " + len + ",\t isGPT: " + isGPT + ",\t type: " + type + ",\t name: " + name + "]";
	}
}
