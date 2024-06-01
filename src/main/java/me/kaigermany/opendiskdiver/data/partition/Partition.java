package me.kaigermany.opendiskdiver.data.partition;

import java.io.IOException;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class Partition {
	public final boolean isGPT;
	public final long offset, len;// defined as sector (512 byte) , this is NOT A BYTE OFFSET!
	public final int type;
	public final String name;
	public final ReadableSource source;
	
	public Partition(int offset, int len, int type, ReadableSource source) {
		isGPT = type == 238 && len == -1;
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
	
	public static class LimitedReadableSource implements ReadableSource {
		private ReadableSource source;
		private long offset, len;
		public LimitedReadableSource(ReadableSource source, long offset, long len) {
			this.source = source;
			this.offset = offset;
			this.len = len;
		}

		@Override
		public void readSectors(long sectorNumber, int sectorCount, byte[] buffer, int bufferOffset) throws IOException {
			if((sectorNumber + offset) * 512 + (buffer.length - bufferOffset) > (offset + len) * 512) throw new IOException("Invalid read: outside of Partition!");
			source.readSectors(sectorNumber + offset, sectorCount, buffer, bufferOffset);
		}
		
	}
}
