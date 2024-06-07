package me.kaigermany.opendiskdiver.data.fat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;

import me.kaigermany.opendiskdiver.data.Reader;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;

public class FatReader implements Reader {
	private static final byte[] EXFAT_SIGNATURE = "EXFAT   ".getBytes();
	
	private static long readLittleEndian(byte[] a, int offset, int len) {
		if (a == null)
			return 0;
		long b = 0;
		for (int i = 0; i < len; i++) {
			b |= (a[offset + i] & 0xFF) << (i << 3);
		}
		return b;
	}
	
	public static boolean isFatFormated(byte[] bootSector){
		return bootSector[510] == (byte)0x55 && bootSector[511] == (byte)0xAA;
	}
	
	public static boolean isExFat(byte[] bootSector){
		for(int i=0; i<8; i++){
			if(bootSector[i + 3] != EXFAT_SIGNATURE[i]) return false;
		}
		return ByteArrayUtils.isEmpty(bootSector, 11, 53);
	}

	public static enum FatType{
		FAT12(0x0FFF),
		FAT16(0xFFFF),
		FAT32(0x0FFFFFFF);
		
		private int EOF_FLAG;
		
		FatType(int EOF_FLAG){
			this.EOF_FLAG = EOF_FLAG;
		}
		
		public int getEOF_FLAG(){
			return EOF_FLAG;
		}
		
		public int getEntrySize(){
			return 0;//TODO
		}
		
		public static FatType getTypeByClusterCount(long clusterCount){
			/*
			if(clusterCount > 65524L) return FAT32;
			else if(clusterCount > 4084L) return FAT16;
			else return FAT12;
			*/
			
			//based on https://averstak.tripod.com/fatdox/bootsec.htm
			if(clusterCount < 4087L) return FAT12;
			if(clusterCount < 65527L) return FAT16;
			if(clusterCount < 268435457L) return FAT32;
			return null;
		}
	}
	
	public static class FAT{//TODO
		
	}
		
	public static class ExFAT{//TODO
		
	}
	 
	public static class ExFAT_BootSector{//TODO
		long VolumeLength;//in local sectors.
		int FatOffset;//in local sectors.
		int FatLength;//in local sectors.
		int NumberOfFats;
		
		int BytesPerSector;// == local sector size.
		int SectorsPerCluster;
		
		public ExFAT_BootSector(byte[] bootSector) throws IOException {
			int BytesPerSectorShift = bootSector[108] & 0xFF; //allowed is 9 (512) .. 12 (4096) ONLY.
			BytesPerSector = 1 << BytesPerSectorShift;
			int SectorsPerClusterShift = bootSector[109] & 0xFF;
			SectorsPerCluster = 1 << SectorsPerClusterShift;
			NumberOfFats = bootSector[110] & 0xFF;
			VolumeLength = readLittleEndian(bootSector, 72, 8);
			FatOffset = (int) readLittleEndian(bootSector, 80, 4);
			FatLength = (int) readLittleEndian(bootSector, 84, 4);
		}
	}
	public static class FAT_BootSector{
		public int rootClustorNumber;
		public int bytes_per_sector;
		public int sectors_per_clustor;
		public int reserved_sectors;
		public int FAT_copys;
		
		public long fatSz;
		public long clusterCount;
		public FatType type;
		public long FirstDataSector;
		
		public FAT_BootSector(byte[] bootSector) throws IOException {
			String oemName = new String(bootSector, 3, 8);
			System.out.println("oemName: " + oemName);
			rootClustorNumber = (int) readLittleEndian(bootSector, 44, 4);
			bytes_per_sector = (int) readLittleEndian(bootSector, 11, 2);
			sectors_per_clustor = (int) readLittleEndian(bootSector, 13, 1);
			reserved_sectors = (int) readLittleEndian(bootSector, 14, 2);
			FAT_copys = (int) readLittleEndian(bootSector, 16, 1);
			int mediaDescriptor = bootSector[21] & 0xFF;
			int sectors_per_track = (int) readLittleEndian(bootSector, 24, 2);
			int headCount = (int) readLittleEndian(bootSector, 26, 2);
			int hidden_sectors = (int) readLittleEndian(bootSector, 28, 2);
			// println(readLiddleEndian(readData(dn, 17, 2)));
			int root_dir_entries = (int) readLittleEndian(bootSector, 17, 2);
			int sectors_per_FAT = (int) readLittleEndian(bootSector, 22, 2);
			
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
			
			System.out.println("rootClustorNumber: " + rootClustorNumber);
			System.out.println("bytes_per_sector: " + bytes_per_sector); // !!!
			System.out.println("sectors_per_clustor: " + sectors_per_clustor);
			System.out.println("reserved_sectors: " + reserved_sectors);
			System.out.println("FAT_copys: " + FAT_copys);
			System.out.println("sectors_per_track: " + sectors_per_track);
			System.out.println("headCount: " + headCount);
			System.out.println("hidden_sectors: " + hidden_sectors);
			System.out.println("root_dir_entries: " + root_dir_entries);
			System.out.println("sectors_per_FAT: " + sectors_per_FAT);

			int total16 = (int) readLittleEndian(bootSector, 19, 2);
			int total32 = (int) readLittleEndian(bootSector, 32, 4);
			int fatSz32 = (int) readLittleEndian(bootSector, 36, 4);

			long totalSectors = total16 == 0 ? total32 : total16;
			fatSz = (sectors_per_FAT == 0 ? fatSz32 : sectors_per_FAT) & 0xFFFFFFFFL;
			System.out.println("fatSz=" + fatSz);
			int rootDirSectors = (root_dir_entries * 32 + (bytes_per_sector - 1)) / bytes_per_sector;
			// if((root_dir_entries * 32 + (bytes_per_sector - 1)) > 0)
			// rootDirSectors++;
			System.out.println("rootDirSectors=" + rootDirSectors);
			long dataSectors = totalSectors - (reserved_sectors + FAT_copys * fatSz + rootDirSectors);
			FirstDataSector = reserved_sectors + (FAT_copys * fatSz) + rootDirSectors;
			System.out.println("FirstDataSector=" + FirstDataSector);
			clusterCount = dataSectors / sectors_per_clustor;
			System.out.println("clusterCount: " + clusterCount);
			
			type = FatType.getTypeByClusterCount(clusterCount);
		}
	}
	

	
	public FatReader() {}

	@Override
	public void read(ReadableSource source) throws IOException {//TODO optimize codeflow for ExFAT
		byte[] bootSector = new byte[512];
		source.readSector(0, bootSector);
		//DumpUtils.binaryDump(bootSector);
		if(ByteArrayUtils.isEmptySector(bootSector)){//alternative boot sector
			source.readSector(6, bootSector);
		}
		
		boolean isExFat = isExFat(bootSector);
		System.out.println("isExFat: " + isExFat);
		
		FAT_BootSector bootSectorContainer = new FAT_BootSector(bootSector);
		
		//boolean isFAT32 = clusterCount > 65524L;
		//boolean isFAT16 = clusterCount > 4084L & !isFAT32;
		// boolean isFAT12 = !isFAT16 & !isFAT32;

		System.out.println("FatType: " + bootSectorContainer.type);
		String volumeLabel = new String(bootSector, bootSectorContainer.type == FatType.FAT32 ? 71 : 43, 11);
		System.out.println("volumeLabel: " + volumeLabel);

		int bytesPerClustor = bootSectorContainer.bytes_per_sector * bootSectorContainer.sectors_per_clustor;

		int[][] fats = new int[bootSectorContainer.FAT_copys][];
		int getEntrySize = bootSectorContainer.type == FatType.FAT32 ? 32 : (bootSectorContainer.type == FatType.FAT16 ? 16 : 12);
		for (int i = 0; i < bootSectorContainer.FAT_copys; i++) {
			long offset = (bootSectorContainer.reserved_sectors * bootSectorContainer.bytes_per_sector) + (i * (bootSectorContainer.fatSz * bootSectorContainer.bytes_per_sector));
			fats[i] = readFAT(bootSectorContainer.clusterCount, source, getEntrySize, offset, bootSectorContainer.fatSz * bootSectorContainer.bytes_per_sector);
		}
		int[] rootClustors = readClusters(fats[0], bootSectorContainer.rootClustorNumber, bootSectorContainer.type);
		System.out.println(Arrays.toString(rootClustors));
		long offsetFix = (bootSectorContainer.FirstDataSector * bootSectorContainer.bytes_per_sector) - (bytesPerClustor * bootSectorContainer.rootClustorNumber);
		Dir dir = new Dir("/");
		ArrayList<FileEntry> files = new ArrayList<FileEntry>();
		readDir(source, rootClustors, bytesPerClustor, offsetFix, dir, files, fats[0], bootSectorContainer.type, true);
		
		/*
		FileOutputStream fos = new FileOutputStream(new File("H:/rawDump.txt"));
		fos.write(files.toString().replace("}, {", "},\n{").getBytes());
		fos.close();
		*/

		System.out.println(files.toString().replace("}, {", "},\n{"));

		/*
		 * { int[] clustors = readClusters(fats[0], 3628, isFAT32, isFAT16);
		 * byte[] container = new byte[clustors.length * bytesPerClustor];
		 * for(int i=0; i<clustors.length; i++) { raf.seek(clustors[i] *
		 * bytesPerClustor + offsetFix); raf.read(container, i *
		 * bytesPerClustor, bytesPerClustor); } //System.out.println(new
		 * String(container)); _saveBytes("recovered_track.mp3", container); }
		 */
		runClusterPointerMapAnalysis(fats[1], bootSectorContainer.type);

	}
	
	private void runClusterPointerMapAnalysis(int[] clusterMap, FatType type) {
		int EOF_FLAG = type.getEOF_FLAG() - 7;
		/*
		 * ArrayList<ArrayList<Long>> inverseClusterMap = new
		 * ArrayList<ArrayList<Long>>(clusterMap.length); for(int i=0;
		 * i<clusterMap.length; i++) inverseClusterMap.add(new
		 * ArrayList<Long>(4)); for(int i=0; i<clusterMap.length; i++) { int
		 * nextPos = clusterMap[i]; if(nextPos >= EOF_FLAG || nextPos == 0)
		 * continue; inverseClusterMap.get(nextPos).add((long)i); }
		 */
		System.out.println("clusterMap.length = " + clusterMap.length);
		// ArrayList<Long> unprocessedElements = new
		// ArrayList<Long>(clusterMap.length);
		HashMap<Integer, Integer> linkerList = new HashMap<Integer, Integer>(clusterMap.length * 2);
		HashMap<Integer, ArrayList<Long>> inverseLinkerList = new HashMap<Integer, ArrayList<Long>>(
				clusterMap.length * 2);
		for (int i = 0; i < clusterMap.length; i++) {
			int nextPos = clusterMap[i];
			if (nextPos >= EOF_FLAG || nextPos == 0){
				nextPos = -1;
			}
			linkerList.put(i, nextPos);
			ArrayList<Long> list = inverseLinkerList.get(nextPos);
			if (list == null){
				inverseLinkerList.put(nextPos, list = new ArrayList<Long>(4));
			}
			list.add((long) i);
		}
		// println(inverseLinkerList);
		ArrayList<Long> treeEnds = inverseLinkerList.get(-1);
		// inverseLinkerList.remove(-1);
		// println(inverseLinkerList);
		StringBuilder logger = new StringBuilder();
		for (Long lastTreeNode : treeEnds) {
			ArrayList<Long> treeRecord = new ArrayList<Long>();
			// treeRecord.add(lastTreeNode);
			while (lastTreeNode != null && lastTreeNode != -1) {
				treeRecord.add(0, lastTreeNode);
				ArrayList<Long> list = inverseLinkerList.get(lastTreeNode.intValue());
				if (list == null) {
					// println("missing list.");
					break;
				}
				if (list.size() != 1) {
					System.out.println("invalid list size: " + list.size());
					break;
				}
				lastTreeNode = list.get(0);
			}
			if (treeRecord.size() > 1)
				logger.append(treeRecord).append("\r\n");
		}
		/*
		try {
			FileOutputStream fos = new FileOutputStream(new File("H:/log.txt"));
			fos.write(logger.toString().getBytes());
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		*/
	}
	
	private static int[] readFAT(long clustorCount, ReadableSource source, int getEntrySize, long offset, long len) throws IOException {
		byte[] data = new byte[(int) (len)];

		System.out.println("fat size: " + data.length);
		source.readSectors(offset / 512, (int)(len / 512), data, 0);
		// long[] entries = new long[(int)(/*this.sectorCount*/sectsPerFat *
		// sectorSize / getEntrySize)];
		int[] entries = new int[(int) clustorCount/*
													 * (((long)data.length *
													 * 8) / getEntrySize)
													 */];
		System.out.println("entries: " + entries.length);
		for (int i = 0; i < entries.length; i++) {
			entries[i] = readVal(data, i, getEntrySize) & 0x0FFFFFFF;
			// entries[i] += firstDataClustorOffset;//resSectBytes / 512;
			// if(entries[i] != 0) System.out.println(entries[i]);
		} /*
			 * { int[] tmp = new int[256]; System.arraycopy(entries, 0, tmp,
			 * 0, tmp.length); System.out.println(Arrays.toString(tmp)); }
			 */
		return entries;
	}
	
	private static int readVal(byte[] buf, int index, int entryBits) {
		if (entryBits >= 16) {// 32, 16
			int bytes = entryBits / 8;
			return (int) readLittleEndian(buf, index * bytes, bytes);
		} else {// 12 only
			int doublewordOffset = index * 12 / 8;
			int val = (int) readLittleEndian(buf, index * doublewordOffset, 2);
			if ((index & 1) == 1) {
				val >>>= 4;
			}
			return val;
		}
	}

	private static int[] readClusters(int[] clustorMap, int clustor, FatType type) {
		int EOF_FLAG = type.getEOF_FLAG() - 7;
		ArrayList<Integer> list = new ArrayList<Integer>();
		int next = clustor;
		if (next >= clustorMap.length) {
			System.out.println("found invalid initial clustorMap offset: " + next + " (allowed = 0.." + (clustorMap.length - 1) + ")");
			return new int[0];
		}
		if (next < EOF_FLAG && next != 0)
			list.add(clustor);
		while ((next = clustorMap[next]) < EOF_FLAG && next != 0) {
			if (next >= clustorMap.length) {
				System.out.println("found invalid clustorMap offset: " + next + " (allowed = 0.." + (clustorMap.length - 1)
						+ ")");
				break;
			}
			// System.out.println("next: " + next + " " +
			// Integer.toHexString(next));
			// System.out.println("EOF_FLAG: " +
			// Integer.toHexString(EOF_FLAG));
			// System.out.println(next + " >= " + EOF_FLAG + " -> " + (next
			// >= EOF_FLAG));
			list.add(next);
		}
		int[] out = new int[list.size()];
		int i = 0;
		for (int a : list)
			out[i++] = a;
		return out;
	}
	
	
	private static void readDir(ReadableSource source, int[] clustors, int bytesPerClustor, long offsetFix, Dir dir,
			ArrayList<FileEntry> files, int[] clustorMap, FatType type, boolean readDetetedEntrys) throws IOException {
		byte[] container = new byte[clustors.length * bytesPerClustor];
		for (int i = 0; i < clustors.length; i++) {
			source.readSectors((clustors[i] * bytesPerClustor + offsetFix) / 512, bytesPerClustor / 512, container, i * bytesPerClustor);
		}
		// System.out.println(Arrays.toString(container));
		int entrySize = 32;
		int entryCount = clustors.length * bytesPerClustor / entrySize;
		/*
		 * { byte[] tmp = new byte[entrySize]; for(int i=0; i<entryCount;
		 * i++) { System.arraycopy(container, i*entrySize, tmp, 0,
		 * entrySize); System.out.println(Arrays.toString(tmp)); } }
		 */
		for (int i = 0; i < entryCount; i++) {
			int p = i * entrySize;
			if (container[p] == (byte) 0xE5 && !readDetetedEntrys)
				continue;// free entry
			if (container[p] == 0x00)
				break;// free entry, including every following index!
			// System.out.println("container[p]: " + container[p] + " | " +
			// Integer.toHexString(container[p]));
			int flags = container[p + 11] & 0xFF;
			// System.out.println("flags: " + flags);
			boolean ATTR_READ_ONLY = (flags & 1) != 0;
			boolean ATTR_HIDDEN = (flags & 2) != 0;
			boolean ATTR_SYSTEM = (flags & 4) != 0;
			boolean ATTR_VOLUME_ID = (flags & 8) != 0;
			boolean ATTR_DIRECTORY = (flags & 16) != 0;
			boolean ATTR_ARCHIVE = (flags & 32) != 0;
			boolean isLongName = flags == 0x0F;
			if (isLongName)
				continue;
			int fileSize = (int) readLittleEndian(container, p + 28, 4);
			int fileIndex = (int) (readLittleEndian(container, p + 26, 2)
					| (readLittleEndian(container, p + 20, 2) << 16));

			if (!isLongName) {
				String name = new String(container, p, 11);
				// System.out.println("Index in Dir: " + i);
				// System.out.println("name="+name);
				// System.out.println("fileSize: " + fileSize + ",
				// fileIndex: "+fileIndex);
				long time;
				{
					int dosTime = (int) readLittleEndian(container, p + 22, 2);
					int dosDate = (int) readLittleEndian(container, p + 24, 2);
					Calendar cal = Calendar.getInstance();

					cal.set(14, 0);
					cal.set(13, (dosTime & 0x1F) * 2);
					cal.set(12, dosTime >> 5 & 0x3F);
					cal.set(11, dosTime >> 11);

					cal.set(5, dosDate & 0x1F);
					cal.set(2, (dosDate >> 5 & 0xF) - 1);
					cal.set(1, 1980 + (dosDate >> 9));

					time = cal.getTimeInMillis();
					// System.out.println("time: " + time);
				}
				if (i > 0) {
					int i_backup = i;
					StringBuilder sb = new StringBuilder();
					while (true) {
						i--;
						if (i < 0)
							break;
						p = i * entrySize;
						int flags2 = container[p + 11] & 0xFF;
						// println("flags2 != 15 -> " + (flags2 != 15));
						if (flags2 != 15)
							break;
						// String name1 = new String(container, p+1, 10);
						sb.append(newStr(container, p + 1, 10)); // +5 chars
						// System.out.println("\t name1="+name1);
						// String name2 = new String(container, p+14, 12);
						sb.append(newStr(container, p + 14, 12)); // +6
																	// chars
						// System.out.println("\t name2="+name2);
						// String name3 = new String(container, p+28, 4);
						sb.append(newStr(container, p + 28, 4)); // +2 chars
						// System.out.println("\t name3="+name3);
						// int index = container[p + entrySize + 0];
						// System.out.println("\t next index: " + index);
						// println("(index & 0x40) != 0 -> " + ((index &
						// 0x40) != 0));
						// if((index & 0x40) != 0) continue;
						// break;
						continue;
					}
					i = i_backup;
					String fullName = sb.toString();
					if (fullName.length() > 0) {
						for (int ii = 0; ii < fullName.length(); ii++) {
							if (fullName.charAt(ii) == 0) {
								fullName = fullName.substring(0, ii);
								// System.out.println("fullname::substr:
								// @"+ii);
								break;
							}
						}
						System.out.println("fullName: " + fullName);
						name = fullName;
					}
				} else {
					for (int ii = 0; ii < name.length(); ii++) {
						if (name.charAt(ii) == 0) {
							name = name.substring(0, ii + 1);
							break;
						}
					}
				}

				for (int ii = name.length() - 1; ii >= 0; ii--) {
					if (name.charAt(ii) != 32) {
						name = name.substring(0, ii + 1);
						break;
					}
				}
				// System.out.println("name=>"+name);
				if (name.startsWith(".")) {
					char[] a = name.toCharArray();
					int[] b = new int[a.length];
					for (int ii = 0; ii < a.length; ii++)
						b[ii] = a[ii] & 0xFFFF;
					System.out.println(Arrays.toString(b));
				}
				if (name.equals(".") || name.equals(".."))
					continue;
				int[] objCoustors = readClusters(clustorMap, fileIndex, type);
				if (ATTR_DIRECTORY) {
					System.out.println("ATTR_DIRECTORY");
					try {
						readDir(source, objCoustors, bytesPerClustor, offsetFix, new Dir(dir.path + name + "/"), files,
								clustorMap, type, readDetetedEntrys);
					} catch (Exception e) {
						e.printStackTrace();
					}
					System.out.println(Arrays.toString(objCoustors));
				} else {
					files.add(new FileEntry(dir, name, time, objCoustors, fileSize));
				}
			}
		}
	}
	
	private static String newStr(byte[] arr, int off, int len) {
		char[] buf = new char[len / 2];
		for (int i = 0; i < buf.length; i++) {
			buf[i] = (char) ((arr[i * 2 + off] & 0xFF) | (arr[i * 2 + 1 + off] << 8));
		}
		if (buf[0] == 0x05) buf[0] = 0xE5; // KANJI lead byte fix
		return new String(buf);
	}
	

	public static class Dir {
		public String path;

		public Dir(String name) {
			path = name;
		}

	}

	public static class FileEntry {//TODO create an univeral File interface to make shared props like name, size etc. accessible
		public String name;
		public long age;
		public int[] clustors;
		public int fileSize;

		public FileEntry(Dir dir, String fname, long age, int[] clustors, int fileSize) {
			this.age = age;
			this.clustors = clustors;
			this.fileSize = fileSize;
			this.name = dir.path + fname;
		}

		@Override
		public String toString() {
			String cl;
			if (clustors.length > 16) {
				int[] c1 = new int[8];
				int[] c2 = new int[8];
				System.arraycopy(clustors, 0, c1, 0, c1.length);
				System.arraycopy(clustors, clustors.length - c2.length, c2, 0, c2.length);

				cl = Arrays.toString(c1);
				cl = cl.substring(0, cl.length() - 1) + ", ..., " + Arrays.toString(c2).substring(1);
			} else {
				cl = Arrays.toString(clustors);
			}
			return "{age: " + age + ", fileSize: " + fileSize + ", clustors: " + cl + ", name: " + name + "}";
		}
	}
}
