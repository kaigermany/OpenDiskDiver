package me.kaigermany.opendiskdiver.writer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class ImageFileWriter implements Writer {
	public ImageFileWriter(){}
	
	private OutputStream outputStream;

	@Override
	public void create(File file, ReadableSource readerReference) throws IOException {
		if(file.toString().equals("/dev/null") || file.toString().endsWith("\\dev\\null")){
			//create a dummy instance that ignores ANY interaction.
			outputStream = new OutputStream() {
				@Override
				public void write(int ignoreMe) throws IOException {}
				@Override
				public void write(byte[] ignoreThisBuffer) throws IOException {}
				@Override
				public void write(byte[] ignoreThisBuffer, int ignoreThisOffset, int ignoreThisLength) throws IOException {}
			};
		} else {
			outputStream = new DirectFileOutputStream(file);
		}
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
