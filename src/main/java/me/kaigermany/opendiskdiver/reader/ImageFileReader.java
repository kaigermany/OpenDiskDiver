package me.kaigermany.opendiskdiver.reader;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ImageFileReader implements ReadableSource, Closeable {
	private final FileInputStream fis;
	private final FileChannel fc;
	private final long numSectors;
	
	public ImageFileReader(File file) throws FileNotFoundException {
		fis = new FileInputStream(file);
		fc = fis.getChannel();
		numSectors = file.length() / 512;
	}
	
	@Override
	public void close() throws IOException {
		fis.close();
	}

	@Override
	public void readSectors(long sectorNumber, int sectorCount, byte[] buffer, int bufferOffset) throws IOException {
		fc.position(sectorNumber * 512);
		fc.read(ByteBuffer.wrap(buffer, bufferOffset, buffer.length - bufferOffset));
	}
	
	@Override
	protected void finalize() throws Throwable {
		fis.close();
	}

	@Override
	public long numSectors() {
		return numSectors;
	}
}
