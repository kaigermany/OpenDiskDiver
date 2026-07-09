package me.kaigermany.opendiskdiver.reader;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import me.kaigermany.opendiskdiver.writer.DirectDiskWriter;

public class DirectDiskReader implements ReadableSource, Closeable {
	private final String path;
	private RandomAccessFile raf;
	private long numSectors;
	public DirectDiskReader(String path, long numBytes) throws FileNotFoundException {
		this.path = path;
		raf = new RandomAccessFile(path, "r");
		numSectors = numBytes / 512;
	}
	
	@Override
	public void readSectors(long sectorNumber, int sectorCount, byte[] buffer, int bufferOffset) throws IOException {
		raf.seek(sectorNumber * 512);
		raf.read(buffer, bufferOffset, sectorCount * 512);
	}

	@Override
	public void close() throws IOException {
		raf.close();
		raf = null;
	}
	
	@Override
	protected void finalize() throws Throwable {
		if(raf != null) raf.close();
	}

	@Override
	public long numSectors() {
		return numSectors;
	}
	
	public DirectDiskWriter openWriter() throws FileNotFoundException {
		return new DirectDiskWriter(path, numSectors * 512);
	}
}
