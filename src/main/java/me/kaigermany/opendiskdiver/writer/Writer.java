package me.kaigermany.opendiskdiver.writer;

import java.io.File;
import java.io.IOException;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public interface Writer {
	void create(File file, ReadableSource readerReference) throws IOException;
	void write(byte[] buf, int numBytes) throws IOException;
	void close() throws IOException;
}
