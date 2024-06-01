package me.kaigermany.opendiskdiver.data.ntfs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class NTFSCompressedFileInputStream extends InputStream {
	public ArrayList<long[]> streamMap;
	public long maxLen;
	public byte[] currentBuffer = new byte[0];
	public int bufferOffset;
	public long byteCounter = 0;

	private ReadableSource source;

	public NTFSCompressedFileInputStream(NtfsStream stream, NtfsConfig config, ReadableSource source) {
		this.source = source;
		this.maxLen = stream.Size;
		this.streamMap = new ArrayList<long[]>();
		long[] out = stream.getFragments();
		for (int i = 0; i < out.length - 2; i += 2) {
			long vClustor = out[i];
			long rClustor = out[i + 1];
			long len = (out[i + 2] - vClustor);
			// System.out.println("vClustor="+vClustor+", rClustor="+rClustor);
			if (rClustor != -1) streamMap.add(new long[] { rClustor * config.clusterSize, len * config.clusterSize });
		}
	}

	private static byte[] readAt(long byteOffset, long byteLen, ReadableSource source) throws IOException {
		int sectorCount = (int) clampExp(byteLen, 512);
		byte[] buffer = new byte[sectorCount];
		source.readSectors(byteOffset / 512, sectorCount / 512, buffer, 0);
		return buffer;
	}

	private static long clampExp(long val, long step) {
		long diff = val % step;
		if (diff == 0) return val;
		return val + step - diff;
	}

	public int read() throws IOException {
		byte[] a = new byte[1];
		int l = read(a, 0, 1);
		if (l <= 0) return -1;
		return a[0] & 0xFF;
	}

	public int read(byte[] buffer, int offset, int len) throws IOException { 
		// is save-reading (trying to keep len declatation maximal used)
		if (bufferOffset >= currentBuffer.length) {
			if (streamMap == null || streamMap.size() == 0)
				return -1;
			long[] offsetAndLen = streamMap.remove(0);
			byte[] rawData = readAt(offsetAndLen[0], (int) offsetAndLen[1], source);
			currentBuffer = decompressBlocks(rawData);;
			bufferOffset = 0;
		}
		int maxLen = Math.min(len, currentBuffer.length - bufferOffset);
		System.arraycopy(currentBuffer, bufferOffset, buffer, offset, maxLen);
		bufferOffset += maxLen;
		byteCounter += maxLen;
		if (len == maxLen) return maxLen;
		int l2 = read(buffer, offset + maxLen, len - maxLen);
		if (l2 == -1) return maxLen;
		return maxLen + l2;
	}
	
	private static byte[] decompressBlocks(byte[] compressedSource) throws IOException {
		int size = LZNT1.CalcDecompressesSize(compressedSource, 0, compressedSource.length);
		byte[] out = new byte[size];
		int len = LZNT1.Decompress(compressedSource, 0, compressedSource.length, out, 0);
		if(len != size) throw new IOException("unexpected sizes: prediced:" + size + ", got: " + len);
		return out;
	}

	public static class LZNT1 {// https://github.com/perpetual-motion/discutils/blob/master/src/Ntfs/LZNT1.cs
		private static final int SubBlockIsCompressedFlag = 0x8000;
		private static final int SubBlockSizeMask = 0x0fff;

		// LZNT1 appears to ignore the actual block size requested, most likely
		// due to
		// a bug in the decompressor, which assumes 4KB block size. To be
		// bug-compatible,
		// we assume each block is 4KB on decode also.
		private static final int FixedBlockSize = 0x1000;

		private static final byte[] s_compressionBits = new byte[4096];

		static {
			byte offsetBits = 0;
			int y = 0x10;
			for (int x = 0; x < s_compressionBits.length; x++) {
				s_compressionBits[x] = (byte) (4 + offsetBits);
				if (x == y) {
					y <<= 1;
					offsetBits++;
				}
			}
		}

		public static int Decompress(byte[] source, int sourceOffset, int sourceLength, byte[] decompressed, int decompressedOffset) {
			int sourceIdx = 0;
			int destIdx = 0;

			while (sourceIdx < sourceLength) {
				int header = (source[sourceOffset + sourceIdx] & 0xFF) | (((source[sourceOffset + sourceIdx + 1]) & 0xFF) << 8);
				sourceIdx += 2;

				// Look for null-terminating sub-block header
				if (header == 0) {
					break;
				}

				if ((header & SubBlockIsCompressedFlag) == 0) {
					int blockSize = (header & SubBlockSizeMask) + 1;
					System.arraycopy(source, sourceOffset + sourceIdx, decompressed, decompressedOffset + destIdx, blockSize);
					sourceIdx += blockSize;
					destIdx += blockSize;
				} else {
					// compressed
					int destSubBlockStart = destIdx;
					int srcSubBlockEnd = sourceIdx + (header & SubBlockSizeMask) + 1;
					while (sourceIdx < srcSubBlockEnd) {
						byte tag = source[sourceOffset + sourceIdx];
						++sourceIdx;

						for (int token = 0; token < 8; ++token) {
							// We might have hit the end of the sub block
							// whilst still working though
							// a tag - abort if we have...
							if (sourceIdx >= srcSubBlockEnd) {
								break;
							}

							if ((tag & 1) == 0) {
								if (decompressedOffset + destIdx >= decompressed.length) {
									return destIdx;
								}

								decompressed[decompressedOffset + destIdx] = source[sourceOffset + sourceIdx];
								++destIdx;
								++sourceIdx;
							} else {
								int lengthBits = (16 - s_compressionBits[destIdx - destSubBlockStart]);
								int lengthMask = ((1 << lengthBits) - 1);

								int phraseToken = (source[sourceOffset + sourceIdx] & 0xFF) | (((source[sourceOffset + sourceIdx + 1]) & 0xFF) << 8);
								sourceIdx += 2;

								int destBackAddr = destIdx - (phraseToken >> lengthBits) - 1;
								int length = (phraseToken & lengthMask) + 3;

								for (int i = 0; i < length; ++i) {
									decompressed[decompressedOffset + destIdx++] = decompressed[decompressedOffset + destBackAddr++];
								}
							}

							tag >>= 1;
						}
					}

					// Bug-compatible - if we decompressed less than 4KB,
					// jump to next 4KB boundary. If
					// that would leave less than a 4KB remaining, abort
					// with data decompressed so far.
					if (decompressedOffset + destIdx + FixedBlockSize > decompressed.length) {
						return destIdx;
					}
				}
			}

			return destIdx;
		}
		
		public static int CalcDecompressesSize(byte[] source, int sourceOffset, int sourceLength) {
			int sourceIdx = 0;
			int destIdx = 0;

			while (sourceIdx < sourceLength) {
				int header = (source[sourceOffset + sourceIdx] & 0xFF) | (((source[sourceOffset + sourceIdx + 1]) & 0xFF) << 8);
				sourceIdx += 2;

				// Look for null-terminating sub-block header
				if (header == 0) {
					break;
				}

				if ((header & SubBlockIsCompressedFlag) == 0) {
					int blockSize = (header & SubBlockSizeMask) + 1;
					sourceIdx += blockSize;
					destIdx += blockSize;
				} else {
					// compressed
					int destSubBlockStart = destIdx;
					int srcSubBlockEnd = sourceIdx + (header & SubBlockSizeMask) + 1;
					while (sourceIdx < srcSubBlockEnd) {
						byte tag = source[sourceOffset + sourceIdx];
						++sourceIdx;

						for (int token = 0; token < 8; ++token) {
							// We might have hit the end of the sub block
							// whilst still working though
							// a tag - abort if we have...
							if (sourceIdx >= srcSubBlockEnd) {
								break;
							}

							if ((tag & 1) == 0) {
								++destIdx;
								++sourceIdx;
							} else {
								int lengthBits = (16 - s_compressionBits[destIdx - destSubBlockStart]);
								int lengthMask = ((1 << lengthBits) - 1);

								int phraseToken = (source[sourceOffset + sourceIdx] & 0xFF) | (((source[sourceOffset + sourceIdx + 1]) & 0xFF) << 8);
								sourceIdx += 2;

								int length = (phraseToken & lengthMask) + 3;
								destIdx += length;
							}

							tag >>= 1;
						}
					}
				}
			}

			return destIdx;
		}
	}
}
