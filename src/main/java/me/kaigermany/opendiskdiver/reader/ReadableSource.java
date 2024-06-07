package me.kaigermany.opendiskdiver.reader;

import java.io.IOException;

public interface ReadableSource {
	default void readSector(long sectorNumber, byte[] buffer, int bufferOffset) throws IOException {
		readSectors(sectorNumber, 1, buffer, 0);
	}
	
	default void readSector(long sectorNumber, byte[] buffer) throws IOException {
		readSectors(sectorNumber, 1, buffer, 0);
	}
	
	default void readSectors(long sectorNumber, int sectorCount, byte[] buffer) throws IOException {
		readSectors(sectorNumber, sectorCount, buffer, 0);
	}
	
	//highly recommended to implement this method, too.
	void readSectors(long sectorNumber, int sectorCount, byte[] buffer, int bufferOffset) throws IOException;
	
	long numSectors();
}
