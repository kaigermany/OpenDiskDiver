package me.kaigermany.opendiskdiver.data.ntfs;

import java.io.IOException;

import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;

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
		long Signature = ByteArrayUtils.read64(buffer, 3);
		if(Signature != 0x202020205346544EL) throw new IOException("No NTFS format detected!");
		//System.out.println("Signature:" + Long.toString(Signature, 16));
		BytesPerSector = ByteArrayUtils.read16(buffer, 11);
		int sectorCount = ByteArrayUtils.read8(buffer, 13);
		clusterSize = BytesPerSector * sectorCount;
		long TotalSectors = ByteArrayUtils.read64(buffer, 40);
		if(TotalSectors < 0) {		//math magic to prevent -a/b -effect.
			TotalSectors >>>= 1;	//...but i am pretty sure it wont get used the next few decades xD
			sectorCount >>>= 1;
		}
		TotalClustors = TotalSectors / sectorCount;
		
		MFT_Offset = ByteArrayUtils.read64(buffer, 48);
		MFTMirror_Offset = ByteArrayUtils.read64(buffer, 56);
		ClustersPerMftRecord = ByteArrayUtils.read32(buffer, 64);
		ClustersPerIndexRecord = ByteArrayUtils.read32(buffer, 68);
		
		if (ClustersPerMftRecord >= 128){
			BytesPerMftRecord = ((long)1 << (byte)(256 - ClustersPerMftRecord));
		} else {
			BytesPerMftRecord = ClustersPerMftRecord * clusterSize;
		}
	}
}
