package me.kaigermany.opendiskdiver.data.fat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;
import me.kaigermany.opendiskdiver.utils.DumpUtils;

public class FatEntryFinder {
	public static ArrayList<FatReader.FatFile> scanReader(ReadableSource reader) throws IOException {
		byte[] sectorBuffer = new byte[512];
		long[] currentLoadedSectorPtr = new long[]{ -1 };
		
		ArrayList<FatReader.FatFile> out = new ArrayList<FatReader.FatFile>(256);
		
		long numSectors = reader.numSectors();
		//byte[] lastBlock = null;
		
		long numEntrysToScan = numSectors * 512 / 32;
		byte[] entryBuffer = new byte[32];
		for(long currReadPos=0; currReadPos<numEntrysToScan; currReadPos++){
			readEntry(reader, currReadPos, sectorBuffer, currentLoadedSectorPtr, entryBuffer);

			if(422789*512/32 == currReadPos){
				System.out.println("POS1: ");
				DumpUtils.binaryDump(entryBuffer);
				System.out.println(isValidEntry(entryBuffer, 0, true));
			}
			if(371525*512/32 == currReadPos){
				System.out.println("POS2: ");
				DumpUtils.binaryDump(entryBuffer);
				System.out.println(isValidEntry(entryBuffer, 0, true));
			}
			
			if (!isValidEntry(entryBuffer, 0, false)) continue;
			StringBuilder sb = new StringBuilder();
			ByteArrayOutputStream nameEntriesRecorder = new ByteArrayOutputStream(2048);
			nameEntriesRecorder.write(entryBuffer);
			int crc = ChkSum(entryBuffer, 0);
			
			final int pos = 0;
			
			if(findAllNameExtensions2(reader, currReadPos, sectorBuffer, currentLoadedSectorPtr, entryBuffer, 1, sb, crc, nameEntriesRecorder)){
				if(testNameBoundries(sb)){
					//System.out.println("name: " + sb);
					//DumpUtils.binaryDump(nameEntriesRecorder.toByteArray());
					int fileIndex = ((sectorBuffer[pos + 21] & 0xFF) << 24) | ((sectorBuffer[pos + 20] & 0xFF) << 16)
							| ((sectorBuffer[pos + 27] & 0xFF) << 8) | (sectorBuffer[pos + 26] & 0xFF);
					int fileSize = ((sectorBuffer[pos + 31] & 0xFF) << 24) | ((sectorBuffer[pos + 30] & 0xFF) << 16)
							| ((sectorBuffer[pos + 29] & 0xFF) << 8) | (sectorBuffer[pos + 28] & 0xFF);
					
					//indexToLengthMap.put((long)fileIndex, (long)fileSize);
					System.out.println("name: " + sb + " \t\t fileIndex="+fileIndex+",fileSize="+fileSize);
					//System.out.println(Integer.toHexString(fileIndex) + "   " + Integer.toHexString(fileSize));
					int[] objCoustors = new int[]{fileIndex};
					int age = 0;
					String fname = sb.toString();
					out.add(new FatReader.FatFile(new FatReader.Dir("LOST_AND_FOUND/"), fname, age, objCoustors, fileSize));
			
				} else {
					System.out.println("testNameBoundries() -> false.");
				}
			} else {
				System.out.println("findAllNameExtensions() -> false.");
				DumpUtils.binaryDump(nameEntriesRecorder.toByteArray());
				if((entryBuffer[13] & 0xFF) == crc){
					System.out.println("found valid DOS entry!");
				}
				
			}
		}
		
		
		/*
		for(long readPos = 0; readPos < numSectors; readPos++){
			reader.readSector(readPos, sectorBuffer);
			
			for(int pos=0; pos<sectorBuffer.length; pos += 32){
				if (!isValidEntry(sectorBuffer, pos)) continue;
				
				//System.out.println((block[pos + 13] & 0xFF) + " -> " + ChkSum(block, pos));
				StringBuilder sb = new StringBuilder();
				ByteArrayOutputStream nameEntriesRecorder = new ByteArrayOutputStream(2048);
				//int crc = block[pos + 13] & 0xFF;
				int crc = ChkSum(sectorBuffer, pos);
				if(findAllNameExtensions(sectorBuffer, lastBlock, pos, crc, 1, sb, nameEntriesRecorder)){
					if(testNameBoundries(sb)){
						//System.out.println("name: " + sb);
						//DumpUtils.binaryDump(nameEntriesRecorder.toByteArray());
						int fileIndex = ((sectorBuffer[pos + 21] & 0xFF) << 24) | ((sectorBuffer[pos + 20] & 0xFF) << 16)
								| ((sectorBuffer[pos + 27] & 0xFF) << 8) | (sectorBuffer[pos + 26] & 0xFF);
						int fileSize = ((sectorBuffer[pos + 31] & 0xFF) << 24) | ((sectorBuffer[pos + 30] & 0xFF) << 16)
								| ((sectorBuffer[pos + 29] & 0xFF) << 8) | (sectorBuffer[pos + 28] & 0xFF);
						
						//indexToLengthMap.put((long)fileIndex, (long)fileSize);
						System.out.println("name: " + sb + " \t\t fileIndex="+fileIndex+",fileSize="+fileSize);
						//System.out.println(Integer.toHexString(fileIndex) + "   " + Integer.toHexString(fileSize));
						/ *
						if(reader != null){
							int[] objCoustors = FatReader.readClusters(reader.fats[0], fileIndex, reader.type);
							int age = 0;
							String fname = sb.toString();
							out.add(new FatReader.FileEntry(new FatReader.Dir("LOST_AND_FOUND/"), fname, age, objCoustors, fileSize));
						}
						* /
						int[] objCoustors = new int[]{fileIndex};
						int age = 0;
						String fname = sb.toString();
						out.add(new FatReader.FileEntry(new FatReader.Dir("LOST_AND_FOUND/"), fname, age, objCoustors, fileSize));
				
					} else {
						System.out.println("testNameBoundries() -> false.");
					}
				} else {
					System.out.println("findAllNameExtensions() -> false.");
					//DumpUtils.binaryDump(nameEntriesRecorder.toByteArray());
				}
				
			}
		}
		*/
		return out;
	}
	
	private static void readEntry(ReadableSource reader, long entryLocationOffset, byte[] sectorBuffer, long[] currentLoadedSectorPtr, byte[] outBuf) throws IOException{
		long byteOffset = entryLocationOffset * 32;
		long sector = byteOffset / 512;
		if(sector != currentLoadedSectorPtr[0]){
			currentLoadedSectorPtr[0] = sector;
			reader.readSector(sector, sectorBuffer);
		}
		int off = (int)(byteOffset % 512);
		for(int i=0; i<32; i++){
			outBuf[i] = sectorBuffer[i + off];
		}
	}
	
	private static boolean findAllNameExtensions2(ReadableSource reader, long entryLocationOffset, byte[] sectorBuffer, long[] currentLoadedSectorPtr, byte[] block, int depth, StringBuilder nameRecorder, int expectedCheckSum, ByteArrayOutputStream nameEntriesRecorder) throws IOException{
		entryLocationOffset--;
		if(entryLocationOffset < 0) return false;
		
		readEntry(reader, entryLocationOffset, sectorBuffer, currentLoadedSectorPtr, block);
		nameEntriesRecorder.write(block);
		
		final int pos = 0;
		int entryID = block[pos] & 0x3F;
		System.out.println("entryID="+entryID);
		if(entryID != depth) {//not the right order (ignoring end-node flags) ?
			return false;
		}
		
		if(block[pos + 11] != 0x0F) return false;//not an extension entry?
		if(block[pos + 26] != 0 && block[pos + 27] != 0) return false;//first-cluster value != 0?
		if((block[pos + 13] & 0xFF) != expectedCheckSum) return false;//this entry does'nt seem to be related to our parent or is damaged!
		
		nameRecorder.append(newStr(block, pos + 1, 10)); // +5 chars
		nameRecorder.append(newStr(block, pos + 14, 12));// +6 chars
		nameRecorder.append(newStr(block, pos + 28, 4)); // +2 chars
		System.out.println((block[pos] & 0x7F) + " == " + (0x40 | depth) + " ( " + depth + " ) ");
		if((block[pos] & 0x7F) == (0x40 | depth)) {//is end-node?
			System.out.println("endnode.");
			//crc compare
			//int crc = ChkSum(block, pos);
			//System.out.println("crc="+crc + ", expectedCheckSum="+expectedCheckSum);
			return true;
		} else {//find next node until completely resolved.
			return findAllNameExtensions2(reader, entryLocationOffset, sectorBuffer, currentLoadedSectorPtr, block, depth+1, nameRecorder, expectedCheckSum, nameEntriesRecorder);
		}
	}

	private static boolean testNameBoundries(StringBuilder sb) {
		if(sb.charAt(sb.length() - 1) == 0xFFFF){
			while(true){
				sb.setLength(sb.length() - 1);
				int nextChar = sb.charAt(sb.length() - 1);
				if(nextChar == 0){
					sb.setLength(sb.length() - 1);
					return true;
				} else if(nextChar == 0xFFFF){
					continue;
				} else {
					return false;//unexpected char in chain end!
				}
			}
		} else if(sb.charAt(sb.length() - 1) == 0){
			sb.setLength(sb.length() - 1);
			return true;
		}
		return true;
	}
	
	private static boolean findAllNameExtensions(byte[] block, byte[] lastBlock, int pos, int expectedCheckSum, int depth, StringBuilder nameRecorder, ByteArrayOutputStream nameEntriesRecorder) {
		pos -= 32;//seek to the previous entry.
		if(pos < 0){
			block = lastBlock;
			if(block == null){
				System.out.println("EOF?");
				return false;
			}
			pos += block.length;
		}
		if(pos < 0) return false;//out of range
		nameEntriesRecorder.write(block, pos, 32);
		
		if((block[pos] & 0x3F) != depth) {//not the right order (ignoring end-node flags) ?
			return false;
		}
		if(block[pos + 11] != 0x0F) return false;//not an extension entry?
		if(block[pos + 26] != 0 && block[pos + 27] != 0) return false;//first-cluster value != 0?
		if((block[pos + 13] & 0xFF) != expectedCheckSum) return false;//this entry does'nt seem to be related to our parent or is damaged!
		
		nameRecorder.append(newStr(block, pos + 1, 10)); // +5 chars
		nameRecorder.append(newStr(block, pos + 14, 12));// +6 chars
		nameRecorder.append(newStr(block, pos + 28, 4)); // +2 chars
		
		if((block[pos] & 0xFF) == (0x40 | depth)) {//is end-node?
			//crc compare
			//int crc = ChkSum(block, pos);
			//System.out.println("crc="+crc + ", expectedCheckSum="+expectedCheckSum);
			return true;
		} else {//find next node until completely resolved.
			return findAllNameExtensions(block, lastBlock, pos, expectedCheckSum, depth + 1, nameRecorder, nameEntriesRecorder);
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

	private static int ChkSum(byte[] pFcbName, int offset) {
		if (pFcbName.length - offset < 11) {
			throw new IllegalArgumentException("The input array must be 11 bytes long.");
		}

		int Sum = 0;

		for (int FcbNameLen = 0; FcbNameLen < 11; FcbNameLen++) {
			// Rotate right operation on Sum
			Sum = (((Sum & 1) << 7) | ((Sum & 0xFF) >> 1)) + (pFcbName[FcbNameLen + offset] & 0xFF);
			// Sum = (((Sum & 1) != 0 ? 0x80 : 0) + ((Sum & 0xFF) >> 1) +
			// (pFcbName[FcbNameLen + offset] & 0xFF));
		}

		return Sum & 0xFF;
	}
	
	private static boolean isValidEntry(byte[] entry, int off, boolean debug) {
		if (ByteArrayUtils.isEmpty(entry, off, 32)) {
			return false;
		}
		// Check if the entry is not empty
		if (entry[off] == 0x00) {
			return false;
		}

		// Check for deleted entries
		if (entry[off] == (byte) 0xE5) {
			return false;
		}

		if (entry[off + 12] != 0) {// expect const 0
			return false;
		}
if(debug)System.out.println("P1");
		if ((entry[off + 13] & 0xFF) >= 200) {// invalid tenths-of-sec-value?
			return false;
		}

		// Check attribute
		int attr = entry[off + 11] & 0xFF;
		/*
		//all valid attributes.
		if(!(attr == 0x01 || attr == 0x10 || attr == 0x20 || attr == 0x30 || attr == 0x08 || attr == 0x40 || attr == 0x0F)){
			return false;
		}
		*/
		if((attr & 0xC0) != 0){
			return false;
		}
		if(entry[0] == '.'){
			if(entry[1] == '.' || entry[1] == ' '){
				for(int i=2; i<11; i++){
					if(entry[i] != ' ') return false;
				}
				return true;
			}
		}
		if(entry[0] == 0xE5 || entry[0] == 0){
			entry[0] = 'A';
		}
		return testFor8Dot3Name(entry, off, true);
	}
	
	public static boolean testFor8Dot3Name(byte[] buffer, int offset, boolean disallowUpper128chars) {
		if (buffer.length - offset < 11) return false; // Too few data.

		// Check for special cases
		if (buffer[offset] == 0x00) return false; // All following entries are free
		if (buffer[offset] == 0x20) return false; // Names cannot start with a space

		// Check for 0xE5 and 0x05 handling for KANJI
		if (buffer[offset] == (byte) 0xE5 || buffer[offset] == (byte) 0x05) {
			// 0xE5 is valid for KANJI, 0x05 can be used in place of 0xE5
			// We proceed further validation
		}
		
		if(disallowUpper128chars){//technically allowed, practically ultra-rare.
			for (int i = 0; i < 11; i++) {
				if((buffer[offset + i] & 0xFF) > 127) return false;
			}
		}

		// Illegal character values for the name
		int[] illegalValues = { 0x22, 0x2A, 0x2B, 0x2C, 0x2E, 0x2F, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F, 0x5B, 0x5C, 0x5D, 0x7C };
		
		for (int i = 0; i < 11; i++) {
			int b = buffer[offset + i] & 0xFF;
			if (b < 0x20 || (b >= 0x61 && b <= 0x7A)) return false; // Illegal or lower-case
			for (int value : illegalValues) {
				if (b == value) return false; // Contains an illegal value
			}
		}

		// Verify main part of the name (first 8 bytes)
		boolean hasSpaceInMainPart = false;
		for (int i = 0; i < 8; i++) {
			if (buffer[offset + i] == 0x20) {
				hasSpaceInMainPart = true;
			} else if (hasSpaceInMainPart) {
				return false; // Non-space character after space in main part
			}
		}

		// Verify extension part of the name (last 3 bytes)
		boolean hasSpaceInExtension = false;
		for (int i = 8; i < 11; i++) {
			if (buffer[offset + i] == 0x20) {
				hasSpaceInExtension = true;
			} else if (hasSpaceInExtension) {
				return false; // Non-space character after space in extension
			}
		}

	    // Verify if the main part and extension part follow the space-padding rule
	    for (int i = 0; i < 8; i++) {
	        if (buffer[offset + i] == 0x20) {
	            // Ensure all subsequent bytes in the main part are spaces
	            for (int j = i; j < 8; j++) {
	                if (buffer[offset + j] != 0x20) return false;
	            }
	            break;
	        }
	    }

	    for (int i = 8; i < 11; i++) {
	        if (buffer[offset + i] == 0x20) {
	            // Ensure all subsequent bytes in the extension part are spaces
	            for (int j = i; j < 11; j++) {
	                if (buffer[offset + j] != 0x20) return false;
	            }
	            break;
	        }
	    }
	    
		return true; // Passed all checks
	}
	
}
