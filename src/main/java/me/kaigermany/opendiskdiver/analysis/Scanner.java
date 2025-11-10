package me.kaigermany.opendiskdiver.analysis;

import java.util.ArrayList;

public interface Scanner {
	void scan(byte[] nextSector, long sectorOffset, ArrayList<String> log);
}
