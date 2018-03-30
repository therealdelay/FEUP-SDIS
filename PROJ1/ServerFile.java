import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.net.*;
import java.lang.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;


public class ServerFile{
	private String fileName;
	private String id;
	private ArrayList<Integer> chunksRepDeg;
	private int replicationDeg;
	
	public ServerFile(String fileName, int replicationDeg){
		this.fileName = fileName;
		this.id = ServerFile.toId(fileName); 
		this.replicationDeg = replicationDeg;
		this.chunksRepDeg = new ArrayList<Integer>();
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

	public ArrayList<Integer> getChunksRepDeg(){
		return this.chunksRepDeg;
	}

	public void incChunksRepDeg(int index){
		this.chunksRepDeg.set(index,this.chunksRepDeg.get(index)+1);
	}

	public void decChunksRepDeg(int index){
		this.chunksRepDeg.set(index,this.chunksRepDeg.get(index)-1);
	}
	
	public String toString(){
		return this.fileName+":"+this.id+":"+this.replicationDeg;
	}
}