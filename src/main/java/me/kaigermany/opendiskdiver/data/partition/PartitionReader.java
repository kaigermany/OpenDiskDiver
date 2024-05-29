package me.kaigermany.opendiskdiver.data.partition;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class PartitionReader {
	private ArrayList<Partition> partitions;
	
	public PartitionReader(ReadableSource source) throws IOException {
		partitions = readMBR(source);
		if (partitions.size() == 1 && partitions.get(0).isGPT) {
			partitions = readGPT(source, partitions.get(0).offset);
		}
	}
	
	public ArrayList<Partition> getPartitions(){
		return partitions;
	}

	private static final ArrayList<Partition> readMBR(ReadableSource source) throws IOException {
		byte[] sector = new byte[512];
		source.readSector(0, sector);
		// https://en.wikipedia.org/wiki/Master_boot_record
		ArrayList<Partition> out = new ArrayList<Partition>(4);
		if ((sector[380] & 0xFF) == 0x5A && (sector[381] & 0xFF) == 0xA5) {
			for (int index = 0; index < 8; index++) {
				int off = 494 - (index * 16);
				int offset = readInt32(sector, off + 8);
				if (offset != 0){
					out.add(new Partition(offset, readInt32(sector, off + 12), sector[off + 4] & 0xFF));
				}
			}
		} else if ((sector[252] & 0xFF) == 0xAA && (sector[253] & 0xFF) == 0x55) {
			for (int index = 0; index < 16; index++) {
				int off = 254 + (index * 16);
				int offset = readInt32(sector, off + 8);
				if (offset != 0){
					out.add(new Partition(offset, readInt32(sector, off + 12), sector[off + 4] & 0xFF));
				}
			}
		} else {
			for (int index = 0; index < 4; index++) {
				int off = 446 + (index * 16);
				int offset = readInt32(sector, off + 8);
				if (offset != 0){
					out.add(new Partition(offset, readInt32(sector, off + 12), sector[off + 4] & 0xFF));
				}
			}
		}
		return out;
	}
	
	private static final ArrayList<Partition> readGPT(ReadableSource source, long offset) throws IOException {
		byte[] sector = new byte[512];
		source.readSector(offset, sector);
		if (!testForGPTHeader(sector)) {
			throw new IOException("Missing GPT Partition Header!");
		}
		long entryLocation = readNumLong(sector, 72);
		int entryCount = readInt32(sector, 80);
		int entrySize = readInt32(sector, 84);

		ArrayList<Partition> out = new ArrayList<Partition>(entryCount);

		sector = new byte[(int) clampExp(entryCount * entrySize, 512)];
		source.readSectors(entryLocation, (sector.length / 512), sector);

		for (int i = 0; i < entryCount; i++) {
			int off = i * entrySize;
			long partitionOffset = readNumLong(sector, off + 32);
			long partitionLen = readNumLong(sector, off + 40) - partitionOffset;
			if (partitionOffset == 0 && partitionLen == 0) {
				continue;
			}
			char[] nameBuf = new char[72 / 2];
			ByteBuffer.wrap(sector, off + 56, 72).order(java.nio.ByteOrder.LITTLE_ENDIAN).asCharBuffer().get(nameBuf);
			out.add(new Partition(partitionOffset, partitionLen, new String(nameBuf)));
		}
		return out;
	}
	
	private static int readInt32(byte[] buffer, int offset) {
		return (buffer[offset] & 0xFF)
				| ((buffer[offset + 1] & 0xFF) << 8)
				| ((buffer[offset + 2] & 0xFF) << 16)
				| (buffer[offset + 3] << 24);
	}
	
	private static boolean testForGPTHeader(byte[] a) {
		return (a[0] & 0xFF) == 0x45 
				&& (a[1] & 0xFF) == 0x46 
				&& (a[2] & 0xFF) == 0x49 
				&& (a[3] & 0xFF) == 0x20
				&& (a[4] & 0xFF) == 0x50 
				&& (a[5] & 0xFF) == 0x41
				&& (a[6] & 0xFF) == 0x52
				&& (a[7] & 0xFF) == 0x54;
	}

	private static long readNumLong(byte[] b, int off) {
		long val = 0;
		for (int i = 0; i < 8; i++) {
			val |= (b[off + i] & 0xFFL) << (i << 3);
		}
		return val;
	}

	private static long clampExp(long val, long step) {
		long diff = val % step;
		if (diff == 0) return val;
		return val + step - diff;
	}
}
