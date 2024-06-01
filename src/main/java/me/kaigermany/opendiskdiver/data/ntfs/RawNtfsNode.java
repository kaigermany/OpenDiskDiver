package me.kaigermany.opendiskdiver.data.ntfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;

public class RawNtfsNode {
	private byte[] data;
	
	public RawNtfsNode(byte[] mftEntryBytes, NtfsConfig config) throws IOException {
		fixupRawMftdata(mftEntryBytes, config);
		this.data = mftEntryBytes;
	}
	
	public boolean isActiveEntry() throws IOException {//is node in use?
		int Flags = ByteArrayUtils.read16(data, 22);
		return (Flags & 1) == 1;
	}

	public boolean isValid(int length) throws IOException {
		if(!isActiveEntry()) return false;
		int Type = ByteArrayUtils.read32(data, 0);
		if(Type != 0x454c4946) return false; //if not file signature -> return
		//This is an inode extension used in an AttributeAttributeList of another inode, don't parse it
		long baseInode = ByteArrayUtils.read48(data, 32);
        if (baseInode != 0) return false;
        
		int AttributeOffset = ByteArrayUtils.read16(data, 20);
		if (AttributeOffset >= length){
			throw new IOException("Error: attributes are outside the FILE record, the MFT may be corrupt.");
		}
		int BytesInUse = ByteArrayUtils.read32(data, 24);
        if (BytesInUse > length){
        	throw new IOException("Error: the node record is bigger than the size of the buffer, the MFT may be corrupt.");
        }
        
        return true;
	}

	public byte[] getData() {
		return data;
	}
	
	private static void fixupRawMftdata(byte[] mft, NtfsConfig config) throws IOException {
		ByteBuffer buffer = ByteBuffer.wrap(mft).order(ByteOrder.LITTLE_ENDIAN);
		if(buffer.asIntBuffer().get() != 0x454c4946) return; //if not file signature -> return
		buffer.position(4);
		int UsaOffset = buffer.asShortBuffer().get() & 0xFFFF;
		buffer.position(4+2);
		int UsaCount = buffer.asShortBuffer().get() & 0xFFFF;
        int increment = (int)config.BytesPerSector >>> 1;

        int Index = increment - 1;
        for (int i = 1; i < UsaCount; i++) {
        	if ((Index << 1) >= config.BytesPerMftRecord) throw new IOException("USA data indicates that data is missing, the MFT may be corrupt.");
            // Check if the last 2 bytes of the sector contain the Update Sequence Number.
        	//if (wordBuffer[Index] != UpdateSequenceArray[0])
        	if (mft[Index << 1] != mft[UsaOffset] || mft[(Index << 1)+1] != mft[UsaOffset+1])
                throw new IOException("USA fixup word is not equal to the Update Sequence Number, the MFT may be corrupt.");
            /* Replace the last 2 bytes in the sector with the value from the Usa array. */
            //wordBuffer[Index] = UpdateSequenceArray[i];
        	mft[Index << 1] = mft[UsaOffset + (i*2)];
        	mft[(Index << 1) + 1] = mft[UsaOffset + (i*2) + 1];
            Index = Index + increment;
        }
	}
}
