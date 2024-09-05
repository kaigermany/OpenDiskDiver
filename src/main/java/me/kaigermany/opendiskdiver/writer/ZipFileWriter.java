package me.kaigermany.opendiskdiver.writer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
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
	
	public static class DirectFileOutputStream extends OutputStream {
		private RandomAccessFile raf;
		private long len;
		public DirectFileOutputStream(File out) throws IOException {
			if(out.exists()) out.delete();
			out.createNewFile();
			raf = new RandomAccessFile(out, "rwd");
		}
		
		@Override
		public void write(int b) throws IOException {
			raf.seek(len);
			raf.write(b);
			len++;
		}
		
		@Override
		public void write(byte[] buf) throws IOException {
			raf.seek(len);
			raf.write(buf);
			len += buf.length;
		}
		
		@Override
		public void write(byte[] buf, int offset, int length) throws IOException {
			raf.seek(len);
			raf.write(buf, offset, length);
			len += length;
		}
		
		@Override
		public void flush() throws IOException {}
		
		@Override
		public void close() throws IOException {
			if(raf != null){
				raf.close();
				raf = null;
			}
		}
		
		@Override
		protected void finalize() throws Throwable {
			close();
			super.finalize();
		}
	}
}
