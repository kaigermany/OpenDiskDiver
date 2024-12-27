package me.kaigermany.opendiskdiver.data;

import java.io.IOException;

import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.writer.Writer;

public interface CopyActivity {
	void onCopy(ReadableSource reader, Writer writer) throws IOException;
}
