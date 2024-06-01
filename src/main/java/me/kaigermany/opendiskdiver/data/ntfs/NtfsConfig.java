package me.kaigermany.opendiskdiver.data.ntfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class NtfsConfig {
	public final int BytesPerSector;
	public final int clusterSize;
	public final long TotalClustors;
	public final long MFT_Offset;
	public final long MFTMirror_Offset;
	public final int ClustersPerMftRecord;
	public final int ClustersPerIndexRecord;
	public final long BytesPerMftRecord;
	
	public NtfsConfig(ReadableSource source) throws IOException {
		byte[] buffer = new byte[512];
		source.readSector(0, buffer);
		
		long Signature = read64(buffer, 3);
		if(Signature != 0x202020205346544EL) throw new IOException("No NTFS format detected!");
		//System.out.println("Signature:" + Long.toString(Signature, 16));
		BytesPerSector = read16(buffer, 11);
		int sectorCount = read8(buffer, 13);
		clusterSize = BytesPerSector * sectorCount;
		long TotalSectors = read64(buffer, 40);
		if(TotalSectors < 0) {		//math magic to prevent -a/b -effect.
			TotalSectors >>>= 1;	//...but i am pretty sure it wont get used the next few decades xD
			sectorCount >>>= 1;
		}
		TotalClustors = TotalSectors / sectorCount;
		
		MFT_Offset = read64(buffer, 48);
		MFTMirror_Offset = read64(buffer, 56);
		ClustersPerMftRecord = read32(buffer, 64);
		ClustersPerIndexRecord = read32(buffer, 68);
		
		if (ClustersPerMftRecord >= 128){
			BytesPerMftRecord = ((long)1 << (byte)(256 - ClustersPerMftRecord));
		} else {
			BytesPerMftRecord = ClustersPerMftRecord * clusterSize;
		}
	}
	
	private static long read64(byte[] buffer, int offset) {
		return ByteBuffer.wrap(buffer, offset, 8).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get();
	}

	private static int read32(byte[] buffer, int offset) {
		return ByteBuffer.wrap(buffer, offset, 4).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get();
	}

	private static int read16(byte[] buffer, int offset) {
		return ByteBuffer.wrap(buffer, offset, 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get() & 0xFFFF;
	}

	private static int read8(byte[] buffer, int offset) {
		return buffer[offset] & 0xFF;
	}
}
