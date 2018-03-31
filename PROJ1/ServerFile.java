import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.net.*;
import java.lang.*;
import java.security.*;
import java.util.*;
import java.util.stream.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class ServerFile{
	private String fileName;
	private String id;
	private ArrayList<ArrayList<Integer>> chunksRepDeg;
	private int replicationDeg;
	
	public ServerFile(String fileName, int replicationDeg){
		this.fileName = fileName;
		this.id = ServerFile.toId(fileName); 
		this.replicationDeg = replicationDeg;
		this.chunksRepDeg = new ArrayList<ArrayList<Integer>>();
	}
	
	public String getFileName(){
		return this.fileName;
	}
	
	public String getId(){
		return this.id;
	}
	
	public static String toId(String fileName){
		try{ 
			MessageDigest digest = MessageDigest.getInstance("SHA-256"); 
			byte[] hash = digest.digest(fileName.getBytes("UTF-8")); 
			StringBuffer hexString = new StringBuffer(); 
			for (int i = 0; i < hash.length; i++) {
				String hex = Integer.toHexString(0xff & hash[i]); 
				if(hex.length() == 1) 
					hexString.append('0'); 
				hexString.append(hex); 
			} 
			return hexString.toString(); 
		} 
		catch(Exception ex){
			throw new RuntimeException(ex); 
		}
	}
	
	public int getReplicationDeg(){
		return this.replicationDeg;
	}

	public ArrayList<ArrayList<Integer>> getChunksRepDeg(){
		return this.chunksRepDeg;
	}
		
	public void incChunksRepDeg(int chunkNr, int peerId){
		while(this.chunksRepDeg.size() <= chunkNr){
			this.chunksRepDeg.add(new ArrayList<Integer>());
		}
		ArrayList<Integer> peers = this.chunksRepDeg.get(chunkNr);
		if(!peers.contains(peerId))
			peers.add(peerId);
	}

	public boolean decChunksRepDeg(int chunkNr, int peerId){
		ArrayList<Integer> peers = this.chunksRepDeg.get(chunkNr);
		peers.remove(new Integer(peerId));
		return peers.size() < this.replicationDeg;
	}
	
	public String toString(){
		String lineSep = System.lineSeparator();
		return  "	PathName: "+this.fileName+lineSep+
				"	ID: "+this.id+lineSep+
				"	Expected replication degree "+this.replicationDeg+lineSep+
				"	Current chunks replication degree: "+ this.chunksRepDeg.stream().map(peers -> Integer.toString(peers.size())).collect(Collectors.joining(", "));
	}
}