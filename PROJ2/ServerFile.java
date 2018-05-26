import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.nio.file.attribute.*;
import java.net.*;
import java.lang.*;
import java.security.*;
import java.util.*;
import java.util.stream.*;

import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Cipher;
import java.util.Base64;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.BadPaddingException;



import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class ServerFile implements Comparable<ServerFile>{
	private String id;
	private String encryptedId;
	private String pathName;
	private long lastModified;
	//private FileTime creationDate;
	private int replicationDeg;
	private int initPeerId;
	private ArrayList<ArrayList<Integer>> chunksRepDeg;
	
	public ServerFile(String fileName, int replicationDeg, long lastModified, SecretKeySpec secretKey, int peerId){
		this.pathName = ServerFile.toPathName(fileName);
		this.id = ServerFile.toId(fileName);
		this.encryptedId = ServerFile.toEncryptedId(fileName, secretKey);
		this.lastModified = lastModified;
		//this.readCreationDate();
		this.replicationDeg = replicationDeg;
		this.initPeerId = peerId;
		this.chunksRepDeg = new ArrayList<ArrayList<Integer>>();
	}
	
	public ServerFile(String fileId, String encryptedFileID, String pathName, long lastModified, int replicationDeg, int peerId){
		this.id = fileId;
		this.encryptedId = encryptedFileID; 
		this.pathName = pathName;
		this.lastModified = lastModified;
		//this.readCreationDate();
		this.replicationDeg = replicationDeg;
		this.initPeerId = peerId;
		this.chunksRepDeg = new ArrayList<ArrayList<Integer>>();
	}


	public ServerFile(ServerFile file){
		this.id = file.getId();
		this.encryptedId = file.getEncryptedId(); 
		this.pathName = file.getPathName();
		this.lastModified = file.getLastModifiedDate();
		//this.readCreationDate();
		this.replicationDeg = file.getReplicationDeg();
		this.initPeerId = file.getInitPeerId();
		this.chunksRepDeg =  new ArrayList<ArrayList<Integer>>();
	}

	/*
	public void readCreationDate(){
		Path file = Paths.get(this.pathName);
		try{
			BasicFileAttributes attr = Files.getFileAttributeView(file,BasicFileAttributeView.class).readAttributes();
			this.creationDate = attr.creationTime();
			//System.out.println("Creation Time: "+this.creationDate);
		}
		catch(Exception e){
			this.creationDate = null;
			e.printStackTrace();
		}
	}*/
	
	public String getPathName(){
		return this.pathName;
	}
	
	public String getId(){
		return this.id;
	}

	public String getEncryptedId() {
		return this.encryptedId;
	}

	public long getLastModifiedDate() {
		return this.lastModified;
	}

	public String getLastModifiedDateStr() {
		return FileTime.fromMillis(this.lastModified).toString();
	}
	
	public static String toRelativeName(String fileName){
		File file = new File(fileName);
		return file.getName();
	}
	
	public static String toPathName(String fileName){
		File file = new File(fileName);
		return file.getAbsolutePath();
	}
	
	public static String toId(String fileName){

		String path = ServerFile.toPathName(fileName);

		try{
			
			MessageDigest digest = MessageDigest.getInstance("SHA-256"); 
			byte[] hash = digest.digest(path.getBytes("UTF-8")); 
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

	public static String toEncryptedId(String fileName, SecretKeySpec secretKey){
		String path = ServerFile.toPathName(fileName);
		File file = new File(path);
		path += "_" + file.lastModified();

		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
			
			byte[] tmp = Base64.getEncoder().encode(cipher.doFinal(path.getBytes()));
			return new String(tmp);
			
		} 
		catch(NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException e){
			System.err.println("Error encrypting filename 22 : " + e);
		}

		return null;
		
	}

	public boolean testKey(SecretKeySpec secretKey){

		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, secretKey);
			cipher.doFinal(this.encryptedId.getBytes());
			return true;
			
		}
		catch(NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException e){
			System.err.println("Error decrypting file "+this.pathName);
		}

		return false;
	}
	
	public int getReplicationDeg(){
		return this.replicationDeg;
	}
	
	public int getInitPeerId(){
		return this.initPeerId;
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
		if(this.chunksRepDeg.size() == 0)
			return false;
		
		ArrayList<Integer> peers = this.chunksRepDeg.get(chunkNr);
		peers.remove(Integer.valueOf(peerId));
		return peers.size() < this.replicationDeg;
	}
	
	public String toMsg(){

		File file = new File(this.pathName);

		return this.encryptedId + " " + this.id+" "+this.pathName+" "+this.lastModified+" "+this.initPeerId;
	}
	

	public String toList(){
		String lineSep = System.lineSeparator();
		return  "	Name: "+ServerFile.toRelativeName(this.pathName)+lineSep+
				"	Path: "+this.pathName+lineSep+
				"	Last Modified: "+this.getLastModifiedDateStr();
				
	}

  	@Override
	public String toString(){
		String lineSep = System.lineSeparator();
		return  "	PathName: "+this.pathName+lineSep+
				"	ID: "+this.id+lineSep+
				"	InitPeer: "+this.initPeerId+lineSep+
				"	Expected replication degree: "+this.replicationDeg+lineSep+
				"	Current chunks replication degree: "+ this.chunksRepDeg.stream().map(peers -> Integer.toString(peers.size())).collect(Collectors.joining(", "));
	}

	@Override
  	public int compareTo(ServerFile file) {
  		String relativeName = ServerFile.toRelativeName(this.pathName);
  		String compRelativeName = ServerFile.toRelativeName(file.getPathName());
   	 	int comp = relativeName.compareTo(compRelativeName);
   	 	if(comp == 0){
   	 		comp = this.pathName.compareTo(file.getPathName());
   	 		if(comp == 0){
   	 			comp = Long.compare(this.lastModified, file.getLastModifiedDate());
   	 		}
   	 	}
   	 	
   	 	return comp;
  	}
}