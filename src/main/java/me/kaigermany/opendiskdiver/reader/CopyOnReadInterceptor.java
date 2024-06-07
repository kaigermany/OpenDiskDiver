package me.kaigermany.opendiskdiver.reader;

import java.io.IOException;

public class CopyOnReadInterceptor implements ReadableSource {
	
	public static interface ReadAcceptor{
		//if the read was successful, the data in buffer at bufferOffset ..  sectorCount*512 is now valid data.
		void onRead(long sectorNumber, int sectorCount, byte[] buffer, int bufferOffset);
	}
	
	private ReadableSource source;
	private ReadAcceptor readAcceptor;
	
	public CopyOnReadInterceptor(ReadableSource source, ReadAcceptor readAcceptor){
		this.source = source;
		this.readAcceptor = readAcceptor;
	}
	
	public void readSector(long sectorNumber, byte[] buffer) throws IOException {
		readSector(sectorNumber, buffer, 0);
	}
	
	public void readSectors(long sectorNumber, int sectorCount, byte[] buffer) throws IOException {
		readSectors(sectorNumber, sectorCount, buffer, 0);
	}
	
	public void readSector(long sectorNumber, byte[] buffer, int bufferOffset) throws IOException {
		readSectors(sectorNumber, 1, buffer, 0);
	}
	
	public void readSectors(long sectorNumber, int sectorCount, byte[] buffer, int bufferOffset) throws IOException {
		source.readSectors(sectorNumber, sectorCount, buffer, bufferOffset);
		readAcceptor.onRead(sectorNumber, sectorCount, buffer, bufferOffset);
	}

	@Override
	public long numSectors() {
		return source.numSectors();
	}
}
