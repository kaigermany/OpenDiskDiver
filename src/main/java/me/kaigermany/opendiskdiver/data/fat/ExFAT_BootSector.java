package me.kaigermany.opendiskdiver.data.fat;

import java.io.IOException;

import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;

public class ExFAT_BootSector {
	public final long VolumeLength;//in local sectors.
	public final int FatOffset;//in local sectors.
	public final int FatLength;//in local sectors.
	public final int NumberOfFats;
	
	public final int BytesPerSector;// == local sector size.
	public final int SectorsPerCluster;
	
	public final long dataOffset;
	public final int ClusterHeapOffset;
	public final int ClusterCount;
	public final int FirstClusterOfRootDirectory;
	
	public ExFAT_BootSector(byte[] bootSector) throws IOException {
		int BytesPerSectorShift = bootSector[108] & 0xFF; //allowed is 9 (512) .. 12 (4096) ONLY.
		int bytesPerSectorTemp = 1 << BytesPerSectorShift;
		int SectorsPerClusterShift = bootSector[109] & 0xFF;
		int sectorsPerClusterTemp = 1 << SectorsPerClusterShift;
		NumberOfFats = bootSector[110] & 0xFF;	//allowed is 1 or 2 ONLY.
		VolumeLength = ByteArrayUtils.readLittleEndian(bootSector, 72, 8);
		int fatOffsetTemp = (int) ByteArrayUtils.readLittleEndian(bootSector, 80, 4);
		int fatLengthTemp = (int) ByteArrayUtils.readLittleEndian(bootSector, 84, 4);
		
		int clusterHeapOffsetTemp = (int) ByteArrayUtils.readLittleEndian(bootSector, 88, 4);
		ClusterCount = (int) ByteArrayUtils.readLittleEndian(bootSector, 92, 4);
		FirstClusterOfRootDirectory = (int) ByteArrayUtils.readLittleEndian(bootSector, 96, 4);
		
		if(bytesPerSectorTemp != 512){//normalize sector size to make further processing steps way easier.
			int scaler = bytesPerSectorTemp / 512;
			bytesPerSectorTemp = 512;
			sectorsPerClusterTemp *= scaler;
			fatOffsetTemp *= scaler;
			fatLengthTemp *= scaler;
		}
		
		dataOffset = fatOffsetTemp + fatLengthTemp * NumberOfFats;
		BytesPerSector = bytesPerSectorTemp;
		SectorsPerCluster = sectorsPerClusterTemp;
		ClusterHeapOffset = clusterHeapOffsetTemp - sectorsPerClusterTemp * 2;
		FatOffset = fatOffsetTemp;
		FatLength = fatLengthTemp;
	}
}
