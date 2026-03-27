package me.kaigermany.opendiskdiver.writer;

import java.io.File;
import java.io.IOException;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class ImageFileWriter implements Writer {
	public ImageFileWriter(){}
	
	private DirectFileOutputStream outputStream;

	@Override
	public void create(File file, ReadableSource readerReference) throws IOException {
		outputStream = new DirectFileOutputStream(file);
	}

	@Override
	public void write(byte[] buf, int numBytes) throws IOException {
		outputStream.write(buf, 0, numBytes);
	}

	@Override
	public void writePlaceholderSector() throws IOException {
		write(new byte[512], 512);
	}

	@Override
	public void close() throws IOException {
		outputStream.close();
		outputStream = null;
	}
}
