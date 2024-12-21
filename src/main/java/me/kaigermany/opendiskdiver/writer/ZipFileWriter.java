package me.kaigermany.opendiskdiver.writer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;

public class ZipFileWriter implements Writer {
	private ZipOutputStream zos;
	private int blockSizeInSectors = (1 << 20) / 512;
	private byte[] writeBuffer;
	private int writePointer = 0;
	private long writtenSectorOffset = 0;
	
	public ZipFileWriter(){}
	
	@Override
	public void create(File file, ReadableSource readerReference) throws IOException {
		zos = new ZipOutputStream(new BufferedOutputStream(new DirectFileOutputStream(file), 128 << 20));
		//zos.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);
		final long maxNumSectors = readerReference.numSectors();
		//numExpectedBlocks = maxNumSectors / blockSizeInSectors;
		zos.putNextEntry(new ZipEntry("info.txt"));
		zos.write((
				"driveSizeInSectors=" + maxNumSectors + "\r\n" +
				"sectorsPerBlock=" + blockSizeInSectors + "\r\n"
				).getBytes());
		zos.closeEntry();
		writeBuffer = new byte[blockSizeInSectors * 512];
	}

	@Override
	public void write(byte[] buf, int numBytes) throws IOException {
		int off = 0;
		int len;
		while((len = fillWriteBuffer(buf, off, numBytes)) != 0){
			off += len;
			numBytes -= len;
		}
	}

	@Override
	public void close() throws IOException {
		zos.flush();
		zos.close();
		zos = null;
	}

	private int fillWriteBuffer(byte[] buf, int off, int numBytes) throws IOException {//fill local buffer, flushes it if full.
		int count = Math.min(writeBuffer.length - writePointer, numBytes);
		
		for(int i=0; i<count; i++){
			writeBuffer[writePointer + i] = buf[off + i];
		}
		writePointer += count;
		
		if(writePointer == writeBuffer.length){
			flushBuffer();
		}
		
		return count;
	}
	
	private void flushBuffer() throws IOException {
		if(writePointer == 0) return;
		if(!ByteArrayUtils.isEmpty(writeBuffer, 0, writePointer)){
			zos.putNextEntry(new ZipEntry(String.valueOf(writtenSectorOffset)));
			zos.write(writeBuffer, 0, writePointer);
			zos.closeEntry();
			writtenSectorOffset += writePointer / 512;
		}
		writePointer = 0;
	}
}
