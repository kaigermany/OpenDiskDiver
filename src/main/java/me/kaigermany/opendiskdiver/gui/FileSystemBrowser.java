package me.kaigermany.opendiskdiver.gui;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import me.kaigermany.opendiskdiver.datafilesystem.FileEntry;
import me.kaigermany.opendiskdiver.datafilesystem.FileSystem;
import me.kaigermany.opendiskdiver.utils.Utils;
import me.kaigermany.opendiskdiver.writer.DirectFileOutputStream;

public class FileSystemBrowser {
	public static class Node {
	    private final FileEntry entry;
	    private final boolean isDirectory;
	    private final List<Node> children;

	    public Node(FileEntry entry, boolean isDirectory) {
	        this.entry = entry;
	        this.isDirectory = isDirectory;
	        this.children = new ArrayList<>();
	    }

	    public FileEntry getEntry() {
	        return entry;
	    }

	    public boolean isDirectory() {
	        return isDirectory;
	    }

	    public List<Node> getChildren() {
	        return children;
	    }

	    public void addChild(Node child) {
	        children.add(child);
	    }
	}
	
	public static final Comparator<FileEntry> SORT_BY_NAME = new Comparator<FileEntry>() {
		@Override
		public int compare(FileEntry a, FileEntry b) {
			return compareStrings(a.name, b.name);
		}
	};
	public static final Comparator<FileEntry> SORT_BY_PATH = new Comparator<FileEntry>() {
		@Override
		public int compare(FileEntry a, FileEntry b) {
			return compareStrings(a.nameAndPath, b.nameAndPath);
		}
	};
	public static final Comparator<FileEntry> SORT_BY_AGE = new Comparator<FileEntry>() {
		@Override
		public int compare(FileEntry a, FileEntry b) {
			return Long.compare(a.age, b.age);
		}
	};
	public static final Comparator<FileEntry> SORT_BY_SIZE = new Comparator<FileEntry>() {
		@Override
		public int compare(FileEntry a, FileEntry b) {
			return Long.compare(a.size, b.size);
		}
	};
	
	public static int compareStrings(String a, String b){
		int l = Math.min(a.length(), b.length());
		for(int i=0; i<l; i++){
			char c1 = a.charAt(i);
			char c2 = b.charAt(i);
			if(c1 != c2) return Character.compare(c1, c2);
		}
		return Integer.compare(a.length(), b.length());
	}
	
	public static Node buildTree(List<FileEntry> entries) {
        // Create a root node for the tree
        Node root = new Node(null, true); // Root node has no FileEntry
        HashMap<String, Node> pathToNodeMap = new HashMap<>();
        pathToNodeMap.put("", root); // Root path is ""

        for (FileEntry entry : entries) {
            String[] parts = entry.nameAndPath.split("/"); // Split the path by '/'
            StringBuilder currentPath = new StringBuilder();
            Node parent = root;

            for (int i = 0; i < parts.length; i++) {
                currentPath.append(parts[i]);
                if (i < parts.length - 1) {
                    currentPath.append("/");
                }

                String path = currentPath.toString();
                Node currentNode = pathToNodeMap.get(path);

                // If the node doesn't exist, create it
                if (currentNode == null) {
                    boolean isDirectory = (i < parts.length - 1);// || entry.size == 0;
                    currentNode = new Node(
                        (i == parts.length - 1) ? entry : null, // Only associate FileEntry with leaf nodes
                        isDirectory
                    );
                    parent.addChild(currentNode);
                    pathToNodeMap.put(path, currentNode);
                }

                parent = currentNode; // Move to the next level
            }
        }

        return root;
    }
	
	public static void browse(FileSystem fs, UI ui){
		List<FileEntry> files = fs.listFiles();
		
		//TODO
		Node rootnode = buildTree(files);
		
		while(true){
			switch (ui.cooseFromList("Files: " + files.size() + " What do you want do do now?", new String[]{
					"Visit file list",
					"Visit directories",
					"Dump files as sorted list",
					"Dump directories and their content",
					"Back"
			})) {
				case 0:
					
					break;
				case 1:
					
					break;
				case 2:
					dumpSortedFiles(ui, files);
					break;
				case 3:
					exportAllFiles(ui, fs, files);
					break;
				case 4: return;
			}
		}
	}

	private static void dumpSortedFiles(UI ui, List<FileEntry> files){
		Comparator<FileEntry> sorter;
		switch (ui.cooseFromList("How you would like to sort the files?", new String[]{
				"Sort by absolute path",
				"Sort by name",
				"Sort by time",
				"Sort by size",
				"Abort"
		})) {
			case 0: sorter = SORT_BY_PATH; break;
			case 1: sorter = SORT_BY_NAME; break;
			case 2: sorter = SORT_BY_AGE; break;
			case 3: sorter = SORT_BY_SIZE; break;
			case 4: default: return;
		}
		File outFile = ui.saveAs();
		if(outFile != null){
			//aggressive object lifespan control to minimize heap peaks.
			files = new ArrayList<>(files);//Duplicate instance
			files.sort(sorter);
			ArrayList<String[]> table = new ArrayList<>();
			table.add(new String[]{"name", "timestamp", "size", "raw unix time (ms)", "raw size", "absolute path"});
			for(FileEntry e : files){
				table.add(new String[]{
						e.name, new Date(e.age).toString(), Utils.toHumanReadableFileSize(e.size), 
						String.valueOf(e.age), String.valueOf(e.size), e.nameAndPath
				});
			}
			files = null;
			String[] lines = Utils.renderStylizedTable("Drive Dump: " + table.size() + " Files", table, new boolean[]{false, false, true, true, true, false});
			table = null;
			try{
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile), 1 << 20);
				for(String line : lines){
					bos.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
				}
				bos.close();
			}catch(Exception e){
				e.printStackTrace();
			}
			lines = null;
			
			//lets tell the runtime that we can now drop the whole garbage we created here.
			System.gc();
		}
	}
	
	private static void exportAllFiles(UI ui, FileSystem fs, List<FileEntry> files) {
		switch (ui.cooseFromList("Export " + files.size() + " files as *.zip?", new String[]{
				"Yes",
				"Abort"
		})) {
			case 0: {
				File outputZip = ui.saveAs();
				long bytesWritten = 0;
				DiskCopyState state = new DiskCopyState(files.size());
				try{
					ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new DirectFileOutputStream(outputZip), 64 << 20));
					byte[] copyBuffer = new byte[1 << 20];
					int len;
					for(int i=0; i<files.size(); i++){
						FileEntry file = files.get(i);
						state.setCurrentSector(i + 1);
						ui.onDiskCopyStateUpdate(state);
						{
							String name = file.nameAndPath;
							if(name.startsWith("/")) name = name.substring(1);
							ZipEntry e = new ZipEntry(name);
							e.setCreationTime(FileTime.fromMillis(file.age));
							e.setLastModifiedTime(FileTime.fromMillis(file.age));
							zos.putNextEntry(e);
						}
						long byteCount = 0;
						try{
							InputStream is = file.openInputStream();
							while((len = is.read(copyBuffer)) != -1){
								zos.write(copyBuffer, 0, len);
								byteCount += len;
							}
							is.close();
							if(byteCount != file.size){
								throw new IOException("file size diff: expected " + file.size + ", got " + byteCount + ", file: '" + file.nameAndPath + "'");
							}
						}catch(Exception fileCopyException){
							fileCopyException.printStackTrace();
							state.incrUnreadableSectorCount();
						}
						zos.closeEntry();
						bytesWritten += byteCount;
					}
					zos.close();
				}catch(Exception e){
					e.printStackTrace();
				}
				ui.showInfo(new String[]{
						"finished writing " + files.size() + " files.",
						"bytes written: " + bytesWritten,
						state.getUnreadableSectorCount() == 0 ? "No invalid files detected." : ("Found " + state.getUnreadableSectorCount() + " invalid files!")
				});
			}break;
			case 1: default: return;
		}
	}
}
