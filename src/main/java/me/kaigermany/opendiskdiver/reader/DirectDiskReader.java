package me.kaigermany.opendiskdiver.reader;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class DirectDiskReader implements ReadableSource, Closeable {
	private RandomAccessFile raf;
	
	public DirectDiskReader(String path) throws FileNotFoundException {
		raf = new RandomAccessFile(path, "r");
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
}
