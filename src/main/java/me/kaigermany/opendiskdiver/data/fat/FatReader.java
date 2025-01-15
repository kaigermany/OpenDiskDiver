package me.kaigermany.opendiskdiver.data.fat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import me.kaigermany.opendiskdiver.data.Reader;
import me.kaigermany.opendiskdiver.datafilesystem.FileEntry;
import me.kaigermany.opendiskdiver.datafilesystem.FileSystem;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;
import me.kaigermany.opendiskdiver.utils.DumpUtils;

public class FatReader implements Reader, FileSystem {
	private static final byte[] EXFAT_SIGNATURE = "EXFAT   ".getBytes();
	
	private static long readLittleEndian(byte[] a, int offset, int len) {
		if (a == null)
			return 0;
		long b = 0;
		for (int i = 0; i < len; i++) {
			b |= (a[offset + i] & 0xFFL) << (i << 3);
		}
		return b;
	}
	
	public static boolean isFatFormated(byte[] bootSector){
		if(bootSector[510] == (byte)0x55 && bootSector[511] == (byte)0xAA){
			if(isExFat(bootSector)) return true;
			
			int FAT_copys = bootSector[16];
			if(FAT_copys >= 1 && FAT_copys <= 2) return true; //classic number of copies.
		}
		return false;
	}
	
	public static boolean isExFat(byte[] bootSector){
		for(int i=0; i<8; i++){
			if(bootSector[i + 3] != EXFAT_SIGNATURE[i]) return false;
		}
		return ByteArrayUtils.isEmpty(bootSector, 11, 53);
	}

	public static enum FatType{
		FAT12(12, 0x0FFF),
		FAT16(16, 0xFFFF),
		FAT32(32, 0x0FFFFFFF);
		
		private int EOF_FLAG;
		private int entrySize;
		
		FatType(int entrySize, int EOF_FLAG){
			this.EOF_FLAG = EOF_FLAG;
			this.entrySize = entrySize;
		}
		
		public int getEOF_FLAG(){
			return EOF_FLAG;
		}
		
		public int getEntrySize(){
			return entrySize;
		}
		
		public static FatType getTypeByClusterCount(long clusterCount){
			/*
			if(clusterCount > 65524L) return FAT32;
			else if(clusterCount > 4084L) return FAT16;
			else return FAT12;
			*/
			/*
			//based on https://averstak.tripod.com/fatdox/bootsec.htm
			if(clusterCount < 4087L) return FAT12;
			if(clusterCount < 65527L) return FAT16;
			if(clusterCount < 268435457L) return FAT32;
			return null;
			*/
			//based on https://academy.cba.mit.edu/classes/networking_communications/SD/FAT.pdf
			if(clusterCount < 4085L) return FAT12;
			else if(clusterCount < 65525L) return FAT16;
			else return FAT32;
		}
	}
	
	public static class FAT{//TODO
		
	}
		
	public static class ExFAT{//TODO
		
	}
	
	public static class ExFatFile extends FileEntry {
		private final ExFatEntryObject file;
		private final ExFatChainTable fat;
		private final int clusterSize;
		private final long clusterHeapOffset;
		private final ReadableSource source;
		private final boolean isContinousFileStream;
		
		public ExFatFile(String name, String nameAndPath, long size, long age, 
				ExFatEntryObject file, ExFatChainTable fat, int clusterSize, long clusterHeapOffset, ReadableSource source, boolean isContinousFileStream) {
			super(name, nameAndPath, size, age);
			this.file = file;
			this.fat = fat;
			this.clusterSize = clusterSize;
			this.clusterHeapOffset = clusterHeapOffset;
			this.source = source;
			this.isContinousFileStream = isContinousFileStream;
		}

		@Override
		public InputStream openInputStream() {
			if(!isContinousFileStream){
				return new ExFatInputStream(file, fat, clusterSize, clusterHeapOffset, source);
			} else {
				return new InputStream() {
					byte[] clusterBuffer = new byte[clusterSize * 512];
					int clusterBufferOffset = clusterBuffer.length;
					int clusterBufferLen = 0;
					long remainingBytes = file.streamInfo.validDataLen;
					
					long currentCluster = file.streamInfo.firstCluster;
					
					@Override
					public int read() throws IOException {
						// Read a single byte
						byte[] singleByte = new byte[1];
						int result = read(singleByte, 0, 1);
						return result == -1 ? -1 : singleByte[0] & 0xFF;
					}

					@Override
					public int read(byte[] b, int off, int len) throws IOException {
						if (remainingBytes <= 0) {
							return -1; // End of stream
						}
						if(remainingBytes < len){
							len = (int)remainingBytes;
						}
						int bytesRead = 0;
						if(clusterBufferLen > 0){
							int numBytesToCopy = Math.min(len, clusterBufferLen);
							System.arraycopy(clusterBuffer, clusterBufferOffset, b, off, numBytesToCopy);
							clusterBufferOffset += numBytesToCopy;
							clusterBufferLen -= numBytesToCopy;
							bytesRead = numBytesToCopy;
							if(numBytesToCopy == len) {
								remainingBytes -= bytesRead;
								System.out.println(remainingBytes + " | " + bytesRead + " | " + clusterBufferLen);
								return bytesRead;
							}
							off += numBytesToCopy;
							len -= numBytesToCopy;
						}
						int clusterBytes = clusterSize * 512;
						int numFullClustersRequired = len / clusterBytes;
						int numBytesRemaining = len % clusterBytes;
						if(numFullClustersRequired > 0){
							source.readSectors(clusterHeapOffset + (currentCluster * clusterSize), clusterSize * numFullClustersRequired, b, off);
							int bytes = clusterBytes * numFullClustersRequired;
							off += bytes;
							len -= bytes;
							System.out.println("currentCluster#1 = " + currentCluster);
							currentCluster += numFullClustersRequired;
							bytesRead += bytes;
						}
						if(numBytesRemaining > 0){
							if(clusterBufferLen != 0){
								throw new IOException("invalid buffer state: expected empty, but size is " + clusterBufferLen);
							}
							
							source.readSectors(clusterHeapOffset + (currentCluster * clusterSize), clusterSize, clusterBuffer, 0);
							clusterBufferOffset = 0;
							clusterBufferLen = clusterBytes;
							System.out.println("currentCluster#2 = " + currentCluster);
							currentCluster++;
						}
						remainingBytes -= bytesRead;
						System.out.println(remainingBytes + " | " + bytesRead);
						return bytesRead;
					}
				};
			}
		}
		
		@Override
		public String toString() {
			return "{file: " + super.name + ", isContinousFileStream: " + isContinousFileStream + "}";
		}
	}
	
	public static class ExFatRawFile{
		public static ExFatRawFile parse(byte[] buf, int off){
			int firstCluster = (int) readLittleEndian(buf, off + 20, 4);
			long dataLength = readLittleEndian(buf, off + 24, 8);
			return new ExFatRawFile(firstCluster, dataLength);
		}
		
		public final int firstCluster;
		public final long numBytes;
		
		public ExFatRawFile(int firstCluster, long fileSizeInBytes){
			this.firstCluster = firstCluster;
			this.numBytes = fileSizeInBytes;
		}
	}
	
	public static class ExFatEntryObject{
		public static ExFatEntryObject parse(byte[] buf, int off){
			int attributeFlags = (int) readLittleEndian(buf, off + 4, 4);
			long timeCreated = parseTime((int)readLittleEndian(buf, off + 8, 4), buf[off + 20] & 0xFF);
			long timeModified = parseTime((int)readLittleEndian(buf, off + 12, 4), buf[off + 21] & 0xFF);
			long timeAccessed = parseTime((int)readLittleEndian(buf, off + 16, 4), buf[off + 22] & 0xFF);
			int numFollowupEntries = buf[off + 1] & 0xFF;
			boolean ATTR_READ_ONLY = (attributeFlags & 1) != 0;
			boolean ATTR_HIDDEN = (attributeFlags & 2) != 0;
			boolean ATTR_SYSTEM = (attributeFlags & 4) != 0;
			boolean ATTR_DIRECTORY = (attributeFlags & 16) != 0;
			System.out.println("ATTR_DIRECTORY = " + ATTR_DIRECTORY);
			return new ExFatEntryObject(numFollowupEntries, ATTR_DIRECTORY, timeModified);
		}
		private static long parseTime(int timeBits, int extra10ms){
			Calendar cal = Calendar.getInstance();
			cal.set(14, 0);
			cal.set(13, (timeBits & 0x1F) * 2);
			cal.set(12, (timeBits >> 5) & 0x3F);
			cal.set(11, (timeBits >> 11) & 0x1F);
			cal.set(5, (timeBits >> 16) & 0x1F);
			cal.set(2, (timeBits >> 21 & 0xF) - 1);
			cal.set(1, 1980 + ((timeBits >> 25) & 0x7F));
			return cal.getTimeInMillis() + (extra10ms * 10);
		}
		
		public final int numFollowupEntries;
		public final boolean isDir;
		
		public ExFatStreamExtension streamInfo;
		public boolean isDeleted = false;
		
		public final long timeLastModified;
		
		public ExFatEntryObject(int numFollowupEntries, boolean isDir, long timeLastModified){
			this.numFollowupEntries = numFollowupEntries;
			this.isDir = isDir;
			this.timeLastModified = timeLastModified;
		}
		
	}
	
	public static class ExFatStreamExtensionBuilder{
		final int fileNameLen;
		final char[] fileName;
		int fileNameWritePtr;
		
		final long validDataLen;//confirmed number of bytes written on disk. might be less then the declaration of dataLen.
		final long firstCluster;
		final long dataLen;
		
		final boolean isContinousFileStream;
		
		public ExFatStreamExtensionBuilder(byte[] buf, int off){
			this.fileNameLen = buf[off + 3] & 0xFF;
			this.fileName = new char[this.fileNameLen];
			this.fileNameWritePtr = 0;

			this.validDataLen = readLittleEndian(buf, off + 8, 8);
			this.firstCluster = readLittleEndian(buf, off + 20, 4);
			this.dataLen = readLittleEndian(buf, off + 24, 8);
			
			this.isContinousFileStream = (buf[off + 1] & 2) == 2;
		}
		public boolean putNextNameEntry(byte[] buf, int off){
			int charsToRead = Math.min(fileNameLen - fileNameWritePtr, 15);
			ByteBuffer.wrap(buf, off + 2, 30).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer().get(fileName, fileNameWritePtr, charsToRead);
			/*
			byte[] a = new byte[30];
			ByteBuffer.wrap(buf, 2, 30).get(a);
			System.out.println(DumpUtils.binaryDumpToString(a));
			*/
			fileNameWritePtr += charsToRead;
			System.out.println("\t\t\tfileNameWritePtr="+fileNameWritePtr + ", fileNameLen="+fileNameLen);
			return fileNameWritePtr == fileNameLen;
		}
		public ExFatStreamExtension build(){
			return new ExFatStreamExtension(validDataLen, firstCluster, dataLen, new String(fileName), isContinousFileStream);
		}
	}
	
	public static class ExFatStreamExtension{
		final long validDataLen;
		final long firstCluster;
		final long dataLen;
		final String name;
		final boolean isContinousFileStream;
		
		public ExFatStreamExtension(long validDataLen, long firstCluster, long dataLen, String name, boolean isContinousFileStream) {
			this.validDataLen = validDataLen;
			this.firstCluster = firstCluster;
			this.dataLen = dataLen;
			this.name = name;
			this.isContinousFileStream = isContinousFileStream;
		}
	}
	
	public void readDirExFat(ExFatChainTable fat, byte[] dirContents, String path, HashMap<String, ExFatEntryObject> filesOut
			, int clusterSize, long clusterHeapOffset) throws IOException {
		
		final boolean parseDeletedEntries = false;
		
		ExFatStreamExtensionBuilder currentStreamBuilder = null;
		ExFatEntryObject currentObject = null;
		for(int off=0; off<dirContents.length; off+=32){
			int EntryType = dirContents[off + 0] & 0xFF;
			//System.out.println("EntryType -> " + EntryType);
			boolean isDeletedEntry = false;
			if(parseDeletedEntries){
				if(EntryType == 5 || EntryType == 64 || EntryType == 65){
					EntryType |= 0x80;
					isDeletedEntry = true;
				}
			}
			if(EntryType == 133){//directory
				currentObject = ExFatEntryObject.parse(dirContents, off);
				currentObject.isDeleted = isDeletedEntry;
				System.out.println("numFollowupEntries: " + currentObject.numFollowupEntries);
			} else if(EntryType == 192){//stream entry
				currentStreamBuilder = new ExFatStreamExtensionBuilder(dirContents, off);
			} else if(EntryType == 193){//stream-name extension entry
				if(currentStreamBuilder != null){
					if(currentStreamBuilder.putNextNameEntry(dirContents, off)){
						ExFatStreamExtension extend = currentStreamBuilder.build();
						System.out.println("extend.name = '" + extend.name + "'");
						currentStreamBuilder = null;
						currentObject.streamInfo = extend;
						String fullName = path + "/" + extend.name;
						filesOut.put(fullName, currentObject);
						
						if(!currentObject.isDir){
							exFatFiles.add(new ExFatFile(extend.name, fullName, extend.validDataLen,
									currentObject.timeLastModified, currentObject, fat, clusterSize, clusterHeapOffset, source, extend.isContinousFileStream));
							/*
							if(fullName.equals("/Unbenannt.png")){
								byte[] bytes = readFullFile(currentObject, fat, clusterSize, clusterHeapOffset);
								try{
									FileOutputStream fos = new FileOutputStream("H:/dumptest_direct.png");
									fos.write(bytes);
									fos.close();
								}catch(Exception e){
									e.printStackTrace();
								}
							}
							*/
						}
						
						if(currentObject.isDir && !currentObject.isDeleted) {
							readDirExFat(fat, currentObject, fullName, filesOut, clusterSize, clusterHeapOffset);
						}
						
						currentObject = null;
					}
				}
			}
		}
	}
	
	public void readDirExFat(ExFatChainTable fat, ExFatEntryObject rootDir, String path, HashMap<String, ExFatEntryObject> filesOut
			, int clusterSize, long clusterHeapOffset) throws IOException {
		byte[] dirContents = readFullFile(rootDir, fat, clusterSize, clusterHeapOffset);
		readDirExFat(fat, dirContents, path, filesOut, clusterSize, clusterHeapOffset);
	}
	
	byte[] readFullFile(ExFatEntryObject file, ExFatChainTable fat, int clusterSize, long clusterHeapOffset) throws IOException {
		/*{
			int cluster = (int)file.streamInfo.firstCluster;
			int ptr = cluster;
			ArrayList<Long> list = new ArrayList<>();
			while(ptr != -1){
				list.add(ptr & 0xFFFFFFFFL);
				System.out.println(list);
				ptr = fat.get(ptr);
				if(list.size() > 20) throw new RuntimeException();
			}
		}*/
		int cluster = (int)file.streamInfo.firstCluster;
		byte[] clusterBuffer = new byte[clusterSize * 512];
		byte[] out = new byte[(int)file.streamInfo.validDataLen];
		int wp = 0;
		while(cluster != -1){
			if(wp == out.length){
				break;
			}
			System.out.println("cluster = " + cluster);
			System.out.println("location = " + cluster);
			{
				byte[] dump = new byte[512];
				source.readSector(clusterHeapOffset + ((cluster) * (long)clusterSize), dump);
				System.out.println(DumpUtils.binaryDumpToString(dump));
			}
			source.readSectors(clusterHeapOffset + ((cluster & 0xFFFFFFFFL) * (long)clusterSize), clusterSize, clusterBuffer);
			cluster = fat.get(cluster);//fat[cluster];
			int len = Math.min(out.length - wp, clusterBuffer.length);
			System.out.println("len="+len);
			System.out.println("out.length="+out.length);
			System.arraycopy(clusterBuffer, 0, out, wp, len);
			wp += len;
		}
		return out;
	}
	
	public ArrayList<FatFile> files = new ArrayList<FatFile>();
	public FatType type;
	public int[][] fats;
	
	private ReadableSource source;
	private long bytesPerClustor;
	
	public ArrayList<ExFatFile> exFatFiles;
	
	public FatReader() {}

	@Override
	public void read(ReadableSource source) throws IOException {
		this.source = source;
		byte[] bootSector = new byte[512];
		source.readSector(0, bootSector);
		//DumpUtils.binaryDump(bootSector);
		if(ByteArrayUtils.isEmptySector(bootSector)){//alternative boot sector
			source.readSector(6, bootSector);//TODO make compatible with ExFat!
		}
		
		boolean isExFat = isExFat(bootSector);
		System.out.println("isExFat: " + isExFat);
		
		if(isExFat){
			readExFAT(bootSector);
		} else {
			readClassicFAT(bootSector);
		}
	}
	private void readExFAT(byte[] bootSector) throws IOException {
		// https://en.wikipedia.org/wiki/ExFAT
		// https://learn.microsoft.com/en-us/windows/win32/fileio/exfat-specification
		// https://events.static.linuxfound.org/images/stories/pdf/lceu11_munegowda_s.pdf
		// https://wiki.osdev.org/ExFAT
		ExFAT_BootSector bootSectorContainer_exFat = new ExFAT_BootSector(bootSector);
		System.out.println("VolumeLength = " + bootSectorContainer_exFat.VolumeLength);
		System.out.println("FatOffset = " + bootSectorContainer_exFat.FatOffset);
		System.out.println("FatLength = " + bootSectorContainer_exFat.FatLength);
		System.out.println("NumberOfFats = " + bootSectorContainer_exFat.NumberOfFats);
		System.out.println("BytesPerSector = " + bootSectorContainer_exFat.BytesPerSector);
		System.out.println("SectorsPerCluster = " + bootSectorContainer_exFat.SectorsPerCluster);
		
		System.out.println("ClusterHeapOffset = " + bootSectorContainer_exFat.ClusterHeapOffset);
		System.out.println("ClusterCount = " + bootSectorContainer_exFat.ClusterCount);
		System.out.println("FirstClusterOfRootDirectory = " + bootSectorContainer_exFat.FirstClusterOfRootDirectory);
		
		System.out.println("dataOffset = " + bootSectorContainer_exFat.dataOffset);
		
		long rootDirFirstSector = bootSectorContainer_exFat.ClusterHeapOffset
				+ bootSectorContainer_exFat.SectorsPerCluster * (long)bootSectorContainer_exFat.FirstClusterOfRootDirectory;
		
		System.out.println("rootDirFirstSector = " + rootDirFirstSector);
		
		byte[] rootDirSector = new byte[512 * bootSectorContainer_exFat.SectorsPerCluster];
		String volumeTitle = null;
		ExFatRawFile bitmapFile = null;
		ExFatRawFile UpCaseTableFile = null;
		ExFatStreamExtensionBuilder currentStreamBuilder = null;
		
		ExFatEntryObject rootDirEntry = null;
		
		HashMap<String, ExFatEntryObject> files = new HashMap<>();
		
		source.readSectors(rootDirFirstSector, bootSectorContainer_exFat.SectorsPerCluster, rootDirSector);
		//System.out.println(DumpUtils.binaryDumpToString(rootDirSector));
		
		
		for(int off=0; off<512; off+=32){
			int EntryType = rootDirSector[off + 0] & 0xFF;
			if(EntryType == 131){//volume title
				int len = rootDirSector[off + 1];
				char[] chars = new char[11];
				ByteBuffer.wrap(rootDirSector, off + 2, 22).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer().get(chars);
				volumeTitle = new String(chars, 0, len);
			} else if(EntryType == 3){//no volume title
				volumeTitle = "";
			} else if(EntryType == 129){
				bitmapFile = ExFatRawFile.parse(rootDirSector, off);
			} else if(EntryType == 130){
				UpCaseTableFile = ExFatRawFile.parse(rootDirSector, off);
			} else if(EntryType == 133){//directory
				rootDirEntry = ExFatEntryObject.parse(rootDirSector, off);
				System.out.println("numFollowupEntries: " + rootDirEntry.numFollowupEntries);
			} else if(EntryType == 192){//stream entry
				currentStreamBuilder = new ExFatStreamExtensionBuilder(rootDirSector, off);
			} else if(EntryType == 193){//stream-name extension entry
				if(currentStreamBuilder != null){
					if(currentStreamBuilder.putNextNameEntry(rootDirSector, off)){
						ExFatStreamExtension extend = currentStreamBuilder.build();
						System.out.println("extend.name = '" + extend.name + "'");
						currentStreamBuilder = null;
						rootDirEntry.streamInfo = extend;
						files.put("/" + extend.name, rootDirEntry);
						rootDirEntry = null;
						break;
					}
				}
			} else if(EntryType == 0){
				break;
			}
		}
		
		ExFatChainTable[] fatTables = new ExFatChainTable[bootSectorContainer_exFat.NumberOfFats];
		for(int fatIndex=0; fatIndex<bootSectorContainer_exFat.NumberOfFats; fatIndex++){
			ExFatChainTable fatTable = new ExFatChainTable(bootSectorContainer_exFat.FatLength * 512);
			fatTable.load(source, bootSectorContainer_exFat.FatOffset + bootSectorContainer_exFat.FatLength * fatIndex);
			fatTables[fatIndex] = fatTable;
		}
		
		exFatFiles = new ArrayList<>();
		
		readDirExFat(fatTables[0], rootDirSector, "", files, bootSectorContainer_exFat.SectorsPerCluster, bootSectorContainer_exFat.ClusterHeapOffset);
	}
	
	private void readClassicFAT(byte[] bootSector) throws IOException {
		FAT_BootSector bootSectorContainer = new FAT_BootSector(bootSector);
		this.type = bootSectorContainer.type;
		
		bytesPerClustor = bootSectorContainer.bytes_per_sector * bootSectorContainer.sectors_per_clustor;
		
		//boolean isFAT32 = clusterCount > 65524L;
		//boolean isFAT16 = clusterCount > 4084L & !isFAT32;
		// boolean isFAT12 = !isFAT16 & !isFAT32;

		System.out.println("FatType: " + bootSectorContainer.type);
		String volumeLabel = new String(bootSector, bootSectorContainer.type == FatType.FAT32 ? 71 : 43, 11);
		System.out.println("volumeLabel: " + volumeLabel);

		int bytesPerClustor = bootSectorContainer.bytes_per_sector * bootSectorContainer.sectors_per_clustor;

		fats = new int[bootSectorContainer.FAT_copys][];
		int getEntrySize = bootSectorContainer.type.getEntrySize(); 
				//bootSectorContainer.type == FatType.FAT32 ? 32 : (bootSectorContainer.type == FatType.FAT16 ? 16 : 12);
		for (int i = 0; i < bootSectorContainer.FAT_copys; i++) {
			long offset = (bootSectorContainer.reserved_sectors * bootSectorContainer.bytes_per_sector) + (i * (bootSectorContainer.fatSz * bootSectorContainer.bytes_per_sector));
			fats[i] = readFAT(bootSectorContainer.clusterCount, source, getEntrySize, offset, bootSectorContainer.fatSz * bootSectorContainer.bytes_per_sector);
		}
		int[] rootClustors = readClusters(fats[0], bootSectorContainer.rootClustorNumber, bootSectorContainer.type);
		System.out.println(Arrays.toString(rootClustors));
		long offsetFix = (bootSectorContainer.FirstDataSector * bootSectorContainer.bytes_per_sector);// - (bytesPerClustor * bootSectorContainer.rootClustorNumber);
		Dir dir = new Dir("");//"/"
		
		if(bootSectorContainer.type == FatType.FAT32){
			
			try{
				System.out.println("offsetFix -> " + offsetFix);
				HashSet<Long> doneDirEntries = new HashSet<Long>(1024);
				readDir(source, rootClustors, bytesPerClustor, offsetFix, dir, files, fats[0], bootSectorContainer.type, false, doneDirEntries);
				
			}catch(Throwable e){
				e.printStackTrace();
			}
			
		} else {
			//offsetFix = (bootSectorContainer.FirstDataSector * bootSectorContainer.bytes_per_sector);
			try{
				System.out.println("offsetFix -> " + offsetFix);
				HashSet<Long> doneDirEntries = new HashSet<Long>(1024);
				int rootDirSectorOffset = bootSectorContainer.rootClustorNumber;
				int rootDirSectorSize = bootSectorContainer.rootDirSectors;
				byte[] directData = new byte[rootDirSectorSize * 512];
				source.readSectors(rootDirSectorOffset, rootDirSectorSize, directData, 0);
				readDirDirect(source, rootClustors, bytesPerClustor, offsetFix, dir, files, fats[0], bootSectorContainer.type, false, doneDirEntries, directData);
			}catch(Throwable e){
				e.printStackTrace();
			}
			
			
		}
		
		
		/*
		FileOutputStream fos = new FileOutputStream(new File("H:/rawDump.txt"));
		fos.write(files.toString().replace("}, {", "},\n{").getBytes());
		fos.close();
		*/

		System.out.println(files.toString().replace("}, {", "},\n{"));
		/*
		int sectorOffsetOfFATonDrive = 245;
		
		int[] clustors = readClusters(fats[0], 2616  , bootSectorContainer.type);
		int[] runLenClusters = asRunLengthList(clustors);
		System.out.println("BEGIN direct file location (in sectors):");
		for(int i=0; i<runLenClusters.length; i+=2){
			long plainDataOffset = (runLenClusters[i] * bytesPerClustor) / 512 + bootSectorContainer.FirstDataSector + sectorOffsetOfFATonDrive;
			long plainLen = runLenClusters[i + 1] * bytesPerClustor / 512;
			System.out.println(plainDataOffset + " +" + plainLen);
		}
		System.out.println("END direct file location");
		*/
		/*
		{
			//System.out.println(Arrays.toString(clustors));
			System.out.println(Arrays.toString(asRunLengthList(clustors)));
			byte[] container = new byte[clustors.length * bytesPerClustor];
			for (int i = 0; i < clustors.length; i++) {
				source.readSectors((clustors[i] * bytesPerClustor) / 512 + bootSectorContainer.FirstDataSector, bytesPerClustor / 512, container, i * bytesPerClustor);
			}
			FileOutputStream fos = new FileOutputStream(new File("H:/dump.mp4"));
			fos.write(container);
			fos.close();
		}
		*/
		
		runClusterPointerMapAnalysis(fats[1], bootSectorContainer.type);

	}

	@Override
	public List<FileEntry> listFiles() {
		if(exFatFiles != null){
			return new ArrayList<>(exFatFiles);
		} else {
			ArrayList<FileEntry> list = new ArrayList<>(files.size());
			for(FatFile e : files){
				list.add(new FatFileEntry(e, source, bytesPerClustor));
			}
			return list;
		}
		
	}
	
	private static int[] asRunLengthList(int[] serialList){
		ArrayList<Integer> temp = new ArrayList<Integer>(16);
		int start = serialList[0];
		int len = 1;
		for(int i=1; i<serialList.length; i++){
			int pos = serialList[i];
			if(pos == start+len){
				len++;
			} else {
				temp.add(start);
				temp.add(len);
				start = pos;
				len = 1;
			}
		}
		temp.add(start);
		temp.add(len);
		int[] out = new int[temp.size()];
		int wp=0;
		for(int v : temp){
			out[wp++] = v;
		}
		return out;
	}
	
	private void runClusterPointerMapAnalysis(int[] clusterMap, FatType type) {
		int EOF_FLAG = type.getEOF_FLAG() - 7;
		System.out.println("clusterMap.length = " + clusterMap.length);
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
		ArrayList<Long> treeEnds = inverseLinkerList.get(-1);
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
		try{
			for (int i = 0; i < entries.length; i++) {
				entries[i] = readVal(data, i, getEntrySize) & 0x0FFFFFFF;
				// entries[i] += firstDataClustorOffset;//resSectBytes / 512;
				// if(entries[i] != 0) System.out.println(entries[i]);
			}
		}catch(Exception e){
			e.printStackTrace();
			System.err.println("WARNING: incomplete FAT linked list! maybe not every file can be read!");
		}
		/*
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

	public static int[] readClusters(int[] clustorMap, int clustor, FatType type) {
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
			ArrayList<FatFile> files, int[] clustorMap, FatType type, boolean readDetetedEntrys, HashSet<Long> doneDirEntries) throws IOException {
		byte[] container = new byte[clustors.length * bytesPerClustor];
		for (int i = 0; i < clustors.length; i++) {
			source.readSectors((clustors[i] * bytesPerClustor + offsetFix) / 512, bytesPerClustor / 512, container, i * bytesPerClustor);
			System.out.println("read cluster: " + ( (clustors[i] * bytesPerClustor + offsetFix) / 512 ));
		}
		readDirDirect(source, clustors, bytesPerClustor, offsetFix, dir, files, clustorMap, type, readDetetedEntrys, doneDirEntries, container);
	}
	
	private static void readDirDirect(ReadableSource source, int[] clustors, int bytesPerClustor, long offsetFix, Dir dir,
			ArrayList<FatFile> files, int[] clustorMap, FatType type, boolean readDetetedEntrys, HashSet<Long> doneDirEntries, byte[] container) throws IOException {
		
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
			if (container[p] == (byte) 0xE5 && !readDetetedEntrys){
				continue;// free entry
			}
			if (container[p] == 0x00){
				break;// free entry, including every following index!
			}
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
			if (isLongName){
				continue;
			}
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
				
				if(doneDirEntries.contains((long)fileIndex)){
					return;
				} else {
					doneDirEntries.add((long)fileIndex);
				}
				int[] objCoustors = readClusters(clustorMap, fileIndex, type);
				
				if (ATTR_DIRECTORY) {
					System.out.println("ATTR_DIRECTORY");
					/*
					if(Arrays.equals(objCoustors, clustors)){
						//System.out.println("INFO/WARN: unexpected recursion in current dir's cluster-list found! entry will be skiped.");
						System.err.println("ERROR: unexpected recursion in current dir's cluster-list found!");
						return;
					}
					*/
					try {
						System.out.println("objCoustors: " + Arrays.toString(objCoustors));
						readDir(source, objCoustors, bytesPerClustor, offsetFix, new Dir(dir.path + name + "/"), files,
								clustorMap, type, readDetetedEntrys, doneDirEntries);
					} catch (Exception e) {
						//e.printStackTrace();
						System.err.println(e);
					}
					
					System.out.println(Arrays.toString(objCoustors));
				} else {
					files.add(new FatFile(dir, name, time, objCoustors, fileSize));
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

	public static class FatFile {//TODO create an universal File interface to make shared props like name, size etc. accessible
		public String name;
		public long age;
		public int[] clustors;
		public int fileSize;
		public String nameOnly;
		
		public FatFile(Dir dir, String fname, long age, int[] clustors, int fileSize) {
			this.age = age;
			this.clustors = clustors;
			this.fileSize = fileSize;
			this.name = dir.path + fname;
			this.nameOnly = fname;
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
