package me.kaigermany.opendiskdiver.writer;

import java.io.File;
import java.io.IOException;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class ImageFileWriter {
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
	/*
	 * 
java.io.IOException: Invalid read: outside of Partition!
	at me.kaigermany.opendiskdiver.reader.LimitedReadableSource.readSectors(LimitedReadableSource.java:17)
	 */
}
