package me.kaigermany.opendiskdiver.data.fat;

import java.io.IOException;
import java.io.InputStream;

import me.kaigermany.opendiskdiver.data.fat.FatReader.ExFatEntryObject;
import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class ExFatInputStream extends InputStream {
    private final ExFatChainTable fat;
    private final int clusterSize;
    private final long clusterHeapOffset;
    private final byte[] clusterBuffer;
    private final ReadableSource source;
    private int currentCluster;
    private int currentBufferPos;
    private long remainingBytes;

    public ExFatInputStream(ExFatEntryObject file, ExFatChainTable fat, int clusterSize, long clusterHeapOffset, ReadableSource source) {
        this.fat = fat;
        this.clusterSize = clusterSize;
        this.clusterHeapOffset = clusterHeapOffset;
        this.clusterBuffer = new byte[clusterSize * 512];
        this.source = source;
        this.currentCluster = (int) file.streamInfo.firstCluster;
        this.currentBufferPos = clusterBuffer.length;//0;
        this.remainingBytes = file.streamInfo.validDataLen;
        System.out.println("new ExFatInputStream() : start = " + currentCluster + ", len = " + remainingBytes);
        System.out.println("len2 = " + file.streamInfo.dataLen);
    }

    @Override
    public int read() throws IOException {
        // Read a single byte
        byte[] singleByte = new byte[1];
        int result = read(singleByte, 0, 1);
        return result == -1 ? -1 : singleByte[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remainingBytes <= 0) {
            return -1; // End of stream
        }

        int totalRead = 0;

        while (len > 0) {
            if (currentBufferPos >= clusterBuffer.length || remainingBytes == 0) {
                // Load the next cluster if needed
                if (!loadNextCluster()) {
                    break; // No more data
                }
            }

            int bytesToRead = Math.min(len, (int) Math.min(remainingBytes, clusterBuffer.length - currentBufferPos));
            System.arraycopy(clusterBuffer, currentBufferPos, b, off + totalRead, bytesToRead);

            currentBufferPos += bytesToRead;
            totalRead += bytesToRead;
            remainingBytes -= bytesToRead;
            len -= bytesToRead;
        }

        return totalRead == 0 ? -1 : totalRead;
    }

    private boolean loadNextCluster() throws IOException {
        if (currentCluster == -1) {
            return false; // No more clusters
        }
        System.out.println("currentCluster = " + currentCluster);
        // Read the cluster into the buffer
        source.readSectors(clusterHeapOffset + ((currentCluster & 0xFFFFFFFFL) * (long) clusterSize), clusterSize, clusterBuffer);
        currentCluster = fat.get(currentCluster); // Move to the next cluster
        System.out.println("currentCluster -> " + currentCluster);
        currentBufferPos = 0;
        return true;
    }

    @Override
    public void close() throws IOException {
        // Cleanup if needed
        super.close();
    }
}
