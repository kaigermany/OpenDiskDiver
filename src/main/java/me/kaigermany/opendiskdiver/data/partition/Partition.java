package me.kaigermany.opendiskdiver.data.partition;

import me.kaigermany.opendiskdiver.reader.LimitedReadableSource;
import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class Partition {
	public final boolean isGPT;
	public final long offset, len;// defined as sector (512 byte) , this is NOT A BYTE OFFSET!
	public final int type;
	public final String name;
	public final ReadableSource source;
	
	public Partition(int offset, int len, int type, ReadableSource source) {
		isGPT = type == 238 && len < 0;
		this.offset = offset;
		this.len = len;
		this.type = type;
		this.name = null;
		this.source = createLimitedReader(source, offset, len);
	}

	public Partition(long offset, long len, String name, ReadableSource source) {
		this.isGPT = true;
		this.offset = offset;
		this.len = len;
		this.type = -1;
		this.name = name;
		this.source = createLimitedReader(source, offset, len);
	}

	@Override
	public String toString() {
		return "[offset: " + offset + ",\t len: " + len + ",\t isGPT: " + isGPT + ",\t type: " + type + ",\t name: " + name + "]";
	}

	private static ReadableSource createLimitedReader(ReadableSource source, long offset, long len) {
		return new LimitedReadableSource(source, offset, len);
	}
}
