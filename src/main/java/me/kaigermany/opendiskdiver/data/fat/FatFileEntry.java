package me.kaigermany.opendiskdiver.data.fat;

import java.io.IOException;
import java.io.InputStream;

import me.kaigermany.opendiskdiver.data.fat.FatReader.FatFile;
import me.kaigermany.opendiskdiver.datafilesystem.FileEntry;
import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class FatFileEntry extends FileEntry {
	private final int[] clustorList;
	private final long bytesPerClustor;
	private final ReadableSource source;
	
	public FatFileEntry(FatFile e, ReadableSource source, long clustorSizeInBytes) {
		super(e.nameOnly, e.name, e.fileSize, e.age);
		this.clustorList = e.clustors;
		this.source = source;
		this.bytesPerClustor = clustorSizeInBytes;
	}

	@Override
	public InputStream openInputStream() {
		return new InputStream(){
			int clusterIndexPos = -1;
			byte[] currentClustor = null;
			int maxLen;
			int currentPos;
			@Override
			public int read() throws IOException {
				byte[] a = new byte[1];
				int l = read(a, 0, 1);
				return l == -1 ? -1 : (a[0] & 0xFF);
			}

			@Override
			public int read(byte[] buf, int off, int len) throws IOException {
				if(currentClustor == null){
					if(clusterIndexPos >= clustorList.length) return -1;
					clusterIndexPos++;
					currentClustor = readCluster(clusterIndexPos);
					if(currentClustor == null) return -1;
					maxLen = Math.min(currentClustor.length, (int)(bytesPerClustor * clustorList.length - FatFileEntry.super.size));
					currentPos = 0;
				}
				int maxCopyLen = Math.min(len, maxLen - currentPos);
				System.arraycopy(currentClustor, currentPos, buf, off, maxCopyLen);
				currentPos += maxCopyLen;
				if(currentPos >= maxLen){
					currentClustor = null;
				}
				return maxCopyLen;
			}
			
			private byte[] readCluster(int index) throws IOException {
				if(index >= clustorList.length) return null;
				int pos = clustorList[index];
				byte[] buf = new byte[(int)bytesPerClustor];
				source.readSectors(pos / 512, (int)(bytesPerClustor / 512), buf);
				return buf;
			}
		};
	}
}
