package me.kaigermany.opendiskdiver.writer;

import java.io.File;
import java.io.IOException;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class ImageFileWriter implements Writer {
	public static void write(ReadableSource reader, File out) throws IOException {
		DirectFileOutputStream fos = new DirectFileOutputStream(out);
		byte[] buf = new byte[1 << 20];
		int maxLen = (1 << 20) / 512;
		long pos = 0;
		long maxPos = reader.numSectors();
		while(pos < maxPos){
			int numSectorsToRead = (int)Math.min(maxPos - pos, maxLen);
			reader.readSectors(pos, numSectorsToRead, buf, 0);
			fos.write(buf, 0, numSectorsToRead * 512);
			pos += numSectorsToRead;
		}
		fos.close();
	}
	
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
	public void close() throws IOException {
		outputStream.close();
		outputStream = null;
	}
}
