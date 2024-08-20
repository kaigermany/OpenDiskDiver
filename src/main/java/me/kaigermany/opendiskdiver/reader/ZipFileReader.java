package me.kaigermany.opendiskdiver.reader;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class ZipFileReader implements ReadableSource, Closeable {
	private ZipFile zip;
	private long maxNumSectors = -1, blockSizeInSectors = -1;
	private ZipEntry[] blockTable;
	
	private byte[] lastReadBlock;
	private int lastReadBlockLocation = -1;
	
	
	public ZipFileReader(File zipFile) throws ZipException, IOException {
		zip = new ZipFile(zipFile);
		Enumeration<? extends ZipEntry> entries = zip.entries();
		ZipEntry e;
		while(entries.hasMoreElements()){
			e = entries.nextElement();
			if(e.getName().equals("info.txt")){
				parseInfoFile(readFullEntry(e));
				break;
			}
		}
		
		if((maxNumSectors | blockSizeInSectors) == -1L){
			throw new IOException("Invalid or missing \"info.txt\" file entry");
		}
		
		long numBlocks = (maxNumSectors / blockSizeInSectors) + (maxNumSectors % blockSizeInSectors != 0 ? 1 : 0);
		if(numBlocks > Integer.MAX_VALUE){
			throw new IOException("There are too many Block entries for this Reader, please choose another implementation.");
		}
		blockTable = new ZipEntry[(int)numBlocks];
		
		entries = zip.entries();
		while(entries.hasMoreElements()){
			e = entries.nextElement();
			String name = e.getName();
			if(!name.equals("info.txt")){
				long sectorOffset = Long.parseLong(name);
				blockTable[(int)(sectorOffset / blockSizeInSectors)] = e;
			}
		}
	}

	private byte[] readBlock(int blockIndex) throws IOException {
		ZipEntry e = blockTable[blockIndex];
		if(e == null){
			return new byte[(int)blockSizeInSectors];
		}
		return readFullEntry(e);
	}
	
	private byte[] readFullEntry(ZipEntry e) throws IOException {
		InputStream is = zip.getInputStream(e);
		int l = (int)e.getSize();
		ByteArrayOutputStream baos = new ByteArrayOutputStream(l > 0 ? l : 1024);
		copyStream(is, baos);
		return baos.toByteArray();
	}
	
	private void copyStream(InputStream is, OutputStream os) throws IOException {
		byte[] buf = new byte[4096];
		int l;
		while((l = is.read(buf)) != -1){
			os.write(buf, 0, l);
		}
	}
	
	private void parseInfoFile(byte[] data) {
		String[] lines = new String(data).split("\n");
		for(String row : lines){
			if((row = row.trim()).length() > 0){
				String[] args = row.split("\\=");
				if(args[0].equals("driveSizeInSectors")){
					maxNumSectors = Long.parseLong(args[1]);
				} else if(args[0].equals("sectorsPerBlock")){
					blockSizeInSectors = Long.parseLong(args[1]);
				}
			}
		}
	}
	
	@Override
	public void readSectors(long sectorNumber, int sectorCount, byte[] buffer, int bufferOffset) throws IOException {
		//int firstBlock = (int)(sectorNumber / blockSizeInSectors);
		//int lastBlock = (int)((sectorNumber + sectorCount) / blockSizeInSectors);
		byte[] currentBlock = null;
		int currentBlockIndex = -1;
		
		while(sectorCount > 0){
			int blockIndex = (int)(sectorNumber / blockSizeInSectors);
			int blockRelativeOffset = (int)(sectorNumber % blockSizeInSectors);
			
			if(blockIndex == lastReadBlockLocation){
				currentBlock = lastReadBlock;
				currentBlockIndex = lastReadBlockLocation;
			} else {
				currentBlock = readBlock(blockIndex);
				currentBlockIndex = blockIndex;
			}
			int len = (int)((blockSizeInSectors - blockRelativeOffset) * 512);
			int bytesLeft = buffer.length - bufferOffset;
			if(len > bytesLeft) len = bytesLeft;
			System.arraycopy(currentBlock, blockRelativeOffset * 512, buffer, bufferOffset, len);
			if(len == bytesLeft) break;
			bufferOffset += len;
			//if(len < 512) len = 512;
			len /= 512; // switch from bytes to sectors.
			sectorCount -= len;
			sectorNumber += len;
		}
		lastReadBlock = currentBlock;
		lastReadBlockLocation = currentBlockIndex;
		
	}

	@Override
	public long numSectors() {
		return maxNumSectors;
	}

	@Override
	public void close() throws IOException {
		zip.close();
	}

}
