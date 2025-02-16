package me.kaigermany.opendiskdiver.data.ntfs;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import me.kaigermany.opendiskdiver.data.Reader;
import me.kaigermany.opendiskdiver.datafilesystem.FileEntry;
import me.kaigermany.opendiskdiver.datafilesystem.FileSystem;
import me.kaigermany.opendiskdiver.reader.ReadableSource;
import me.kaigermany.opendiskdiver.utils.ByteArrayUtils;
import me.kaigermany.opendiskdiver.utils.MathUtils;

// https://flatcap.github.io/linux-ntfs/ntfs/concepts/file_record.html

public class NtfsReader implements Reader, FileSystem {
	public static final int AttributeType_AttributeData = 0x80;
	public static final int AttributeType_AttributeAttributeList = 0x20;
	public static final int ROOTDIRECTORY = 5;
	public static final int Attributes_Directory = 16;
	
	public NtfsConfig config;
	public HashMap<String, NtfsNode> fileMap;
	
	public NtfsReader(){}
	
	public void read(ReadableSource source) throws IOException {
		config = new NtfsConfig(source);
		System.out.println(config);
		NtfsNode[] nodes = readMFT(source);
		fileMap = convertNodesToFiles(nodes);
		/*
		for(String f : fileMap.keySet()){
			System.out.println(f);
		}
		*/
	}
	
	public static class NtfsFileEntry extends FileEntry{
		private NtfsNode node;
		
		public NtfsFileEntry(String nameAndPath, NtfsNode node) {
			super(node.Name, nameAndPath, node.Size, node.lastEdited);
		}

		@Override
		public InputStream openInputStream() {
			return node.openInputStream();
		}
	}

	@Override
	public List<FileEntry> listFiles() {
		ArrayList<FileEntry> list = new ArrayList<>(fileMap.size());
		for(Entry<String, NtfsNode> e : fileMap.entrySet()){
			NtfsNode n = e.getValue();
			if(n.isDir){
				continue;
			}
			list.add(new NtfsFileEntry(e.getKey(), n));
		}
		return list;
	}
	
	private HashMap<String, NtfsNode> convertNodesToFiles(NtfsNode[] nodes){
		HashMap<String, NtfsNode> fileMap = new HashMap<String, NtfsNode>(nodes.length * 2);
		for(NtfsNode n : nodes) {
			if(n == null || /*n.isDir ||*/ n.streams.size() == 0 || n.NodeIndex == 5) continue;
			//System.out.println(n.Name);
			try{
				String name = solveFilePath(nodes, n.NodeIndex);
				// https://winprotocoldoc.blob.core.windows.net/productionwindowsarchives/MS-FSCC/%5BMS-FSCC%5D.pdf
				for(NtfsStream s : n.streams){
					if(s.Name != null && s.Name.equals("$I30")){
						s.Name = "$I30:$INDEX_ALLOCATION";
					} else if(s.Type != 0x80){
						String typeName = getStreamTypeName(s.Type);
						s.Name = (s.Name == null ? "" : s.Name) + ":" + typeName;
					}
				}
				fileMap.put(name, n);
				n.isSystemFile |= name != null && name.charAt(0) == '$';
			}catch(Throwable e){
				if(e instanceof StackOverflowError){
					System.err.println(e);
				} else {
					e.printStackTrace();
				}
			}
		}
		return fileMap;
	}
	
	private String solveFilePath(NtfsNode[] nodes, long nodeId) {
		try{
			NtfsNode n = nodes[(int)nodeId];
			if(n.ParentNodeIndex == nodeId){
				System.err.println("node dir loop detected! return \"\"; instead.");
				return "";
			}
			if(n.ParentNodeIndex > 5) return solveFilePath(nodes, n.ParentNodeIndex) + '\\' + n.Name;
			return n.Name;
		}catch(NullPointerException e){
			e.printStackTrace();
			NtfsNode n = nodes[(int)nodeId];
			System.err.println("node: " + nodeId);
			if(n == null){
				System.err.println("node is NULL");
			} else {
				System.err.println("node-name: " + n.Name);
			}
			throw e;
		}
	}
	
	
	public NtfsNode[] readMFT(ReadableSource source) throws IOException {
		NtfsStream mftStream = null;
		NtfsNode MFTnode = null;
		{//step 1: read MFT entry
			
			byte[] mftEntryBytes = new byte[ (int)MathUtils.clampExp(config.BytesPerMftRecord, 512) ]; //align towards 512
			source.readSectors(config.MFT_Offset * config.clusterSize / 512, mftEntryBytes.length / 512, mftEntryBytes);
			
			RawNtfsNode temp = new RawNtfsNode(mftEntryBytes, config);
			NtfsNode node = new NtfsNode(0, config, source);
			if(!ProcessMftRecordOfMFT(node, temp.getData(), (int)config.BytesPerMftRecord, null, config, source, 0, null)){
				mftStream = node.SearchStream(AttributeType_AttributeData);
				node = ProcessMftRecord(temp.getData(), (int)config.BytesPerMftRecord, mftStream, config, source, 0, null, node);
			}
			mftStream = node.SearchStream(AttributeType_AttributeData);
			System.out.println(node.streams);
			System.out.println("mftStream: " + mftStream);
			MFTnode = node;
		}
		
		//step 2: walk through all entries and load them into memory as long as they are marked as valid.
		NTFSFileInputStream nfis = new NTFSFileInputStream(mftStream, config, source);
		@SuppressWarnings("resource")
		BufferedInputStream bis = new BufferedInputStream(nfis, 1 << 20);//1 MB cache

		int nodeCount = (int)(mftStream.Size / config.BytesPerMftRecord);
		System.out.println("nodeCount="+nodeCount);
		RawNtfsNode[] rawNodes = new RawNtfsNode[nodeCount];
		int bytesPerMftRecord = (int)config.BytesPerMftRecord;
		try{
			for(int nodeIndex=0; nodeIndex<nodeCount; nodeIndex++){
				byte[] buffer2 = new byte[bytesPerMftRecord];
		        int l = bis.read(buffer2, 0, bytesPerMftRecord);
		        if(l != (int)config.BytesPerMftRecord) throw new IOException("Missing data in MFT-Stream: readed only " + l + " bytes, expected " + config.BytesPerMftRecord + " bytes.");
		        RawNtfsNode node = new RawNtfsNode(buffer2, config);
		        if(node.isActiveEntry()){
		        	rawNodes[nodeIndex] = node;
		        }
			}
		}catch(Exception e){e.printStackTrace();}
		bis = null;
		nfis = null;
		//step 3: if a node is a FILE node then parse it fully.
		NtfsNode[] nodes = new NtfsNode[nodeCount];
		for(int nodeIndex=0; nodeIndex<nodeCount; nodeIndex++){
			RawNtfsNode rawNode = rawNodes[nodeIndex];
			if(rawNode != null && rawNode.isValid(bytesPerMftRecord)){
				rawNodes[nodeIndex] = null;
		        
		        NtfsNode node = nodes[nodeIndex] = ProcessMftRecord(rawNode.getData(), bytesPerMftRecord, mftStream, config, source, nodeIndex, rawNodes, MFTnode);
		        
		        if(node != null){
		        	node.isSystemFile = node.Name != null && node.Name.startsWith("$") && node.ParentNodeIndex == 5 && !node.Name.equals("$RECYCLE.BIN");
		        }
			}
		}
		return nodes;
	}
	
		

	public NtfsNode ProcessMftRecord(byte[] data, int length, NtfsStream MftStream, NtfsConfig config, ReadableSource source, long nodeIndex, RawNtfsNode[] rawNodes, NtfsNode MFTnode) throws IOException {
		int AttributeOffset = ByteArrayUtils.read16(data, 20);
		int Flags = ByteArrayUtils.read16(data, 22);
		
		// https://flatcap.github.io/linux-ntfs/ntfs/concepts/file_record.html
		int storedIndex = ByteArrayUtils.read32(data, 0x2C);//if we don't know its index (where we read), we can use the value stored in the entry :)
		if(nodeIndex == -1) nodeIndex = storedIndex & 0xFFFFFFFFL;//this only may get a problem if we have more then ((1 << 32) - 1) entries.
		
        NtfsNode node = new NtfsNode(nodeIndex, config, source);
        
        if ((Flags & 2) == 2) {
        	node.isDir = true;
        }
/*
        ArrayList<byte[]> attributes = readAttributes(data, AttributeOffset, length, false);
        {
        	int attribute_Flags = ByteArrayUtils.read16(attributes.get(0), 12);//0x0001 = Compressed, 0x4000 = Encrypted, 0x8000 = Sparse
        	//int attribute_AttributeNumber = read16(ptr, offset+14);
        	node.isCompressed = (attribute_Flags & 0x0001) != 0;
        	node.isEncrypted = (attribute_Flags & 0x4000) != 0;
        	node.isSparse = (attribute_Flags & 0x8000) != 0;
        }
    	for(byte[] a : attributes) parseAttribute(node, a, MFTnode, rawNodes);
  */      
        ProcessAttributes(node, data, AttributeOffset, length - AttributeOffset, false, 0, MftStream, config, source, rawNodes);
        
        for(NtfsStream s : node.streams) s.applyFragments();
        
        return node;
	}
	
	
	public boolean ProcessMftRecordOfMFT(NtfsNode node, byte[] data, int length, NtfsStream MftStream, NtfsConfig config, ReadableSource source, long nodeIndex, RawNtfsNode[] rawNodes) throws IOException {
		int AttributeOffset = ByteArrayUtils.read16(data, 20);
		int Flags = ByteArrayUtils.read16(data, 22);
		
        
        
        if ((Flags & 2) == 2) {
        	node.isDir = true;
        }
        boolean success = true;
        try{
        	//ArrayList<byte[]> attributes = readAttributes(data, AttributeOffset, length, false);
        	//for(byte[] a : attributes) parseAttribute(node, a, node, rawNodes);
        	ProcessAttributes(node, data, AttributeOffset, length - AttributeOffset, false, 0, MftStream, config, source, rawNodes);
        }catch(Exception e){
        	success = false;
        }
        
        for(NtfsStream s : node.streams) s.applyFragments();
        
        return success;
	}

	private ArrayList<byte[]> readAttributes(byte[] data, int offset, int maxLen, boolean isSubAttribute) {
		// predict list size: min attr-size=24, len=maxLen-offset
		ArrayList<byte[]> out = new ArrayList<byte[]>(24 / (maxLen - offset));
		while (offset < maxLen) {
			int attributeType = ByteArrayUtils.read32(data, offset);
			if (attributeType == -1 || attributeType == 0)
				break;
			int attributeLength = isSubAttribute ? ByteArrayUtils.read16(data, offset + 4) : ByteArrayUtils.read32(data, offset + 4);
			if (offset + attributeLength >= maxLen)
				break;
			out.add(cutOutBytes(data, offset, attributeLength));
			offset += attributeLength;
		}
		return out;
	}

	private static byte[] cutOutBytes(byte[] arr, int off, int len) {
		byte[] out = new byte[len];
		System.arraycopy(arr, off, out, 0, len);
		return out;
	}
	//"NTFS stores dates as the number of 100ns units since Jan 1st 1601. Unix, stores dates as the number of seconds since Jan 1st 1970."
	// https://flatcap.github.io/linux-ntfs/ntfs/help/glossary.html -> "Time Stamp"
	// https://stackoverflow.com/questions/6161776/convert-windows-filetime-to-second-in-unix-linux
	// https://cs.ou.nl/members/hugo/supervision/2019-jelle.bouma-bsc-thesis.pdf
	private static long convertTimeToUnix(long ntfsTime){
		//if(true) return ntfsTime;
		final long NTFS_TO_UNIX_EPOCH_SECONDS = 11644473600_000L;
		final long HUNDRED_NANOSECONDS_IN_ONE_MILLISECOND = 10000L;
        return (ntfsTime / HUNDRED_NANOSECONDS_IN_ONE_MILLISECOND) - NTFS_TO_UNIX_EPOCH_SECONDS;
	}
	// https://flatcap.github.io/linux-ntfs/ntfs/attributes/index.html
	private void parseAttribute(NtfsNode outputNode, byte[] data, NtfsNode MFTnode, RawNtfsNode[] rawNodes) {
		final int AttributeFileName = 0x30;
		final int AttributeData = 0x80;

		int attributeType = ByteArrayUtils.read32(data, 0);

		boolean isExternalAttibute = data[8] != 0;// attribute_Nonresident
		String name = null;
		{
			byte nameLength = data[9];
			if (nameLength > 0) {
				int nameOffset = ByteArrayUtils.read16(data, 10);
				name = UTF16String(data, nameOffset, nameLength);
			}
		}
		if (!isExternalAttibute) {
			int ValueLength = ByteArrayUtils.read32(data, 16);
			int ValueOffset = ByteArrayUtils.read16(data, 16 + 4);
			if (attributeType == 0x10) {// https://flatcap.github.io/linux-ntfs/ntfs/attributes/standard_information.html
    			LongBuffer lb = ByteBuffer.wrap(data, ValueOffset, 8*4).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer();
    			long FileCreation = lb.get();
    			long FileAltered = lb.get();
    			long MFTChanged = lb.get();
    			long FileRead = lb.get();
    			outputNode.lastEdited = convertTimeToUnix(FileAltered);
			} else if (attributeType == AttributeFileName) {
				AttributeFileName attributeFileName = new AttributeFileName(data, ValueOffset);

				//outputNode.lastEdited = convertTimeToUnix(attributeFileName.ChangeTime);
				outputNode.ParentNodeIndex = ByteArrayUtils.read48(data, ValueOffset);

				if (attributeFileName.NameType == 1 || outputNode.Name == null) {
					outputNode.Name = UTF16String(data, attributeFileName.NameOffset_struct_getCurrOffset,
							attributeFileName.NameLength & 0xFF);
				}
			} else if (attributeType == AttributeData) {
				byte[] fileBytes = cutOutBytes(data, ValueOffset, ValueLength);
				outputNode.fileBytes.put(name, fileBytes);
				outputNode.Size = ValueLength;
			} else if (attributeType == AttributeType_AttributeAttributeList) {
				//TODO
				/*
				try {
					ProcessAttributeList(outputNode, data, ValueOffset, ValueLength, 0, null, config, null, null);
				} catch (IOException e) {
					e.printStackTrace();
				}
				*/
				processAttributesList(data, ValueOffset, ValueLength, outputNode, MFTnode, rawNodes);
			}
		} else {// nonresident, aka somewhere else then this entry.
			int offset = 16;

			long StartingVcn = ByteArrayUtils.read64(data, offset);
			int RunArrayOffset = ByteArrayUtils.read16(data, offset + 16);
			long AllocatedSize = ByteArrayUtils.read64(data, offset + 24);
			long DataSize = ByteArrayUtils.read64(data, offset + 32);
			long InitializedSize = ByteArrayUtils.read64(data, offset + 40);
			long CompressedSize = ByteArrayUtils.read64(data, offset + 48);
			
			if (attributeType == 0xA0) {//INDEX_ALLOCATION
				
				NtfsStream stream = new NtfsStream(name, attributeType, DataSize);
				//TODO

				outputNode.streams.add(stream);
				
				// we need the fragment of the MFTNode so retrieve them this
				// time
				// even if fragments aren't normally read

				try {
					ProcessFragments(stream, data, 0 + (RunArrayOffset & 0xFFFF) + 0, data.length, StartingVcn);
				} catch (IOException e) {
					e.printStackTrace();
				}

			} else if (attributeType == 0x20) {//ATTRIBUTE_LIST
				
				if (DataSize >= Integer.MAX_VALUE) {
					System.err.println("too many bytes to read: " + DataSize);
					System.err.println("affected file: " + outputNode.Name);
				} else {
					//TODO
					
					try{
						byte[] buffer = readNonResidentData(data, RunArrayOffset, data.length - RunArrayOffset, DataSize, config, MFTnode.getSource());
						
						System.out.println("buffer: " + buffer.length);
						
						processAttributesList(buffer, 0, (int)DataSize, outputNode, MFTnode, rawNodes);
						
					} catch (IOException e) {
						e.printStackTrace();
					}
					
				}
				

			}
			
			
		}
	}
	
	
	private void processAttributesList(byte[] data, int ValueOffset, int ValueLength, NtfsNode outputNode, NtfsNode MFTnode, RawNtfsNode[] rawNodes){
		final int AttributeData = 0x80;
		System.out.println(Arrays.toString(data));
		ArrayList<byte[]> list = readAttributes(data, ValueOffset, ValueLength, true);
		System.out.println("list: " + list);
		for(byte[] attrData : list) {
			
			
			
        	int attribute_AttributeType = ByteArrayUtils.read32(attrData, 0);
        	System.out.println("processAttributesList()::attribute_AttributeType = " + (attribute_AttributeType >> 4));
        	int attribute_Length = ByteArrayUtils.read16(attrData, 4) & 0xFFFF;
        	long RefInode = ByteArrayUtils.read48(attrData, 16);
        	//if (AttributeOffset + 3 > bufLength) break;
            if (attribute_AttributeType == 0xFFFFFFFF) break;
            if (attribute_Length < 3) break;
            //if (AttributeOffset + attribute_Length > bufLength) break;
            /* Extract the referenced Inode. If it's the same as the calling Inode then ignore
               (if we don't ignore then the program will loop forever, because for some
               reason the info in the calling Inode is duplicated here...). */
            //long RefInode = (((long)attribute.InodeNumberHighPart & 0xFFFF) << 32) | (attribute.InodeNumberLowPart & 0xFFFFFFFF);
            if (RefInode == outputNode.NodeIndex) continue;
            /* Extract the streamname. I don't know why AttributeLists can have names, and
               the name is not used further down. It is only extracted for debugging purposes.
               */
            
            // Find the fragment in the MFT that contains the referenced Inode.
            RawNtfsNode rawNode;
            if(rawNodes == null){//in some rare cases, the MFT itself wants to read extended contents...
            	try{
            		@SuppressWarnings("resource")
					NTFSFileInputStream nfis = new NTFSFileInputStream(MFTnode.SearchStream(AttributeData), config, MFTnode.getSource());
	            	nfis.skip(RefInode * config.BytesPerMftRecord);
	            	byte[] localEntry = new byte[(int)config.BytesPerMftRecord];
	            	nfis.read(localEntry, 0, localEntry.length);
	            	rawNode = new RawNtfsNode(localEntry, config);
            	}catch(Exception e){
            		e.printStackTrace();
            		rawNode = null;
            	}
            } else {
            	rawNode = rawNodes[(int)RefInode];
            }
            
            byte[] buffer = rawNode.getData();
			
			int AttributeOffset2 = ByteArrayUtils.read16(buffer, 20);
			
            long baseInode = ByteArrayUtils.read48(buffer, 32);
            
            if (outputNode.NodeIndex != baseInode) continue;

            // Process the list of attributes in the Inode, by recursively calling the ProcessAttributes() subroutine.
            /*
            ProcessAttributes(
                node,
                buffer, (AttributeOffset2 & 0xFFFF),
                (int)config.BytesPerMftRecord - (AttributeOffset2 & 0xFFFF),
                debug, depth + 1, MftStream, config, source, rawNodes
            );
			*/
            ArrayList<byte[]> attributes = readAttributes(data, AttributeOffset2, (int)config.BytesPerMftRecord - AttributeOffset2, false);//TODO bool==false?
            for(byte[] attr : attributes) parseAttribute(outputNode, attr, MFTnode, rawNodes);
			
			
			
			
			
			
			
			
			
			
		}
	}

		private void ProcessAttributes(NtfsNode node, byte[] ptr, int ptr_offset, int BufLength, boolean debug, int depth
				, NtfsStream MftStream, NtfsConfig config, ReadableSource source, RawNtfsNode[] rawNodes) throws IOException {

			int AttributeOffset = 0;
			int attribute_Length;
			//System.out.println("BufLength="+BufLength);
	        for(; AttributeOffset < BufLength; AttributeOffset += attribute_Length){
	        	int offset = AttributeOffset+ptr_offset;
	        	int attribute_AttributeType = ByteArrayUtils.read32(ptr, offset);
	        	attribute_Length = ByteArrayUtils.read32(ptr, offset+4);
	        	//System.out.println((AttributeOffset + attribute_Length) + " >= " + BufLength + " ?");
	        	if(AttributeOffset + attribute_Length >= BufLength) break;
	            //attribute = new Attribute(ptr, AttributeOffset +ptr_offset);
	            if(debug) System.out.println("depth="+depth+", AttributeOffset="+AttributeOffset + ", AttributeType="+attribute_AttributeType);
	            // exit the loop if end-marker.
	            if (attribute_AttributeType == 0xFFFFFFFF || attribute_AttributeType == 0 || attribute_Length == 0) break;
	            

	        	byte attribute_Nonresident = ptr[offset+8];
	        	byte attribute_NameLength = ptr[offset+9];
	        	int attribute_NameOffset = ByteArrayUtils.read16(ptr, offset+10);
	        	int attribute_Flags = ByteArrayUtils.read16(ptr, offset+12);//0x0001 = Compressed, 0x4000 = Encrypted, 0x8000 = Sparse
	        	//int attribute_AttributeNumber = read16(ptr, offset+14);
	        	node.isCompressed = (attribute_Flags & 0x0001) != 0;
	        	node.isEncrypted = (attribute_Flags & 0x4000) != 0;
	        	node.isSparse = (attribute_Flags & 0x8000) != 0;
	        	
	            
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
	            		case 0x10:{// https://flatcap.github.io/linux-ntfs/ntfs/attributes/standard_information.html
	            			int offset2 = AttributeOffset + residentAttribute.ValueOffset+ptr_offset;
	            			LongBuffer lb = ByteBuffer.wrap(ptr, offset2, 8*4).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer();
	            			long FileCreation = lb.get();
	            			long FileAltered = lb.get();
	            			long MFTChanged = lb.get();
	            			long FileRead = lb.get();
	            			node.lastEdited = convertTimeToUnix(FileAltered);
	            			break;
	            		}
	                    case 0x30://AttributeType.AttributeFileName:
	                    	int offset2 = AttributeOffset + residentAttribute.ValueOffset+ptr_offset;
	                    	AttributeFileName attributeFileName = new AttributeFileName(ptr, offset2);
	                    	
	                    	//node.lastEdited = convertTimeToUnix(attributeFileName.ChangeTime);
	                    	
	                    	node.ParentNodeIndex = ByteArrayUtils.read48(ptr, offset2);
	                        
	                        if (attributeFileName.NameType == 1 || node.Name == null){
	                        	node.Name = UTF16String(ptr, attributeFileName.NameOffset_struct_getCurrOffset, attributeFileName.NameLength & 0xFF);
	                        	if(debug) System.out.println("node.Name="+node.Name);
	                        }
	                        break;

	                    case 0x80://AttributeType.AttributeData: //hier ist die rohe datei gespeichert, wenn tag verwendet wird :)
	                        node.Size = residentAttribute.ValueLength;
	                        if(debug) System.out.println("residentAttribute.ValueLength="+residentAttribute.ValueLength);
	                        
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
	                    streamName = UTF16String(ptr, AttributeOffset + (attribute_NameOffset & 0xFFFF)+ptr_offset, attribute_NameLength & 0xFF);
	                    if(debug) System.out.println("streamName="+streamName);
	                }
	                //find or create the stream
	                NtfsStream stream = node.SearchStream(attribute_AttributeType, streamName);
	                if (stream == null){
	                    stream = new NtfsStream(streamName, attribute_AttributeType, nonResidentAttribute.DataSize);
	                    node.streams.add(stream);
	                } else if (stream.Size == 0){
	                    stream.Size = nonResidentAttribute.DataSize;
	                }

	                //we need the fragment of the MFTNode so retrieve them this time
	                //even if fragments aren't normally read
	                
	                ProcessFragments(
	                        stream,
	                        ptr, AttributeOffset + (nonResidentAttribute.RunArrayOffset & 0xFFFF) + ptr_offset,
	                        (int)BufLength+ptr_offset,  nonResidentAttribute.StartingVcn
	                    );
	                
	                
	            }
	        }
	        
	        AttributeOffset = 0;
	        attribute_Length = 0;
	        for(; AttributeOffset < BufLength; AttributeOffset += attribute_Length){
	        	int offset = AttributeOffset+ptr_offset;
	        	int attribute_AttributeType = ByteArrayUtils.read32(ptr, offset);
	        	attribute_Length = ByteArrayUtils.read32(ptr, offset+4);

	        	if(AttributeOffset + attribute_Length >= BufLength) break;
	            // exit the loop if end-marker.
	            if ((AttributeOffset + 4 <= BufLength) && attribute_AttributeType == 0xFFFFFFFF) break;

	        	byte attribute_Nonresident = ptr[offset+8];
	        	
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
	                        depth, MftStream, config, source, rawNodes
	                        );
	            	} else {
	            		NonResidentAttribute nonResidentAttribute = new NonResidentAttribute(ptr, AttributeOffset+ptr_offset+16);
	            		long WantedLength = nonResidentAttribute.DataSize;
	            		byte[] buffer = null;
        				if (WantedLength >= Integer.MAX_VALUE) {
        					System.err.println("too many bytes to read: " + WantedLength);
        					System.err.println("affected file: " + node.Name);
        				} else {
							buffer = readNonResidentData(ptr,
									AttributeOffset + ptr_offset + (nonResidentAttribute.RunArrayOffset & 0xFFFF),
									attribute_Length - nonResidentAttribute.RunArrayOffset, WantedLength, config, source
							);
							ProcessAttributeList(node, buffer, 0, nonResidentAttribute.DataSize, depth + 1, MftStream, config, source, rawNodes);
        				}
	            				
	            	}
	            	continue;
	            }
	        }
		}
		
		private static String UTF16String(byte[] src, int offset, int len) {
			char[] arr = new char[len];
			ByteBuffer.wrap(src, offset, len << 1).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer().get(arr);
			return new String(arr);
		}
		
		private void ProcessFragments(NtfsStream stream, byte[] runData, int index, int runDataLength, long StartingVcn) throws IOException {
				int[] indexPtr = new int[]{index};
	            //Walk through the RunData and add the extents.
	            long lcn = 0;
	            long vcn = StartingVcn;
	            int runOffsetSize = 0;
	            int runLengthSize = 0;
	            
	            int nextNumberSize;
	            while (( nextNumberSize = runData[indexPtr[0]] ) != 0) {
	                //Decode the RunData and calculate the next Lcn.
	                runLengthSize = (nextNumberSize & 0x0F);
	                runOffsetSize = ((nextNumberSize >> 4) & 0x0F);

	                if (++indexPtr[0] >= runDataLength){
	                	throw new IOException("Error: datarun is longer than buffer, the MFT may be corrupt.");
	                }

	                long runLength = ProcessRunLength(runData, runDataLength, runLengthSize, indexPtr);
	                long runOffset = ProcessRunOffset(runData, runDataLength, runOffsetSize, indexPtr);
	                lcn += runOffset;
	                vcn += runLength;
	                //System.out.println("-> " + lcn + " | " + vcn);
	                
	                /* Add the size of the fragment to the total number of clusters.
	                   There are two kinds of fragments: real and virtual. The latter do not
	                   occupy clusters on disk, but are information used by compressed
	                   and sparse files. */
	                if (runOffset != 0) stream.Clusters += runLength;

	                stream.addFragment(runOffset == 0 ? -1L : lcn, vcn);
	            }
	        }
		
		
		

		private static long ProcessRunLength(byte[] runData, int runDataLength, int runLengthSize, int[] index) throws IOException {
			if(runLengthSize == 0) return 0;
			
			long val = 0;
	        int rp = index[0];
	        for (int i = 0; i < runLengthSize; i++) {
	        	val |= (runData[rp] & 0xFF) << (i << 3);
	        	rp++;
	        	if (rp >= runDataLength) throw new IOException("Datarun is longer than buffer, the MFT may be corrupt.");
	        }
	        index[0] = rp;
	        
	        return val;
	    }

	    /// Decode the RunOffset value.
	    private static long ProcessRunOffset(byte[] runData, int runDataLength, int runOffsetSize, int[] index) throws IOException {
	    	if(runOffsetSize == 0) return 0;
	    	
	    	long val = 0;
	        int rp = index[0];
	        for (int i = 0; i < runOffsetSize; i++) {
	        	val |= (runData[rp] & 0xFF) << (i << 3);
	        	rp++;
	        	if (rp >= runDataLength) throw new IOException("Datarun is longer than buffer, the MFT may be corrupt.");
	        }
	        index[0] = rp;

	        long l = val;
	        int diff = (8 - runOffsetSize) * 8;
	        l <<= diff;
	        l >>= diff;//copy minus-bit over diff-number of left bits
	        return l;
	    }
		
	    
	    private void ProcessAttributeList(NtfsNode node, byte[] ptr, int ptr_offset, long bufLength, int depth, 
	    		NtfsStream MftStream, NtfsConfig config, ReadableSource source, RawNtfsNode[] rawNodes) throws IOException {
	        boolean debug = false;
	    	if(debug) System.out.println("ProcessAttributeList: " + node.Name);
	    	if(debug) System.out.println("bufLength="+bufLength);
	    	if(debug) System.out.println("ptr_offset="+ptr_offset);
	    	if (ptr == null || bufLength == 0) return;
	        if (depth > 10) throw new IOException("Error: infinite attribute loop, the MFT may be corrupt.");

	        //AttributeList attribute = null;
	        int attribute_Length = 0;
	        for (int AttributeOffset = 0; AttributeOffset < bufLength; AttributeOffset += attribute_Length){
	            //attribute = new AttributeList(ptr, AttributeOffset+ptr_offset);//(AttributeList*)&ptr[AttributeOffset];
	            
	            //attribute = new AttributeList(ptr, AttributeOffset+ptr_offset);//(AttributeList*)&ptr[AttributeOffset];
	        	int offset = AttributeOffset+ptr_offset;
	        	int attribute_AttributeType = ByteArrayUtils.read32(ptr, offset);
	        	attribute_Length = ByteArrayUtils.read16(ptr, offset+4) & 0xFFFF;
	        	long RefInode = ByteArrayUtils.read48(ptr, offset+16);
	            /* Exit if no more attributes. AttributeLists are usually not closed by the
	               0xFFFFFFFF endmarker. Reaching the end of the buffer is therefore normal and
	               not an error. */
	            if(debug) System.out.println("attribute.AttributeType="+attribute_AttributeType);
	            if(debug) System.out.println("attribute.Length="+attribute_Length);
	        	if (AttributeOffset + 3 > bufLength) break;
	            if (attribute_AttributeType == 0xFFFFFFFF) break;
	            if (attribute_Length < 3) break;
	            if (AttributeOffset + attribute_Length > bufLength) break;
	            /* Extract the referenced Inode. If it's the same as the calling Inode then ignore
	               (if we don't ignore then the program will loop forever, because for some
	               reason the info in the calling Inode is duplicated here...). */
	            //long RefInode = (((long)attribute.InodeNumberHighPart & 0xFFFF) << 32) | (attribute.InodeNumberLowPart & 0xFFFFFFFF);
	            if(debug) System.out.println("check refNode");
	            if (RefInode == node.NodeIndex) continue;
	            if(debug) System.out.println("RefInode="+RefInode);
	            /* Extract the streamname. I don't know why AttributeLists can have names, and
	               the name is not used further down. It is only extracted for debugging purposes.
	               */
	            
	            // Find the fragment in the MFT that contains the referenced Inode.
	            RawNtfsNode rawNode;
	            if(rawNodes == null){//in some rare cases, the MFT itself wants to read extended contents...
	            	@SuppressWarnings("resource")
					NTFSFileInputStream nfis = new NTFSFileInputStream(MftStream, config, source);
	            	nfis.skip(RefInode * config.BytesPerMftRecord);
	            	byte[] localEntry = new byte[(int)config.BytesPerMftRecord];
	            	nfis.read(localEntry, 0, localEntry.length);
	            	rawNode = new RawNtfsNode(localEntry, config);
	            } else {
	            	rawNode = rawNodes[(int)RefInode];
	            }
	            byte[] buffer = rawNode.getData();
				
				int AttributeOffset2 = ByteArrayUtils.read16(buffer, 20);
				
	            long baseInode = ByteArrayUtils.read48(buffer, 32);
	            
	            if (node.NodeIndex != baseInode) continue;

	            if(debug) System.out.println("call ProcessAttributes()");
	            // Process the list of attributes in the Inode, by recursively calling the ProcessAttributes() subroutine.
	            ProcessAttributes(
	                node,
	                buffer, (AttributeOffset2 & 0xFFFF),
	                (int)config.BytesPerMftRecord - (AttributeOffset2 & 0xFFFF),
	                debug, depth + 1, MftStream, config, source, rawNodes
	            );
	        }
	        if(debug) System.out.println(">>end");
	    }
	    
	    
	    private byte[] readNonResidentData(byte[] RunData, int RunData_offset, int RunDataLength,
				long WantedLength, NtfsConfig config, ReadableSource source) throws IOException {
			
			if (RunData == null || RunDataLength == 0){
				throw new IOException("nothing to read");
			}
			
			NtfsStream stream = new NtfsStream("", 0, WantedLength);
			ProcessFragments(stream, RunData, RunData_offset, RunDataLength + RunData_offset, 0);
			stream.applyFragments();
			
			byte[] data = readFile(stream, config, source);
			
			if(data.length != WantedLength){
				byte[] out = new byte[(int) WantedLength];
				System.arraycopy(data, 0, out, 0, out.length);
				data = out;
			}
			return data;
	    }
		

    public static class ResidentAttribute {
         public int ValueLength;
         public short ValueOffset;
         
         public ResidentAttribute(byte[] buffer, int offset){
        	 ValueLength = ByteArrayUtils.read32(buffer, offset);
        	 ValueOffset = (short)ByteArrayUtils.read16(buffer, offset + 4);
         }
    }
    public static class NonResidentAttribute{
        public long StartingVcn;
        public short RunArrayOffset;
        
        public long AllocatedSize;
        public long DataSize;
        public long InitializedSize;
        public long CompressedSize;    // Only when compressed
        
        public NonResidentAttribute(byte[] buffer, int offset){
        	StartingVcn = ByteArrayUtils.read64(buffer, offset);
        	RunArrayOffset = (short)ByteArrayUtils.read16(buffer, offset + 16);
        	AllocatedSize = ByteArrayUtils.read64(buffer, offset + 24);
        	DataSize = ByteArrayUtils.read64(buffer, offset + 32);
        	InitializedSize = ByteArrayUtils.read64(buffer, offset + 40);
        	CompressedSize = ByteArrayUtils.read64(buffer, offset + 48);
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
        public int AlignmentOrReserved;//Used by EAs and Reparse
        public byte NameLength;
        public byte NameType;//NTFS=0x01, DOS=0x02
        public int NameOffset_struct_getCurrOffset;
    	public AttributeFileName(byte[] buffer, int offset){
        	super.parse(buffer, offset);
        	//if(CompressedSize = read64(buffer, offset + 48)) throw new RuntimeException();
    	}
    }
    
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
    
    public static byte[] readFile(NtfsStream stream, NtfsConfig config, ReadableSource source) throws IOException {
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
    	int sectorCount = (int)MathUtils.clampExp(byteLen, 512);
    	byte[] buffer = new byte[sectorCount];
    	source.readSectors(byteOffset / 512, sectorCount / 512, buffer, 0);
    	return buffer;
    }
    
    
    //---------- Utility Methods: ----------
	
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
}
