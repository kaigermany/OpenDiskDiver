package me.kaigermany.opendiskdiver.data.ntfs;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;

import me.kaigermany.opendiskdiver.reader.ReadableSource;

public class NtfsReader {
	private static final int AttributeType_AttributeData = 0x80;
	private static final int ROOTDIRECTORY = 5;
	private static final int Attributes_Directory = 16;
	
	public HashMap<String, Node> fileMap2;
	
	
	public NtfsConfig config;
	
	public Node[] nodes;
	
	public NtfsReader(ReadableSource source) throws IOException {
		config = new NtfsConfig(source);
		readMFT(source);
		fileMap2 = new HashMap<String, Node>(nodes.length * 2);
		System.gc();
		for(Node n : nodes) {
			if(n == null || /*n.isDir ||*/ n.streams.size() == 0) continue;
			//System.out.println(n.Name);
			try{
				String name = solveFilePath(n.NodeIndex);
				// https://winprotocoldoc.blob.core.windows.net/productionwindowsarchives/MS-FSCC/%5BMS-FSCC%5D.pdf
				for(Stream s : n.streams){
					if(s.Name != null && s.Name.equals("$I30")){
						s.Name = "$I30:$INDEX_ALLOCATION";
					} else if(s.Type != 0x80){
						String typeName = getStreamTypeName(s.Type);
						s.Name = (s.Name == null ? "" : s.Name) + ":" + typeName;
					}
				}
				fileMap2.put(name, n);
				n.isSystemFile |= name != null && name.charAt(0) == '$';
			}catch(Throwable e){
				if(e instanceof StackOverflowError){
					System.err.println(e);
				} else {
					e.printStackTrace();
				}
			}
		}
	}
	
	private static String getStreamTypeName(int id){
		switch(id){
			case 0x80: return "$DATA";
			case 0xA0: return "$INDEX_ALLOCATION";
			case 0xB0: return "$BITMAP";

			case 0x20: return "$ATTRIBUTE_LIST";
			case 0xE0: return "$EA";
			case 0xD0: return "$EA_INFORMATION";
			case 0x30: return "$FILE_NAME";
			case 0x90: return "$INDEX_ROOT";
			case 0x100: return "LOGGED_UTILITY_STREAM";
			case 0x40: return "OBJECT_ID";
			case 0xF0: return "$PROPERTY_SET";
			case 0xC0: return "$REPARSE_POINT";
			case 0x50: return "$SECURITY_DESCRIPTOR";
			case 0x10: return "$STANDARD_INFORMATION";
			//$SYMBOLIC_LINK Obsolete
			//$TXF_DATA Transactional NTFS data
			case 0x70: return "$VOLUME_INFORMATION";
			case 0x60: return "$VOLUME_NAME";
		}
		System.err.println("getStreamTypeName():  unknown Type " + id + "(0x"+Integer.toHexString(id)+")");
		return null;
	}
	
	private String solveFilePath(long nodeId) {
		try{
			Node n = nodes[(int)nodeId];
			if(n.ParentNodeIndex == nodeId){
				System.err.println("node dir loop detected! return \"\"; instead.");
				return "";
			}
			if(n.ParentNodeIndex > 5) return solveFilePath(n.ParentNodeIndex) + '\\' + n.Name;
			/*
			if(n.Name == null){
				Stream stream = n.streams.get(0);
				//System.out.println("stream:"+stream.Name);
			}
			*/
			return n.Name;
		}catch(NullPointerException e){
			e.printStackTrace();
			Node n = nodes[(int)nodeId];
			System.err.println("node: " + nodeId);
			if(n == null){
				System.err.println("node is NULL");
			} else {
				System.err.println("node-name: " + n.Name);
			}
			throw e;
		}
	}
	
	
	public void readMFT(ReadableSource source) throws IOException {
		Stream mftStream;
		{
			//step 1: read MFT entry
			byte[] mftEntryBytes = new byte[ (int)clampExp(config.BytesPerMftRecord, 512) ]; //align towards 512
			source.readSectors(config.MFT_Offset * config.clusterSize / 512, mftEntryBytes.length / 512, mftEntryBytes);
			
			RawNode temp = new RawNode(mftEntryBytes, config);
			
			Node node = temp.ProcessMftRecord((int)config.BytesPerMftRecord, null, config, source, 0, null);
			mftStream = SearchStream(node.streams, AttributeType_AttributeData);
		}
		NTFSFileInputStream nfis = new NTFSFileInputStream(mftStream, config, source);
		BufferedInputStream bis = new BufferedInputStream(nfis, 1 << 20);//1 MB cache

		int nodeCount = (int)(mftStream.Size / config.BytesPerMftRecord);
		System.out.println("nodeCount="+nodeCount);
		this.nodes = new Node[nodeCount];
		RawNode[] rawNodes = new RawNode[nodeCount];
		int bytesPerMftRecord = (int)config.BytesPerMftRecord;
		for(int nodeIndex=0; nodeIndex<nodeCount; nodeIndex++){
			byte[] buffer2 = new byte[bytesPerMftRecord];
	        int l = bis.read(buffer2, 0, bytesPerMftRecord);
	        if(l != (int)config.BytesPerMftRecord) throw new IOException("Missing data in MFT-Stream: readed only " + l + " bytes, expected " + config.BytesPerMftRecord + " bytes.");
	        RawNode node = new RawNode(buffer2, config);
	        if(node.isActiveEntry()){
	        	rawNodes[nodeIndex] = node;
	        }
		}
		bis = null;
		nfis = null;
		for(int nodeIndex=0; nodeIndex<nodeCount; nodeIndex++){
			RawNode rawNode = rawNodes[nodeIndex];
			if(rawNode != null && rawNode.isValid(bytesPerMftRecord)){
				rawNodes[nodeIndex] = null;
		        
		        Node node = this.nodes[nodeIndex] = rawNode.ProcessMftRecord(bytesPerMftRecord, mftStream, config, source, nodeIndex, rawNodes);
		        
		        if(node != null){
		        	node.isSystemFile = node.Name != null && node.Name.startsWith("$") && node.ParentNodeIndex == 5 && !node.Name.equals("$RECYCLE.BIN");
		        }
			}
		}
	}

	private static long clampExp(long val, long step) {
		long diff = val % step;
		if (diff == 0) return val;
		return val + step - diff;
	}
	
	public static class NtfsConfig{
		public final int BytesPerSector;
		public final int clusterSize;
		public final long TotalClustors;
		public final long MFT_Offset;
		public final long MFTMirror_Offset;
		public final int ClustersPerMftRecord;
		public final int ClustersPerIndexRecord;
		public final long BytesPerMftRecord;
		
		public NtfsConfig(ReadableSource source) throws IOException {
			byte[] buffer = new byte[512];
			source.readSector(0, buffer);
			
			long Signature = read64(buffer, 3);
			if(Signature != 0x202020205346544EL) throw new IOException("No NTFS format detected!");
			//System.out.println("Signature:" + Long.toString(Signature, 16));
			BytesPerSector = read16(buffer, 3+8);
			int sectorCount = read8(buffer, 3+8+2);
			clusterSize = BytesPerSector * sectorCount;
			long TotalSectors = read64(buffer, 3+8+2+1+26);
			if(TotalSectors < 0) {		//math magic to prevent -a/b -effect.
				TotalSectors >>>= 1;	//...but i am pretty sure it wont get used the next few decades xD
				sectorCount >>>= 1;
			}
			TotalClustors = TotalSectors / sectorCount;
			
			MFT_Offset = read64(buffer, 3+8+2+1+26+8);
			MFTMirror_Offset = read64(buffer, 3+8+2+1+26+8+8);
			ClustersPerMftRecord = read32(buffer, 3+8+2+1+26+8+8+8);
			ClustersPerIndexRecord = read32(buffer, 3+8+2+1+26+8+8+8+4);
			
			if (ClustersPerMftRecord >= 128){
				BytesPerMftRecord = ((long)1 << (byte)(256 - ClustersPerMftRecord));
			} else {
				BytesPerMftRecord = ClustersPerMftRecord * clusterSize;
			}
		}
	}

	private static long read48(byte[] buffer, int offset) throws IOException {
		long lower = read32(buffer, offset) & 0xFFFFFFFFL;
		long upper = read16(buffer, offset + 4) & 0xFFFFL;
		return (upper << 32) | lower;
	}
	
	private static long read64(byte[] buffer, int offset) {
		return ByteBuffer.wrap(buffer, offset, 8).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get();
	}

	private static int read32(byte[] buffer, int offset) {
		return ByteBuffer.wrap(buffer, offset, 4).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get();
	}

	private static int read16(byte[] buffer, int offset) {
		return ByteBuffer.wrap(buffer, offset, 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get() & 0xFFFF;
	}

	private static int read8(byte[] buffer, int offset) {
		return buffer[offset] & 0xFF;
	}
	
	public static class RawNode{
		private byte[] data;
		
		public RawNode(byte[] mftEntryBytes, NtfsConfig config) throws IOException {
			fixupRawMftdata(mftEntryBytes, config);
			this.data = mftEntryBytes;
		}
		
		public boolean isActiveEntry() throws IOException {//is node in use?
			int Flags = read16(data, 22);
			return (Flags & 1) == 1;
		}

		public boolean isValid(int length) throws IOException {
			if(!isActiveEntry()) return false;
			int Type = read32(data, 0);
			int AttributeOffset = read16(data, 20);
			int BytesInUse = read32(data, 24);
			//int BaseFileRecord_InodeNumberLowPart = read32(data, 32);
			//int BaseFileRecord_InodeNumberHighPart = read16(data, 36);
			
			//FileRecordHeader struct = new FileRecordHeader(data, 0);
			if(Type != 0x454c4946) return false; //if not file signature -> return
	        //long baseInode = (BaseFileRecord_InodeNumberLowPart & 0xFFFFFFFFL) | ((BaseFileRecord_InodeNumberHighPart & 0xFFFFL) << 32);
	        long baseInode = read48(data, 32);
	        //This is an inode extension used in an AttributeAttributeList of another inode, don't parse it
			if (baseInode != 0) return false;
			//int AttributeOffset = struct.AttributeOffset & 0xFFFF;//bb.asShortBuffer().get() & 0xFFFF;
	        if (AttributeOffset >= length)
	            throw new IOException("Error: attributes are outside the FILE record, the MFT may be corrupt.");
			//int BytesInUse = struct.BytesInUse;//bb.asIntBuffer().get() & 0xFFFF;
	        
	        if (BytesInUse > length)
	            throw new IOException("Error: the node record is bigger than the size of the buffer, the MFT may be corrupt.");
	        
	        return true;
		}

		public Node ProcessMftRecord(int length, Stream MftStream, NtfsConfig config, ReadableSource source, long nodeIndex, RawNode[] rawNodes) throws IOException {
			int AttributeOffset = read16(data, 20);
			int Flags = read16(data, 22);
			
	        Node node = new Node();
	        node.NodeIndex = nodeIndex;
	        
	        if ((Flags & 2) == 2) {
	        	node.Attributes |= Attributes_Directory;
	        	node.isDir = true;
	        }
	        
	        ProcessAttributes(node, data, AttributeOffset, length - AttributeOffset, false, 0, MftStream, config, source, rawNodes);
	        
	        for(Stream s : node.streams) s.applyFragments();
	        
	        return node;
		}

		private void fixupRawMftdata(byte[] mft, NtfsConfig config) throws IOException {
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
		
		private void ProcessAttributes(Node node, byte[] ptr, int ptr_offset, int BufLength, boolean debug, int depth
				, Stream MftStream, NtfsConfig config, ReadableSource source, RawNode[] rawNodes) throws IOException {
			ArrayList<Stream> streams = node.streams;
			final int AttributeType_AttributeAttributeList = 0x20;
			final int AttributeType_AttributeData = 0x80;
			int AttributeOffset = 0;
			int attribute_Length;
			//System.out.println("BufLength="+BufLength);
	        for(; AttributeOffset < BufLength; AttributeOffset += attribute_Length){
	        	int offset = AttributeOffset+ptr_offset;
	        	int attribute_AttributeType = read32(ptr, offset);
	        	attribute_Length = read32(ptr, offset+4);
	        	//System.out.println((AttributeOffset + attribute_Length) + " >= " + BufLength + " ?");
	        	if(AttributeOffset + attribute_Length >= BufLength) break;
	            //attribute = new Attribute(ptr, AttributeOffset +ptr_offset);
	            if(debug) System.out.println("depth="+depth+", AttributeOffset="+AttributeOffset + ", AttributeType="+attribute_AttributeType);
	            // exit the loop if end-marker.
	            if (attribute_AttributeType == 0xFFFFFFFF || attribute_AttributeType == 0 || attribute_Length == 0) break;


	        	byte attribute_Nonresident = ptr[offset+8];
	        	byte attribute_NameLength = ptr[offset+9];
	        	int attribute_NameOffset = read16(ptr, offset+10);
	        	//int attribute_Flags = read16(ptr, offset+12);//0x0001 = Compressed, 0x4000 = Encrypted, 0x8000 = Sparse
	        	//int attribute_AttributeNumber = read16(ptr, offset+14);
	            
	            
	            //make sure we did read the data correctly
	            if ((AttributeOffset + 4 > BufLength) || attribute_Length < 3 || (AttributeOffset + attribute_Length > BufLength))
	                throw new IOException("Error: attribute in Inode %I64u is bigger than the data, the MFT may be corrupt.");
	            //attributes list needs to be processed at the end
	            if (attribute_AttributeType == AttributeType_AttributeAttributeList) {
	            	if(debug) System.out.println("found AttributeType_AttributeAttributeList");
	            	
	            	continue;
	            }

	            /* If the Instance does not equal the AttributeNumber then ignore the attribute.
	               This is used when an AttributeList is being processed and we only want a specific
	               instance. */
	            
	            if (attribute_Nonresident == 0) {
	            	
	            	ResidentAttribute residentAttribute = new ResidentAttribute(ptr, AttributeOffset+ptr_offset+16);
	            	if(debug) System.out.println("Resident");
	            	switch (attribute_AttributeType){
	                    case 0x30://AttributeType.AttributeFileName:
	                    	AttributeFileName attributeFileName = new AttributeFileName(ptr, AttributeOffset + residentAttribute.ValueOffset+ptr_offset);
	                    	
	                    	node.lastEdited = attributeFileName.ChangeTime;
	                    	/*
	                        if (attributeFileName.InodeNumberHighPart > 0){
	                            throw new IOException("NotSupportedException: 48 bits inode are not supported to reduce memory footprint.");
	                        }
	                        */
	                        node.ParentNodeIndex = ((attributeFileName.InodeNumberHighPart & 0xFFFF) << 32) | (attributeFileName.InodeNumberLowPart & 0xFFFFFFFF);
	                        
	                        if (attributeFileName.NameType == 1 || node.Name == null){
	                        	node.Name = new_String(ptr, attributeFileName.NameOffset_struct_getCurrOffset, attributeFileName.NameLength & 0xFF);
	                        	if(debug) System.out.println("node.Name="+node.Name);
	                        }
	                        break;

	                    case 0x10://AttributeType.AttributeStandardInformation:
	                    	node.Attributes |= ByteBuffer.wrap(ptr, AttributeOffset + (residentAttribute.ValueOffset & 0xFFFF) + 64+ptr_offset, 4)
	                    		.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get();

	                        break;

	                    case 0x80://AttributeType.AttributeData: //hier ist die rohe datei gespeichert, wenn tag verwendet wird :)
	                        node.Size = residentAttribute.ValueLength;
	                        if(debug) System.out.println("residentAttribute.ValueLength="+residentAttribute.ValueLength);
	                        /*
	                        int start = AttributeOffset + residentAttribute.ValueOffset+ptr_offset;
	                    	int len = residentAttribute.Length;
	                        System.out.println("AttributeData:"+start+".."+len);
	                    	System.out.println('"' + new String(ptr, start, len) + '"');
	                    	for(int i=0; i<len; i++) System.out.println("#"+i+": " + (ptr[start+i] & 0xFF));
	                    	*/
	                        break;
	                    case 0x40://AttributeObjectId
	                    	/*
	                    	int start = AttributeOffset + residentAttribute.ValueOffset+ptr_offset;
	                    	int len = residentAttribute.Length;
	                    	System.out.println("AttributeObjectId:"+start+".."+len);
	                    	System.out.println('"' + new String(ptr, start, len) + '"');
	                    	for(int i=0; i<len; i++) System.out.println("#"+i+": " + (ptr[start+i] & 0xFF));
	                    	*/
	                        break;
	                }
	            } else {
	            	NonResidentAttribute nonResidentAttribute = new NonResidentAttribute(ptr, AttributeOffset+ptr_offset+16);

	            	if(debug) System.out.println("NonResident");
	            	
	                //save the length (number of bytes) of the data.
	                if (attribute_AttributeType == AttributeType_AttributeData && node.Size == 0)  node.Size = nonResidentAttribute.DataSize;
	                //extract the stream name
	                String streamName = null;
	                if (attribute_NameLength > 0){
	                    streamName = new_String(ptr, AttributeOffset + (attribute_NameOffset & 0xFFFF)+ptr_offset, attribute_NameLength & 0xFF);
	                    if(debug) System.out.println("streamName="+streamName);
	                }
	                //find or create the stream
	                Stream stream = SearchStream(streams, attribute_AttributeType, streamName);
	                if (stream == null){
	                    stream = new Stream(streamName, attribute_AttributeType, nonResidentAttribute.DataSize);
	                    streams.add(stream);
	                } else if (stream.Size == 0){
	                    stream.Size = nonResidentAttribute.DataSize;
	                }

	                //we need the fragment of the MFTNode so retrieve them this time
	                //even if fragments aren't normally read
	                
	                ProcessFragments(
	                        stream,
	                        ptr, new int[]{AttributeOffset + (nonResidentAttribute.RunArrayOffset&0xFFFF)+ptr_offset},
	                        (int)BufLength+ptr_offset,
	                        nonResidentAttribute.StartingVcn
	                    );
	                
	                
	            }
	        }
	        
	        AttributeOffset = 0;
	        attribute_Length = 0;
	        for(; AttributeOffset < BufLength; AttributeOffset += attribute_Length){
	        	int offset = AttributeOffset+ptr_offset;
	        	int attribute_AttributeType = read32(ptr, offset);
	        	attribute_Length = read32(ptr, offset+4);

	        	if(AttributeOffset + attribute_Length >= BufLength) break;
	            // exit the loop if end-marker.
	            if ((AttributeOffset + 4 <= BufLength) && attribute_AttributeType == 0xFFFFFFFF) break;

	        	byte attribute_Nonresident = ptr[offset+8];
	        	/*
	        	byte attribute_NameLength = ptr[offset+9];
	        	int attribute_NameOffset = read16(ptr, offset+10);
	        	int attribute_Flags = read16(ptr, offset+12);
	        	int attribute_AttributeNumber = read16(ptr, offset+14);
	        	*/
	            //make sure we did read the data correctly
	            if ((AttributeOffset + 4 > BufLength) || attribute_Length < 3 || (AttributeOffset + attribute_Length > BufLength))
	                throw new IOException("Error: attribute in Inode %I64u is bigger than the data, the MFT may be corrupt.");
	            //attributes list needs to be processed at the end
	            if (attribute_AttributeType == AttributeType_AttributeAttributeList) {
	            	if (attribute_Nonresident == 0) {
	            		ResidentAttribute residentAttribute = new ResidentAttribute(ptr, AttributeOffset+ptr_offset+16);
	            		ProcessAttributeList(
	                        node,
	                        ptr, AttributeOffset+ptr_offset + (residentAttribute.ValueOffset & 0xFFFF),
	                        residentAttribute.ValueLength,
	                        depth, streams
	                        ,MftStream,config,source, rawNodes
	                        );
	            	} else {
	            		NonResidentAttribute nonResidentAttribute = new NonResidentAttribute(ptr, AttributeOffset+ptr_offset+16);
	            		byte[] buffer = ProcessNonResidentData(
	                        ptr, AttributeOffset+ptr_offset + (nonResidentAttribute.RunArrayOffset & 0xFFFF),
	                        attribute_Length - nonResidentAttribute.RunArrayOffset,
	                        0,
	                        nonResidentAttribute.DataSize, streams, null, node, config, source
	                    );
	            		if(buffer != null) ProcessAttributeList(node, buffer,0, nonResidentAttribute.DataSize, depth + 1
	            				, streams, MftStream, config, source, rawNodes);
	            	}
	            	continue;
	            }
	        }
	        if(debug) System.out.println("-----------------RETURN-----------------");
		}
		
		private static String new_String(byte[] src, int offset, int len) {
			char[] arr = new char[len];
			ByteBuffer.wrap(src, offset, len << 1).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer().get(arr);
			return new String(arr);
		}
		
		private void ProcessFragments(
	            Stream stream,
	            byte[] runData,
	            int[] index,
	            int runDataLength,
	            long StartingVcn) throws IOException
	        {
	            //Walk through the RunData and add the extents.
	            long lcn = 0;
	            long vcn = StartingVcn;
	            int runOffsetSize = 0;
	            int runLengthSize = 0;
	            
	            ArrayList<Fragment> fragments = new ArrayList<Fragment>();
	            while (runData[index[0]] != 0) {
	                //Decode the RunData and calculate the next Lcn.
	                runLengthSize = (runData[index[0]] & 0x0F);
	                runOffsetSize = ((runData[index[0]] >> 4) & 0x0F);

	                if (++index[0] >= runDataLength)
	                    throw new IOException("Error: datarun is longer than buffer, the MFT may be corrupt.");

	                long runLength = ProcessRunLength(runData, runDataLength, runLengthSize, index);

	                long runOffset = ProcessRunOffset(runData, runDataLength, runOffsetSize, index);
	             //System.out.println("runOffset & runLength: " + runOffset + " | " + runLength);
	                lcn += runOffset;
	                vcn += runLength;
	                //System.out.println("-> " + lcn + " | " + vcn);
	                
	                /* Add the size of the fragment to the total number of clusters.
	                   There are two kinds of fragments: real and virtual. The latter do not
	                   occupy clusters on disk, but are information used by compressed
	                   and sparse files. */
	                if (runOffset != 0) stream.Clusters += runLength;

	                fragments.add(new Fragment(runOffset == 0 ? -1L : lcn, vcn));
	            }
	            //System.out.println("-------------------------");
	            stream.addFragments(fragments);
	        }
		
		
		

		private static long ProcessRunLength(byte[] runData, int runDataLength, int runLengthSize, int[] index) throws IOException {
			ByteBuffer bb = ByteBuffer.allocate(8);
	        for (int i = 0; i < runLengthSize; i++) {
	        	bb.put(i, runData[index[0]]);
	            if (++index[0] >= runDataLength) throw new IOException("Datarun is longer than buffer, the MFT may be corrupt.");
	        }
	        bb.rewind();
	        return bb.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get();
	    }

	    /// Decode the RunOffset value.
	    private static long ProcessRunOffset(byte[] runData, int runDataLength, int runOffsetSize, int[] index) throws IOException {
	    	if(runOffsetSize == 0) return 0;
	        ByteBuffer bb = ByteBuffer.allocate(8);
	        int i;
	        for (i = 0; i < runOffsetSize; i++) {
	            bb.put(i, runData[index[0]]);
	            if (++index[0] >= runDataLength) throw new IOException("Datarun is longer than buffer, the MFT may be corrupt.");
	        }
	        /*
	        //process negative values
	        if ((bb.get(i - 1) & 0xFF) >= 0x80){
	        	while (i < 8){
	        		bb.put(i++, (byte)0xFF);
	        	}
	        }

	        bb.rewind();
	        return bb.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get();
	        */
	        long l = bb.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get();
	        int diff = (8 - i) * 8;
	        l <<= diff;
	        l >>= diff;//copy minus-bit over diff-number of left bits
	        return l;
	    }
		
	    
	    private void ProcessAttributeList(Node node, byte[] ptr, int ptr_offset, long bufLength, int depth, ArrayList<Stream> streams, 
	    		Stream MftStream, NtfsConfig config, ReadableSource source, RawNode[] rawNodes) throws IOException {
	        boolean debug = false;
	        //if(node.Name != null) debug = node.Name.equals("Red+Dragon.zip");
	    	if(debug) System.out.println("ProcessAttributeList: " + node.Name);
	    	if(debug) System.out.println("bufLength="+bufLength);
	    	if(debug) System.out.println("ptr_offset="+ptr_offset);
	    	if (ptr == null || bufLength == 0) return;
	        //if (depth > 1000) throw new IOException("Error: infinite attribute loop, the MFT may be corrupt.");
	        if (depth > 10) throw new IOException("Error: infinite attribute loop, the MFT may be corrupt.");

	        AttributeList attribute = null;
	        for (int AttributeOffset = 0; AttributeOffset < bufLength; AttributeOffset = AttributeOffset + attribute.Length){
	            attribute = new AttributeList(ptr, AttributeOffset+ptr_offset);//(AttributeList*)&ptr[AttributeOffset];

	            /* Exit if no more attributes. AttributeLists are usually not closed by the
	               0xFFFFFFFF endmarker. Reaching the end of the buffer is therefore normal and
	               not an error. */
	            if(debug) System.out.println("attribute.AttributeType="+attribute.AttributeType);
	            if(debug) System.out.println("attribute.Length="+attribute.Length);
	        	if (AttributeOffset + 3 > bufLength) break;
	            if (attribute.AttributeType == 0xFFFFFFFF) break;
	            if (attribute.Length < 3) break;
	            if (AttributeOffset + attribute.Length > bufLength) break;

	            /* Extract the referenced Inode. If it's the same as the calling Inode then ignore
	               (if we don't ignore then the program will loop forever, because for some
	               reason the info in the calling Inode is duplicated here...). */
	            long RefInode = (((long)attribute.InodeNumberHighPart & 0xFFFF) << 32) | (attribute.InodeNumberLowPart & 0xFFFFFFFF);
	            if(debug) System.out.println("check refNode");
	            if (RefInode == node.NodeIndex) continue;
	            if(debug) System.out.println("RefInode="+RefInode);
	            /* Extract the streamname. I don't know why AttributeLists can have names, and
	               the name is not used further down. It is only extracted for debugging purposes.
	               */
	            
	            /* Find the fragment in the MFT that contains the referenced Inode. */
	            /*
	            Stream stream = MftStream;
	            if(stream == null){
	            	for(Stream s : streams){
	            		System.out.println("s.Type="+s.Type);
	            		if(s.Type == 0x80){
	            			s.applyFragments();
	            			stream = s;
	            			System.out.println("found native stream channel");
	            		}
	            	}
	            }*/
	            
	            RawNode rawNode = rawNodes[(int)RefInode];
	            byte[] buffer = rawNode.data;
				
				int AttributeOffset2 = read16(buffer, 20);
				
	            long baseInode = read48(buffer, 32);
	            
	            if (node.NodeIndex != baseInode)
	                continue;

	            if(debug) System.out.println("call ProcessAttributes()");
	            // Process the list of attributes in the Inode, by recursively calling the ProcessAttributes() subroutine.
	            ProcessAttributes(
	                node,
	                buffer, 0 + (AttributeOffset2 & 0xFFFF),
	                (int)config.BytesPerMftRecord - (AttributeOffset2 & 0xFFFF),
	                debug,
	                depth + 1
	                , MftStream,config,source, rawNodes
	                );
	        }
	        if(debug) System.out.println(">>end");
	    }
	    
	    
	    private byte[] ProcessNonResidentData(
	    		byte[] RunData, int RunData_offset, int RunDataLength,
				long Offset, /* Bytes to skip from begin of data. */
				long WantedLength /* Number of bytes to read. */
				, ArrayList<Stream> streams, Stream external_stream, Node debug_node, NtfsConfig config, ReadableSource source) throws IOException {
			// Sanity check.
			if (RunData == null || RunDataLength == 0)
				throw new IOException("nothing to read");

			if (WantedLength >= /* UInt32.MaxValue */0xFFFFFFFFL) {
				// throw new IOException("too many bytes to read");
				System.err.println("too many bytes to read: " + WantedLength);
				System.err.println("affected file: " + debug_node.Name);
				return null;
			}
			Stream stream = new Stream("", 0, WantedLength);
			long StartingVcn = 0;
			ProcessFragments(stream, RunData, new int[] { RunData_offset }, RunDataLength + RunData_offset, StartingVcn);
			stream.applyFragments();
			byte[] raw = readFile(stream, config, source);
			byte[] out = new byte[(int) WantedLength];
			System.arraycopy(raw, (int) Offset, out, 0, out.length);
			return out;
	    }
		
		
		
		
		
		
		
		
		
	}
	
	public static class Node{
		//make the file appear in the rootdirectory by default
		public int ParentNodeIndex = ROOTDIRECTORY;
		public int Attributes;
		public boolean isDir;
		public ArrayList<Stream> streams = new ArrayList<Stream>();
		public long lastEdited;
		public String Name;
		public long Size;
		public long NodeIndex;
		public boolean isSystemFile;
		
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	public static class Stream {
        public long Clusters;                      // Total number of clusters.
        public long Size;                          // Total number of bytes.
        public int Type;
        
        public ArrayList<Fragment> raw_fragments = new ArrayList<Fragment>(16);
        long[] fragments;
        public String Name;
        
        private boolean debug = false;
        public Stream(String name, int type, long size) {
            Name = name;
            Type = type;
            Size = size;
            fragments = new long[0];
        }
        
        public long[] getFragments(){
			return fragments.clone();
        }

		public void addFragments(ArrayList<Fragment> fragments) {
			raw_fragments.addAll(fragments);
		}

		public void applyFragments() {
			//System.out.println("applyFragments: input: " + raw_fragments);
			int len = raw_fragments.size();
			if(len == 0) return;
			
			long[] out = new long[(len+1) * 2];
			int wp = 0;
			out[wp++] = 0;
			for(Fragment f : raw_fragments){
				out[wp++] = f.Lcn;
				out[wp++] = f.NextVcn;
				if(debug) System.out.println("applyFragments: f.Lcn="+f.Lcn + ", f.NextVcn="+f.NextVcn);
			}
			out[out.length - 1] = out[out.length - 3] + (out[out.length - 2] - out[out.length - 4]);
			this.fragments = out;
			//raw_fragments = null;
			//System.out.println("applyFragments: output: " + Arrays.toString(fragments));
			
		}
    }
	public static class Fragment{
        public long Lcn;                // Logical cluster number, location on disk.
        public long NextVcn;            // Virtual cluster number of next fragment.

        public Fragment(long lcn, long nextVcn) {
            Lcn = lcn;
            NextVcn = nextVcn;
        }
        
        @Override
        public String toString(){
        	return "{Lcn="+Lcn+", NextVcn="+NextVcn+"}";
        }
    }
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

    public static class ResidentAttribute {
         public int ValueLength;
         public short ValueOffset;
         
         public ResidentAttribute(byte[] buffer, int offset){
        	 ValueLength = read32(buffer, offset);
        	 ValueOffset = (short)read16(buffer, offset + 4);
         }
    }
    public static class NonResidentAttribute extends ParsableStructure {
        public long StartingVcn;
        public long LastVcn;
        public short RunArrayOffset;
        public byte CompressionUnit;
        //public fixed byte AlignmentOrReserved[5]; //skip 5 bytes
        private int dummyField1;
        private byte dummyField2;// DO NOT DELETE! used for layout undefined space
        
        public long AllocatedSize;
        public long DataSize;
        public long InitializedSize;
        public long CompressedSize;    // Only when compressed
        
        public NonResidentAttribute(byte[] buffer, int offset){
        	super.parse(buffer, offset);
        }
        
        public void dummy(){
        	System.out.println(dummyField1 + dummyField2);
        }
    }
    public static class AttributeFileName extends ParsableStructure {
    	//public INodeReference ParentDirectory;
    	public int InodeNumberLowPart;
        public short InodeNumberHighPart;
        public short SequenceNumber;
        
        public long CreationTime;
        public long ChangeTime;
        public long LastWriteTime;
        public long LastAccessTime;
        public long AllocatedSize;
        public long DataSize;
        public int FileAttributes;
        public int AlignmentOrReserved;
        public byte NameLength;
        public byte NameType;//NTFS=0x01, DOS=0x02
        public int NameOffset_struct_getCurrOffset;
    	public AttributeFileName(byte[] buffer, int offset){
    		/*
    		ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
         	bb.position(offset);
         	InodeNumberLowPart = bb.asIntBuffer().get();
         	bb.position(offset+4);
         	InodeNumberHighPart = bb.asShortBuffer().get();
         	bb.position(offset+6);
         	SequenceNumber = bb.asShortBuffer().get();
         	bb.position(offset+8);
         	CreationTime = bb.asLongBuffer().get();
         	bb.position(offset+16);
         	ChangeTime = bb.asLongBuffer().get();
         	bb.position(offset+24);
         	LastWriteTime = bb.asLongBuffer().get();
         	bb.position(offset+32);
         	LastAccessTime = bb.asLongBuffer().get();
         	bb.position(offset+40);
         	AllocatedSize = bb.asLongBuffer().get();
         	bb.position(offset+48);
         	DataSize = bb.asLongBuffer().get();
         	bb.position(offset+56);
         	FileAttributes = bb.asIntBuffer().get();
         	bb.position(offset+60);
         	AlignmentOrReserved = bb.asIntBuffer().get();
         	NameLength = buffer[offset + 64];
         	NameType = buffer[offset + 65];
         	NameOffset = offset + 66;
         	//bb.position(offset+66);
         	//AlignmentOrReserved = bb.asIntBuffer().get();
         	 */
        	super.parse(buffer, offset);
    	}
    }
    
    public static class AttributeList extends ParsableStructure {
        public int AttributeType;
        public short Length;
        public byte NameLength;
        public byte NameOffset;
        public long LowestVcn;
        //public INodeReference FileReferenceNumber;
        public int InodeNumberLowPart;
        public short InodeNumberHighPart;
        public short SequenceNumber;
        
        public short Instance;

    	public AttributeList(byte[] buffer, int offset){
    		super.parse(buffer, offset);
    	}
    };
	
    public static class ParsableStructure{
		public int parse(byte[] raw, int offset){
			try{
				Field[] fields = this.getClass().getDeclaredFields();
				ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
				for(Field f : fields){
					f.setAccessible(true);
					if(f.getName().contains("_struct_")){
						if(f.getName().endsWith("_struct_getCurrOffset")){
							f.set(this, offset);
							continue;
						}
					}
					bb.position(offset);
					Class<?> type = f.getType();
					if(type == byte.class) {
						f.set(this, bb.get());
						offset++;
					} else if(type == short.class) {
						f.set(this, bb.asShortBuffer().get());
						offset += 2;
					} else if(type == int.class) {
						f.set(this, bb.asIntBuffer().get());
						offset += 4;
					} else if(type == long.class) {
						f.set(this, bb.asLongBuffer().get());
						offset += 8;
					}
				}
			}catch(Exception e){
				e.printStackTrace();
				return -1;
			}
			return offset;
		}
	}
    
    
    
    

    private static Stream SearchStream(ArrayList<Stream> streams, int streamType, String streamName){
        //since the number of stream is usually small, we can afford O(n)
        for(Stream stream : streams){
            if (stream.Type == streamType && stream.Name == streamName){
            	return stream;
            }
        }
        return null;
    }
    private static Stream SearchStream(ArrayList<Stream> streams, int streamType){
        //since the number of stream is usually small, we can afford O(n)
        for(Stream stream : streams){
            if (stream.Type == streamType){
            	return stream;
            }
        }
        return null;
    }
    
    
    
    
    public static byte[] readFile(Stream stream, NtfsConfig config, ReadableSource source) throws IOException {
		long[] out = stream.getFragments();
		byte[] data = new byte[(int)stream.Size];
		for(int i=0; i<out.length-2; i+=2){
			long vClustor = out[i];
			long rClustor = out[i+1];
			//System.out.println("vClustor="+vClustor+", rClustor="+rClustor + ", out[i+2]="+out[i+2]);
			int len = (int)(out[i+2] - vClustor);
			len *= config.clusterSize;
			//if(vClustor * clusterSize + len > stream.Size) len = (int)( stream.Size - (vClustor * clusterSize) );
			long bytesLeft = stream.Size - (vClustor * config.clusterSize);
			if(bytesLeft < 0) break;
			if(bytesLeft < len) len = (int)bytesLeft;
			byte[] a = readAt(rClustor * config.clusterSize, len, source);
			System.arraycopy(a, 0, data, (int)(vClustor * config.clusterSize), len);
		}
		return data;
	}
    
    
    public static byte[] readAt(long byteOffset, long byteLen, ReadableSource source) throws IOException {
    	int sectorCount = (int)clampExp(byteLen, 512);
    	byte[] buffer = new byte[sectorCount];
    	source.readSectors(byteOffset / 512, sectorCount / 512, buffer, 0);
    	return buffer;
    }
    
    
    public static class NTFSFileInputStream extends InputStream {
		ArrayList<long[]> runs;
		long fileBytesLeft;
		long readPos;
		ReadableSource source;
		public NTFSFileInputStream(Stream stream, NtfsConfig config, ReadableSource source){
			this.source = source;
			long[] out = stream.getFragments();
			fileBytesLeft = stream.Size;
			runs = new ArrayList<long[]>((out.length - 2) / 2);
			int off = 0;
			for(int i=0; i<out.length-2; i+=2){
				long offset = out[i+1];
				long length = out[i+2] - out[i];
				runs.add(new long[]{offset * config.clusterSize, Math.min(length * config.clusterSize, stream.Size - off)});
				off += length * config.clusterSize;
			}
		}
		
		public int read() throws IOException {
			byte[] a = new byte[1];
			int l = read(a, 0, 1);
			if(l <= 0) return -1;
			return a[0] & 0xFF;
		}
		
		public int read(byte[] buffer, int offset, int len) throws IOException {
			if(runs.size() == 0 || fileBytesLeft == 0) return -1;
			long[] currentRun = runs.get(0);
			int new_len = (int)Math.min(currentRun[1], len);
			
			
			byte[] data = readAt(currentRun[0], new_len, source);
			
			
			currentRun[0] += new_len;
			currentRun[1] -= new_len;
			fileBytesLeft -= new_len;
			readPos += new_len;
			if(currentRun[1] == 0) runs.remove(0);
			
			for(int i=0; i<new_len; i++) buffer[i + offset] = data[i];
			
			if(new_len < len){
				int l = read(buffer, offset + new_len, len - new_len);
				if(l == -1) {
					return new_len;
				} else {
					return new_len + l;
				}
			}
			
			return new_len;
		}
		
		public long skip(long bytes) throws IOException {
			long bytesSkipped = 0;
			while(bytes > 0 && runs.size() > 0 && fileBytesLeft > 0){
				long[] currentRun = runs.get(0);
				long skipRunBytes = Math.min(currentRun[1], bytes);
				currentRun[0] += skipRunBytes;
				currentRun[1] -= skipRunBytes;
				bytes -= skipRunBytes;
				bytesSkipped += skipRunBytes;
				if(currentRun[1] == 0) runs.remove(0);
			}
			if(runs.size() == 0) System.out.println("skip: seeked to EOF!");
			return bytesSkipped;
		}
	}
}
