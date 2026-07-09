package me.kaigermany.opendiskdiver.writer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;

public class MultipartZipFileWriter implements Writer {
	public long splitSize = 0x00000000FFFFFFFFL;
	private int blockSizeInSectors = (1 << 20) / 512;
	private ZipOutputStream zos;
	private byte[] writeBuffer;
	private int writePointer = 0;
	private long writtenSectorOffset = 0;
	private StringBuilder damagedSectorRecorder = null;
	
	private String basePath;
	private int fileIndex = 0;
	private ByteArrayOutputStream zipOutputwriteBuffer = new ByteArrayOutputStream(blockSizeInSectors);
	private OutputStream currentOutputFileChannel;
	private long bytesWrittenIntoCurrentOutputFile;
	
	public MultipartZipFileWriter(){}
	
	@Override
	public void create(File file, ReadableSource readerReference) throws IOException {
		basePath = file.getAbsolutePath();
		zipOutputwriteBuffer.reset();
		zos = new ZipOutputStream(zipOutputwriteBuffer);
		currentOutputFileChannel = openNextFileChannel();
		
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

	private OutputStream openNextFileChannel() throws IOException {
		fileIndex++;
		File file;
		if(fileIndex < 10){
			file = new File(basePath + ".00" + fileIndex);
		} else if(fileIndex < 100){
			file = new File(basePath + ".0" + fileIndex);
		} else {
			file = new File(basePath + "." + fileIndex);
		}
		return new DirectFileOutputStream(file);
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
	public void writePlaceholderSector() throws IOException {
		if(damagedSectorRecorder == null) damagedSectorRecorder = new StringBuilder();
		damagedSectorRecorder.append(writtenSectorOffset + (writePointer / 512)).append("\r\n");
		write(new byte[512], 512);
	}

	@Override
	public void close() throws IOException {
		flushBuffer();
		
		zos.flush();
		
		if(damagedSectorRecorder != null){
			zos.putNextEntry(new ZipEntry("invalid_sectors.txt"));
			zos.write(damagedSectorRecorder.toString().getBytes());
			damagedSectorRecorder = null;
			zos.closeEntry();
		}
		
		zos.close();
		zos = null;
		
		finishOutputFile();
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
		}
		writtenSectorOffset += writePointer / 512;
		writePointer = 0;
		
		checkSwitchOutputFile();
	}

	private void checkSwitchOutputFile() throws IOException {
		long remainingSpace = splitSize - bytesWrittenIntoCurrentOutputFile;
		long bytesNeeded = zipOutputwriteBuffer.size();
		
		if(bytesNeeded >= remainingSpace){
			//file full. then open a new one.
			currentOutputFileChannel.close();
			currentOutputFileChannel = openNextFileChannel();
			bytesWrittenIntoCurrentOutputFile = 0;
		}
		
		//write and clear local buffer.
		zipOutputwriteBuffer.writeTo(currentOutputFileChannel);
		zipOutputwriteBuffer.reset();
		bytesWrittenIntoCurrentOutputFile += bytesNeeded;
	}

	private void finishOutputFile() throws IOException {
		checkSwitchOutputFile();
		currentOutputFileChannel.close();
	}
}
