package me.kaigermany.opendiskdiver.data.fat;

import java.io.IOException;

import me.kaigermany.opendiskdiver.data.fat.FatReader.FatType;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;

public class FAT_BootSector {
	public final int rootClustorNumber;
	public final int bytes_per_sector;
	public final int sectors_per_clustor;
	public final int reserved_sectors;
	public final int FAT_copys;
	
	public final long fatSz;
	public final long clusterCount;
	public final FatType type;
	public final long FirstDataSector;
	
	int rootDirSectors;
	
	public FAT_BootSector(byte[] bootSector) throws IOException {
		String oemName = new String(bootSector, 3, 8);
		System.out.println("oemName: " + oemName);
		int rootClustorNumberTemp = (int) ByteArrayUtils.readLittleEndian(bootSector, 44, 4);
		bytes_per_sector = (int) ByteArrayUtils.readLittleEndian(bootSector, 11, 2);
		sectors_per_clustor = (int) ByteArrayUtils.readLittleEndian(bootSector, 13, 1);
		reserved_sectors = (int) ByteArrayUtils.readLittleEndian(bootSector, 14, 2);
		FAT_copys = (int) ByteArrayUtils.readLittleEndian(bootSector, 16, 1);
		int mediaDescriptor = bootSector[21] & 0xFF;
		int sectors_per_track = (int) ByteArrayUtils.readLittleEndian(bootSector, 24, 2);
		int headCount = (int) ByteArrayUtils.readLittleEndian(bootSector, 26, 2);
		int hidden_sectors = (int) ByteArrayUtils.readLittleEndian(bootSector, 28, 2);
		// println(readLiddleEndian(readData(dn, 17, 2)));
		int root_dir_entries = (int) ByteArrayUtils.readLittleEndian(bootSector, 17, 2);
		int sectors_per_FAT = (int) ByteArrayUtils.readLittleEndian(bootSector, 22, 2);
		
		String mediaTypeName = null;
		switch (mediaDescriptor) {
			case 0xFF: mediaTypeName = "MSDOS 1.1, 5 1/4 floppy, 320KB"; break;
			case 0xFE: mediaTypeName = "MSDOS 1.0, 5 1/4 floppy, 160KB"; break;
			case 0xFD: mediaTypeName = "MSDOS 2.0, 5 1/4 floppy, 360KB"; break;
			case 0xFC: mediaTypeName = "MSDOS 2.0, 5 1/4 floppy, 180KB"; break;
			case 0xF9: mediaTypeName = "MSDOS 3.2, 3 1/2 floppy, 720KB"; break;
			case 0xF8: mediaTypeName = "MSDOS 2.0, Any Hard Drive"; break;
			case 0xF0: mediaTypeName = "MSDOS 3.3, 3 1/2 floppy, 1.44MB"; break;
			default: mediaTypeName = "Unknown"; break;
		}
		
		System.out.println("mediaTypeName: " + mediaTypeName);
		System.out.println("rootClustorNumber: " + rootClustorNumberTemp);
		System.out.println("bytes_per_sector: " + bytes_per_sector); // !!!
		System.out.println("sectors_per_clustor: " + sectors_per_clustor);
		System.out.println("reserved_sectors: " + reserved_sectors);
		System.out.println("FAT_copys: " + FAT_copys);
		System.out.println("sectors_per_track: " + sectors_per_track);
		System.out.println("headCount: " + headCount);
		System.out.println("hidden_sectors: " + hidden_sectors);
		System.out.println("root_dir_entries: " + root_dir_entries);
		System.out.println("sectors_per_FAT: " + sectors_per_FAT);

		int total16 = (int) ByteArrayUtils.readLittleEndian(bootSector, 19, 2);
		int total32 = (int) ByteArrayUtils.readLittleEndian(bootSector, 32, 4);
		int fatSz32 = (int) ByteArrayUtils.readLittleEndian(bootSector, 36, 4);

		long totalSectors = total16 == 0 ? total32 : total16;
		fatSz = (sectors_per_FAT == 0 ? fatSz32 : sectors_per_FAT) & 0xFFFFFFFFL;
		System.out.println("fatSz=" + fatSz);
		rootDirSectors = (root_dir_entries * 32 + (bytes_per_sector - 1)) / bytes_per_sector;
		// if((root_dir_entries * 32 + (bytes_per_sector - 1)) > 0)
		// rootDirSectors++;
		System.out.println("rootDirSectors=" + rootDirSectors);
		//FirstDataSector = reserved_sectors + (FAT_copys * fatSz) + rootDirSectors;
		FirstDataSector = ((0-2) * sectors_per_clustor) + (reserved_sectors + (FAT_copys * fatSz) + rootDirSectors);
		long dataSectors = totalSectors - FirstDataSector;
		System.out.println("FirstDataSector=" + FirstDataSector);
		clusterCount = dataSectors / sectors_per_clustor;
		System.out.println("clusterCount: " + clusterCount);
		
		type = FatType.getTypeByClusterCount(clusterCount);
		
		if(type != FatType.FAT32){
			//FirstRootDirSecNum = BPB_ResvdSecCnt + (BPB_NumFATs * BPB_FATSz16);
			rootClustorNumberTemp = reserved_sectors + (FAT_copys * sectors_per_FAT);
			System.out.println("recalculated rootClustorNumber (because FS-Type != FAT32): " + rootClustorNumberTemp);
		}
		rootClustorNumber = rootClustorNumberTemp;
	}
}
