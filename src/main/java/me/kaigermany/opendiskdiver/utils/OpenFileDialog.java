package me.kaigermany.opendiskdiver.utils;

import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.io.File;

import javax.swing.JFileChooser;

public class OpenFileDialog {
	public static File userOpenFile(String title, File defaultSelection){
		return userFileDialog(title, false, defaultSelection);
	}
	
	public static File userSaveFile(String title, File defaultSelection){
		return userFileDialog(title, true, defaultSelection);
	}
	
	//crates a tiny dummy window, forces it to the top,
	//then spawns the file dialog.
	//after interaction, close everything.
	public static File userFileDialog(String title, boolean isSaveFileDialog, File defaultSelection){
		//Do an ugly trick to force the dialog window right to the top.
		Window window = new Window(new Frame());
		window.setAlwaysOnTop(true);
		
		//window.setSize(100, 100);
		window.setLocationRelativeTo(null);
		window.setVisible(true);
		
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(title);
		if (defaultSelection != null) {
			chooser.setSelectedFile(defaultSelection);
		}
		
		window.setAlwaysOnTop(false);
		
		//move our dummy frame behind the dialog screen
		//i don't like this code very much but i didn't found any other way.
		Thread t = new Thread(new Runnable() {
			public void run() {
				window.setSize(100, 100);
				while(window.isVisible()){
					try{
						Point p = chooser.getLocationOnScreen();
						Rectangle r = chooser.getBounds();
						window.setLocation((int)(p.getX() + r.getWidth() / 2), (int)(p.getY() + r.getHeight() / 2));
						Thread.sleep(10);
					}catch(Exception e){}
				}
			}
		});
		t.setDaemon(true);
		t.start();
		
		//Now do the actual request:
		File selectedFile = null;
		int resultCode;
		if(isSaveFileDialog){
			resultCode = chooser.showSaveDialog(window);
		} else {
			resultCode = chooser.showOpenDialog(window);
		}
		if (resultCode == 0) {
			selectedFile = chooser.getSelectedFile();
		}
		
		//dispose our workaround
		window.getOwner().dispose();
		
		return selectedFile;
	}
}
