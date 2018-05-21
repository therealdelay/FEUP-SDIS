import java.util.ArrayList;
import java.util.Arrays;

public class ServerChunk{
	private String id;
	private String fileId;
	private int chunkNr;
	private long size;
	private boolean onDisk;
	private ArrayList<Integer> peers; //keeps track of the unique peers that have stored the chunk
	
	public ServerChunk(String id){
		this.id = id;
		
		String[] parts = this.id.split("\\.")[0].split("_");
		this.fileId = fileId;
		this.chunkNr = chunkNr;
		this.size = size;
		this.peers = new ArrayList<Integer>();
		this.onDisk = false;
	}
	
	public ServerChunk(String id, long size){
		this.id  = id;
		
		String[] parts = this.id.split("\\.")[0].split("_");
		this.fileId = parts[0];
		this.chunkNr = Integer.parseInt(parts[1]);
		
		this.size = size;
		this.peers = new ArrayList<Integer>();
		this.onDisk = true;
	}
	
	public static String toId(String fileId, int chunkNr){
		String[] parts = fileId.split("\\.");
		String chunkId = parts[0]+"_"+chunkNr;
		return chunkId;
	}
	
	public void incChunkRepDeg(int peerId){
		
		System.out.println("Peer: "+peerId);
		if(!this.peers.contains(peerId))
			peers.add(peerId);
		
		String state = "Curr peers: "+Arrays.toString(this.peers.toArray());
		System.out.println(state);
	}
		
	
	public int getPerceivedRepDeg(){
		return this.peers.size();
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
	
	public void setSize(long size){
		this.size = size;
	}
	
	public boolean onDisk(){
		return this.onDisk;
	}
	
	public void setOnDisk(boolean status){
		this.onDisk = status;
	}

	public String toString(){
		String newLine = System.lineSeparator();
		
		return "	ID: "+this.id+newLine+
			   "	Size: "+this.size+newLine+
			   "	Perceived RepDeg: "+this.getPerceivedRepDeg()+newLine+
			   "	On disk: "+this.onDisk;
	}
}