package me.kaigermany.opendiskdiver.writer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;

public class ZipFileWriter {
	public static void write(ReadableSource reader, File out, int blockSizeInSectors) throws IOException {
		ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new DirectFileOutputStream(out), 128 << 20));
		final long maxNumSectors = reader.numSectors();
		final long numExpectedBlocks = maxNumSectors / blockSizeInSectors;
		
		//zos.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);
		
		zos.putNextEntry(new ZipEntry("info.txt"));
		zos.write((
				"driveSizeInSectors=" + maxNumSectors + "\r\n" +
				"sectorsPerBlock=" + blockSizeInSectors + "\r\n"
				).getBytes());
		zos.closeEntry();
		
		byte[] block = new byte[512 * blockSizeInSectors];
		long offset = 0;
		int blockCount = 0;
		while(offset < maxNumSectors){
			long sectorsToCopy = maxNumSectors - offset;
			if(sectorsToCopy > blockSizeInSectors) sectorsToCopy = blockSizeInSectors;
			
			reader.readSectors(offset, (int)sectorsToCopy, block);
			
			if(!ByteArrayUtils.isEmpty(block, 0, (int)(sectorsToCopy * 512))){
				zos.putNextEntry(new ZipEntry(String.valueOf(offset)));
				zos.write(block, 0, (int)(sectorsToCopy * 512));
				zos.closeEntry();
			}
			
			offset += sectorsToCopy;
			
			blockCount++;
			System.out.println(blockCount + " / " + numExpectedBlocks);
		}
		zos.flush();
		zos.close();
	}
}
