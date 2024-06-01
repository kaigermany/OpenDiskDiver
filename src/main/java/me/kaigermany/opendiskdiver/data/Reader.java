package me.kaigermany.opendiskdiver.data;

import java.io.IOException;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public interface Reader {
	void read(ReadableSource source) throws IOException;
}
