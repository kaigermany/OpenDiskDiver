package me.kaigermany.opendiskdiver.analysis;

import java.util.function.BiConsumer;

public interface Scanner {
	void scan(byte[] nextSector, long sectorOffset, BiConsumer<Scanner, String> logger);
}
