package me.kaigermany.opendiskdiver.reader;

import java.io.IOException;

public class LimitedReadableSource implements ReadableSource {
	private ReadableSource source;
	private long offset, len;
	
	public LimitedReadableSource(ReadableSource source, long offset, long len) {
		this.source = source;
		this.offset = offset;
		this.len = len;
	}

	@Override
	public void readSectors(long sectorNumber, int sectorCount, byte[] buffer, int bufferOffset) throws IOException {
		//if((sectorNumber + offset) * 512 + (buffer.length - bufferOffset) > (offset + len) * 512) throw new IOException("Invalid read: outside of Partition!");
		if(sectorNumber + sectorCount  > len) throw new IOException("Invalid read: outside of Partition!");
		source.readSectors(sectorNumber + offset, sectorCount, buffer, bufferOffset);
	}

	@Override
	public long numSectors() {
		return len;
	}
}
