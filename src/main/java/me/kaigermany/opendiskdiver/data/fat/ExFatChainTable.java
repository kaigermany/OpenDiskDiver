package me.kaigermany.opendiskdiver.data.fat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class ExFatChainTable {
	final int[][] chainTables;
	
	public ExFatChainTable(int len){
		int pages = (len >> 24) + ((len & 0x00FFFFFF) != 0 ? 1 : 0);
		chainTables = new int[pages][];
		int c = 0;
		for(int i=0; i<len; i+=0x01000000){
			chainTables[c++] = new int[Math.min(0x01000000, len - i)];
		}
	}
	
	public void load(ReadableSource source, long offset) throws IOException {
		final int numSectorsPerPage = 0x01000000 / 512;
		final int numBytesPerPage = 0x01000000 * 4;
		byte[] pageBuffer = new byte[numBytesPerPage];
		//allocateDirect() can maybe unsupported on some rare computers.
		ByteBuffer bb = ByteBuffer.allocate(numBytesPerPage).order(ByteOrder.LITTLE_ENDIAN);
		IntBuffer ib = bb.asIntBuffer();
		for(int i=0; i<chainTables.length - 1; i++){
			source.readSectors(offset + i * numSectorsPerPage, numSectorsPerPage, pageBuffer);
			bb.rewind();
			bb.put(pageBuffer);
			ib.rewind();
			ib.get(chainTables[i]);
		}
		int[] lastEntryPtr = chainTables[chainTables.length - 1];
		int lastSectorCount = lastEntryPtr.length * 4;
		lastSectorCount = lastSectorCount / 512 + ((lastSectorCount & 511) != 0 ? 1 : 0);
		source.readSectors(offset + (chainTables.length - 1) * numSectorsPerPage, lastSectorCount, pageBuffer);
		bb.rewind();
		bb.put(pageBuffer);
		ib.rewind();
		ib.get(lastEntryPtr, 0, lastEntryPtr.length);
		
	}
	
	public int get(int pos){
		return chainTables[(pos >> 24) & 0xFF][pos & 0x00FFFFFF];
	}
}
