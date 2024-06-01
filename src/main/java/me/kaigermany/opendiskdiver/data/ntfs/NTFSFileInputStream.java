package me.kaigermany.opendiskdiver.data.ntfs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class NTFSFileInputStream extends InputStream {
	private ArrayList<long[]> runs;
	private long fileBytesLeft;
	// private long readPos;
	private ReadableSource source;

	public NTFSFileInputStream(NtfsStream stream, NtfsConfig config, ReadableSource source) {
		this.source = source;
		long[] out = stream.getFragments();
		fileBytesLeft = stream.Size;
		runs = new ArrayList<long[]>((out.length - 2) / 2);
		int off = 0;
		for (int i = 0; i < out.length - 2; i += 2) {
			long offset = out[i + 1];
			long length = out[i + 2] - out[i];
			runs.add(new long[] { offset * config.clusterSize, Math.min(length * config.clusterSize, stream.Size - off) });
			off += length * config.clusterSize;
		}
	}

	public int read() throws IOException {
		byte[] a = new byte[1];
		int l = read(a, 0, 1);
		if (l <= 0) return -1;
		return a[0] & 0xFF;
	}

	public int read(byte[] buffer, int offset, int len) throws IOException {
		if (runs.size() == 0 || fileBytesLeft == 0)
			return -1;
		long[] currentRun = runs.get(0);
		int new_len = (int) Math.min(currentRun[1], len);

		byte[] data = readAt(currentRun[0], new_len, source);

		currentRun[0] += new_len;
		currentRun[1] -= new_len;
		fileBytesLeft -= new_len;
		// readPos += new_len;
		if (currentRun[1] == 0) runs.remove(0);

		for (int i = 0; i < new_len; i++)
			buffer[i + offset] = data[i];

		if (new_len < len) {
			int l = read(buffer, offset + new_len, len - new_len);
			if (l == -1) {
				return new_len;
			} else {
				return new_len + l;
			}
		}

		return new_len;
	}

	public long skip(long bytes) throws IOException {
		long bytesSkipped = 0;
		while (bytes > 0 && runs.size() > 0 && fileBytesLeft > 0) {
			long[] currentRun = runs.get(0);
			long skipRunBytes = Math.min(currentRun[1], bytes);
			currentRun[0] += skipRunBytes;
			currentRun[1] -= skipRunBytes;
			bytes -= skipRunBytes;
			bytesSkipped += skipRunBytes;
			if (currentRun[1] == 0) runs.remove(0);
		}
		if (runs.size() == 0) System.out.println("skip: seeked to EOF!");
		return bytesSkipped;
	}
	
	private static byte[] readAt(long byteOffset, long byteLen, ReadableSource source) throws IOException {
    	int sectorCount = (int)clampExp(byteLen, 512);
    	byte[] buffer = new byte[sectorCount];
    	source.readSectors(byteOffset / 512, sectorCount / 512, buffer, 0);
    	return buffer;
    }

	private static long clampExp(long val, long step) {
		long diff = val % step;
		if (diff == 0) return val;
		return val + step - diff;
	}
}
