package me.kaigermany.opendiskdiver.writer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class DirectDiskWriter implements Writer {
	private RandomAccessFile raf;
	private long numSectors;
	
	private long writeOffset;
	
	public DirectDiskWriter(String path, long numBytes) throws FileNotFoundException {
		raf = new RandomAccessFile(path, "rwd");
		numSectors = numBytes / 512;
	}
	
	@Override
	public void create(File file, ReadableSource readerReference) throws IOException {}

	@Override
	public void write(byte[] buf, int numBytes) throws IOException {
		if(writeOffset + numBytes > numSectors * 512){
			throw new IOException("Out of range: capacity: " + (numSectors * 512) + ", writeOffset + numBytes = " + (writeOffset + numBytes));
		}
		raf.seek(writeOffset);
		raf.write(buf, 0, numBytes);
		writeOffset += numBytes;
	}

	@Override
	public void writePlaceholderSector() throws IOException {
		write(new byte[512], 512);
	}

	@Override
	public void close() throws IOException {
		if(raf != null){
			raf.close();
			raf = null;
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		close();
		super.finalize();
	}
}
