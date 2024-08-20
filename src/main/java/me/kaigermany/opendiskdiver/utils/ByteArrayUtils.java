package me.kaigermany.opendiskdiver.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteArrayUtils {
	public static long read64(byte[] buffer, int offset) {
		return ByteBuffer.wrap(buffer, offset, 8).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get();
	}

	public static int read32(byte[] buffer, int offset) {
		return ByteBuffer.wrap(buffer, offset, 4).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get();
	}

	public static int read16(byte[] buffer, int offset) {
		return ByteBuffer.wrap(buffer, offset, 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get() & 0xFFFF;
	}

	public static int read8(byte[] buffer, int offset) {
		return buffer[offset] & 0xFF;
	}
	
	public static long read48(byte[] buffer, int offset) {
		long lower = read32(buffer, offset) & 0xFFFFFFFFL;
		long upper = read16(buffer, offset + 4) & 0xFFFFL;
		return (upper << 32) | lower;
	}
	
	public static boolean isEmptySector(byte[] sector){
		for(int i=0; i<512; i++){
			if(sector[i] != 0) return false;
		}
		return true;
	}

	public static boolean isEmpty(byte[] data, int offset, int length) {
		length += offset;
		for(int i=offset; i<length; i++){
			if(data[i] != 0) return false;
		}
		return true;
	}
}
