
public class ServerChunk{
	private String id;
	private String fileId;
	private int chunkNr;
	private long size;
	
	public ServerChunk(String id, long size){
		this.id  = id;
		
		String[] parts = this.id.split("\\.")[0].split("_");
		this.fileId = parts[0];
		this.chunkNr = Integer.parseInt(parts[1]);
		
		this.size = size;
	}
	
	public String getId(){
		return this.id;
	}
	
	public String getFileId(){
		return this.fileId;
	}
	
	public String getFileName(){
		return this.id+".chunk";
	}
	
	public int getChunkNr(){
		return this.chunkNr;
	}
	
	public long getSize(){
		return this.size;
	}

	public String toString(){
		return "	ID: "+this.id+System.lineSeparator()+
			   "	Size: "+this.size;
	}
}