package me.kaigermany.opendiskdiver.data.ntfs;

import java.io.InputStream;
import java.util.ArrayList;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class NtfsNode {
	// make the file appear in the rootdirectory by default
	public long ParentNodeIndex = NtfsReader.ROOTDIRECTORY;
	public int Attributes;
	public boolean isDir;
	public ArrayList<NtfsStream> streams = new ArrayList<NtfsStream>();
	public long lastEdited;
	public String Name;
	public long Size;
	public long NodeIndex;
	public boolean isSystemFile;

	public boolean isCompressed;
	public boolean isEncrypted;
	public boolean isSparse;
	
	private NtfsConfig config;
	private ReadableSource source;
	//config, source
	public NtfsNode(long NodeIndex, NtfsConfig config, ReadableSource source){
		this.NodeIndex = NodeIndex;
		this.config = config;
		this.source = source;
	}

	public NtfsStream SearchStream(int streamType, String streamName) {
		// since the number of streams is usually small, we can afford O(n)
		for (NtfsStream stream : streams) {
			if (stream.Type == streamType && strEquals(stream.Name, streamName)) {
				return stream;
			}
		}
		return null;
	}

	public NtfsStream SearchStream(int streamType) {
		// since the number of streams is usually small, we can afford O(n)
		for (NtfsStream stream : streams) {
			if (stream.Type == streamType) {
				return stream;
			}
		}
		return null;
	}

	private static boolean strEquals(String a, String b) {
		if (a == null) {
			return b == null;
		} else {
			return a.equals(b);
		}
	}

	public InputStream openInputStream() {
		NtfsStream stream = SearchStream(0x80, null);
		System.out.println("isCompressed="+isCompressed);
		if(isCompressed){
			return new NTFSCompressedFileInputStream(stream, config, source);
		} else {
			return new NTFSFileInputStream(stream, config, source);
		}
	}
}
