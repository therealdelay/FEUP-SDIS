import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Collectors.*;

public class ServerChunk{
	private String id;
	private String fileId;
	private String fileEncryptedId;
	private int chunkNr;
	private long size;
	private boolean onDisk;
	private int repDeg = -1;
	private ArrayList<Integer> peers; //keeps track of the unique peers that have stored the chunk
	
	public ServerChunk(String id, String fileEncryptedId){
		this.id = id;
		
		String[] parts = this.id.split("_");
		this.fileId = parts[0];
		this.fileEncryptedId = fileEncryptedId;
		this.chunkNr = Integer.parseInt(parts[1]);
		this.size = size;
		this.peers = new ArrayList<Integer>();
		this.onDisk = false;
	}
	
	public ServerChunk(String id, String fileEncryptedId, long size, int repDeg){
		this.id  = id;

		String[] parts = this.id.split("_");
		this.fileId = parts[0];
		this.fileEncryptedId = fileEncryptedId;
		this.chunkNr = Integer.parseInt(parts[1]);
		
		this.size = size;
		this.repDeg = repDeg;
		this.peers = new ArrayList<Integer>();
		this.onDisk = true;
	}


	public ServerChunk(String id, String fileEncryptedId, ArrayList<Integer> peers, int repDeg){
		this.id  = id;

		String[] parts = this.id.split("_");
		this.fileId = parts[0];
		this.fileEncryptedId = fileEncryptedId;
		this.chunkNr = Integer.parseInt(parts[1]);

		this.size = 0;
		this.repDeg = repDeg;
		this.peers = peers;
		this.onDisk = false;
	}

	public ServerChunk(ServerChunk chunk){
		this.id = chunk.getId();
		this.fileId = chunk.getFileId();
		this.fileEncryptedId = chunk.getFileEncryptedId();
		this.chunkNr = chunk.getChunkNr();
		this.size = chunk.getSize();
		this.repDeg = chunk.getReplicationDeg();
		this.peers = chunk.getPeers();
		this.onDisk = chunk.onDisk();
	}
	
	public static String toId(String fileId, int chunkNr){
 		String[] parts = fileId.split("\\.");
		String chunkId = parts[0]+"_"+chunkNr;
		return chunkId;
	}
	
	public void incRepDeg(int peerId){		
		//System.out.println("Peer: "+peerId);
		if(!this.peers.contains(peerId))
			peers.add(peerId);
		
		//String state = "Curr peers: "+Arrays.toString(this.peers.toArray());
		//System.out.println(state);
	}
		
	public boolean decRepDeg(int peerId){
		this.peers.remove(Integer.valueOf(peerId));
		if(this.repDeg != -1)
			return this.peers.size() < this.repDeg;
		else
			return false;
	}
		
	public ArrayList<Integer> getPeers(){
		return this.peers;
	}

	public int getPerceivedRepDeg(){
		return this.peers.size();
	}

	public int getReplicationDeg(){
		return this.repDeg;
	}
	
	public String getId(){
		return this.id;
	}
	
	public String getFileId(){
		return this.fileId;
	}

	public String getFileEncryptedId() {
		return this.fileEncryptedId;
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
	
	public void setRepDeg(int repDeg){
		this.repDeg = repDeg;
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
	
	public void addToDisk(int peerId, int size){
		this.onDisk = true;
		this.size = size;
		this.incRepDeg(peerId);
	}
	
	public void removeFromDisk(int peerId){
		this.onDisk = false;
		this.decRepDeg(peerId);
	}

	public String toMsg(){
		return this.fileEncryptedId+" "+this.fileId+" "+this.chunkNr;
	}

	public String toMeta(){
		String peersStr = this.peers.stream().map(Object::toString).collect(Collectors.joining(","));
		return "CHUNK "+this.id+" "+this.fileEncryptedId+" "+peersStr+" "+this.repDeg;
	}	

	public String toString(){
		String newLine = System.lineSeparator();
		String repDegStr = this.repDeg == -1 ? "-" : ""+this.repDeg;
		
		return "	ID: "+this.id+newLine+
		//	   "	FileID: "+this.fileId+newLine+
		//	   "	Number: "+this.chunkNr+newLine+
			   "	Size: "+this.size+newLine+
			   "	RepDeg: "+repDegStr+newLine+
			   "	Perceived RepDeg: "+this.getPerceivedRepDeg()+newLine+
			   "	On disk: "+this.onDisk;
	}
}