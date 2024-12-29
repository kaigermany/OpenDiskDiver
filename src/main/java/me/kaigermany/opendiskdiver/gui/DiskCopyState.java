package me.kaigermany.opendiskdiver.gui;

public class DiskCopyState {
	private final long numSectors;
	private long currentSector;
	private long unreadableSectorCount;
	
	private Object uiPrivateContext;
	
	// ----- Handler ------
	
	public DiskCopyState(long numSectors){
		this.numSectors = numSectors;
		this.currentSector = 0;
		this.unreadableSectorCount = 0;
		this.uiPrivateContext = null;
	}
	
	public void setCurrentSector(long currentSector){
		this.currentSector = currentSector;
	}
	
	public void incrUnreadableSectorCount(){
		this.unreadableSectorCount++;
	}
	
	// ----- UI ------

	public long getNumSectors(){
		return numSectors;
	}
	
	public long getCurrentSector(){
		return currentSector;
	}
	
	public long getUnreadableSectorCount(){
		return unreadableSectorCount;
	}
	
	public void setUiPrivateContext(Object context){
		this.uiPrivateContext = context;
	}
	
	public <T> T getUiPrivateContext(Class<T> clazz){
		return clazz.cast(this.uiPrivateContext);
	}
}
