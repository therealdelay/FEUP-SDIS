import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.stream.*;
import static java.util.stream.Collectors.*;

import java.security.NoSuchAlgorithmException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class FileManager{
	private int serverId;
	private ArrayList<ServerFile> files;
	private ArrayList<ServerChunk> chunks;
	private Path WDir;
	private long usedMem = 0;
	
	public FileManager(int serverId){
		this.serverId = serverId;
		this.files = new ArrayList<ServerFile>();
		this.chunks = new ArrayList<ServerChunk>();
	}
	
	public void setWDir(Path WDir){
		this.WDir = WDir;
	}
	
	public boolean addFile(ServerFile newFile){
		if(this.containsFile(newFile.getId()))
			return false;
		
		synchronized(this.files){
			this.files.add(newFile);
		}
		System.out.println(this.toString());
		return true;
	}


	public boolean addChunk(ServerChunk newChunk){
		
		if(this.containsChunk(newChunk.getId()))
			return false;

		synchronized(this.chunks){
			this.chunks.add(newChunk);
		}
		System.out.println(this.toString());
		return true;
	}
	
	public synchronized void addChunk(String chunkId, String fileEncryptedId, int size, int repDeg, int peerId){
		this.usedMem += size;
		
		for(ServerChunk chunk : this.chunks){
			if(chunk.getId().compareTo(chunkId) == 0){
				chunk.addToDisk(peerId,size);
				System.out.println(this.toString());
				return;
			}
		}
		
		ServerChunk chunk = new ServerChunk(chunkId, fileEncryptedId, size,repDeg);
		chunk.incRepDeg(peerId);
		this.chunks.add(chunk);
		System.out.println(this.toString());
	}
	
	public synchronized void incChunkRepDeg(String chunkId, String fileEncryptedId, int repDeg, int peerId){
		ServerChunk chunk;
		for(int i = 0; i < this.chunks.size(); i++){
			chunk = this.chunks.get(i);
			if(chunk.getId().compareTo(chunkId) == 0){
				chunk.setRepDeg(repDeg);
				chunk.incRepDeg(peerId);
				return;
			}
		}
		
		chunk = new ServerChunk(chunkId, fileEncryptedId);
		chunk.setRepDeg(repDeg);
		chunk.incRepDeg(peerId);
		this.chunks.add(chunk);
	}
	
	public synchronized void incFileChunkRepDeg(String fileId, int chunkNr, int peerId){
		ServerFile file;
		for(int i = 0; i < this.files.size(); i++){
			file = this.files.get(i);
			if(file.getId().compareTo(fileId) == 0){
				file.incChunksRepDeg(chunkNr,peerId);
				return;
			}
		}
	}
	
	public synchronized boolean decFileChunkRepDeg(String fileId, int chunkNr, int peerId){
		
		ServerFile file;
		for(int i = 0; i < this.files.size(); i++){
			file = this.files.get(i);
			System.out.println("\nDEC REP DEGREE: " + file.getId() + " :::: " + fileId + 
			" :: " + file.getEncryptedId().compareTo(fileId) + " \n");
			if(file.getId().compareTo(fileId) == 0){
				file.decChunksRepDeg(chunkNr,peerId);
			}
		}
		
		String chunkId = ServerChunk.toId(fileId,chunkNr);
		for(ServerChunk chunk : this.chunks){
			System.out.println("\nCHUNK ID: " + chunk.getId() + " :: " + chunkId + "\n");
			if(chunk.getId().compareTo(chunkId) == 0)
				return (chunk.decRepDeg(peerId) && chunk.onDisk());
		}
		
		return false;
	}
	
	public synchronized ServerFile getFile(String fileId){
		ServerFile file;
		for(int i = 0; i < this.files.size(); i++){
			file = this.files.get(i);
			if(file.getId().compareTo(fileId) == 0){
				return file;
			}
		}
		return null;
	}

	public synchronized ArrayList<ServerFile> getFiles(){
			return this.files.stream().map(ServerFile::new).collect(toCollection(ArrayList::new));
	}
	
	public synchronized String getFilePath(String fileId){
		ServerFile file;
		for(int i = 0; i < this.files.size(); i++){
			file = this.files.get(i);
			if(file.getId().compareTo(fileId) == 0){
				return file.getPathName();
			}
		}
		return null;
	}
	
	public synchronized int getFileRepDeg(String fileId){
		ServerFile file;
		for(int i = 0; i < this.files.size(); i++){
			file = this.files.get(i);
			if(file.getId().compareTo(fileId) == 0){
				return file.getReplicationDeg();
			}
		}
		return -1;
	}

	public ArrayList<ServerChunk> freeMem(int maxMem){
		ArrayList<ServerChunk> chunksToRemove = new ArrayList<ServerChunk>();
		int currChunk = 0;
		synchronized(this.chunks){
			ServerChunk chunk;
			while(this.usedMem > maxMem){
				chunk = this.chunks.get(currChunk);
				currChunk++;
				if(!chunk.onDisk())
					continue;
				
				System.out.println("UsedMem: "+this.usedMem+" maxMem: "+maxMem);
				chunk.removeFromDisk(this.serverId);
				chunksToRemove.add(chunk);
				this.usedMem -= chunk.getSize();
			}
		}
		
		File file;
		for(int i = 0; i < chunksToRemove.size(); i++){
			System.out.println("Deleting chunk: "+chunksToRemove.get(i).getId());
			file = new File(this.getSWDFilePathName(chunksToRemove.get(i).getFileName()));
			file.delete();
		}
		
		return chunksToRemove;
	}
	
	public void removeAllChunks(String fileId, String secretKey){ 
		Cipher cipher; 
		SecretKeySpec key;
		
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			byte[] decodedKey = Base64.getDecoder().decode(secretKey);
			key = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

		} catch(NoSuchAlgorithmException | NoSuchPaddingException e){
			System.err.println("Error Removing All Chunks");
			return;
		}
		
		//Delete file if exists
		synchronized(this.files){
			ServerFile serverFile;
			for(int i = 0; i < this.files.size(); i++){
				serverFile = this.files.get(i);
				if(serverFile.getId().compareTo(fileId) == 0){
					
					//delete if user is authorized
					try {
						
						cipher.init(Cipher.DECRYPT_MODE, key);
						byte[] encryptedFileName = Base64.getDecoder().decode(serverFile.getEncryptedId());
						cipher.doFinal(encryptedFileName);

						//it can decrypt
						this.files.remove(i);
						break;
					}
					catch(Exception e){
						System.out.println("The client ins't authorize to delete this file");
					}
				}
			}
		}
		
		//Delete chunks of fileId
		ArrayList<String> chunksToRemove = new ArrayList<String>();
		synchronized(this.chunks){
			ServerChunk chunk;
			for(int i = 0; i < this.chunks.size(); i++){
				chunk = this.chunks.get(i);
				if(chunk.getId().matches("(.*)"+fileId+"(.*)")){

					try {
						cipher.init(Cipher.DECRYPT_MODE, key);
						byte[] encryptedFileName = Base64.getDecoder().decode(chunk.getFileEncryptedId());
						cipher.doFinal(encryptedFileName);
	
						this.chunks.remove(i);
						this.usedMem -= chunk.getSize();
						chunksToRemove.add(chunk.getId());
						i--;
					}
					catch(Exception e) {
						System.out.println("The client ins't authorize to delete this file");
					}
				}
			}
			this.toString();
		}
		
		File file;
		String fileName;
		for(int i = 0; i < chunksToRemove.size(); i++){
			fileName = chunksToRemove.get(i)+".chunk";
			System.out.println("Deleting file: "+fileName);
			file = new File(this.getSWDFilePathName(fileName));
			file.delete();
		}
	}
		
	public synchronized boolean containsFile(String fileId){
		ServerFile file;
		for(int i = 0; i < this.files.size(); i++){
			file = this.files.get(i);
			if(file.getId().compareTo(fileId) == 0)
				return true;
		}
		return false;
	}
	
	public synchronized boolean ownsChunk(String chunkId){
		
		String fileId = chunkId.split("_")[0];
		System.out.println(fileId);
		
		for(ServerFile file : this.files){
			System.out.println(file.getInitPeerId());
			if(file.getId().compareTo(fileId) == 0 && file.getInitPeerId() == this.serverId)
				return true;
		}
		
		return false;
	}
	
	public synchronized boolean containsChunk(String chunkId){
		ServerChunk chunk;
		for(int i = 0; i < this.chunks.size(); i++){
			chunk = this.chunks.get(i);
			if(chunk.getId().compareTo(chunkId) == 0)
				return chunk.onDisk();
		}
		return false;
	}
		
	public synchronized int getPerceivedRepDeg(String chunkId){
		ServerChunk chunk;
		for(int i = 0; i < this.chunks.size(); i++){
			chunk = this.chunks.get(i);
			if(chunk.getId().compareTo(chunkId) == 0)
				return chunk.getPerceivedRepDeg();
		}
		return -1;
	}
	
	public int getFileTotalChunks(String fileName){
		File file = new File(fileName);
		float size = (float) file.length();
		System.out.println("Size: "+size);
		
		int total = (int) size/Server.MAX_CHUNK_SIZE+1;
		return total;
	}
	
	public FileInputStream getFileInStream(String filePath){
		FileInputStream inStream = null;
		try{
			File inFile = new File(filePath);
			inStream = new FileInputStream(inFile);
		}
		catch(IOException e){}
		
		return inStream;
	}
	
	private String getSWDFilePathName(String fileName){
		return this.WDir.toString()+"/"+fileName;
	}
	
	public FileOutputStream getFileOutStream(String filePath){
		FileOutputStream outStream = null;
		try{
			File outFile = new File(filePath);
			outFile.createNewFile();
			outStream = new FileOutputStream(outFile);
		}
		catch(IOException e){}
		
		return outStream;
	}
	
	public FileInputStream getInStream(String fileName){
		FileInputStream inStream = null;
		try{
			File inFile = new File(this.getSWDFilePathName(fileName));
			inStream = new FileInputStream(inFile);
		}
		catch(IOException e){}
		
		return inStream;
	}
	
	public FileOutputStream getOutStream(String fileName){
		FileOutputStream outStream = null;
		try{
			File outFile = new File(this.getSWDFilePathName(fileName));
			outFile.createNewFile();
			outStream = new FileOutputStream(outFile);
		}
		catch(IOException e){
			System.err.println("Error creating file to save chunk : " + e);
		}
		
		return outStream;
	}
	
	public void deleteSWDFile(String fileName){
		File file = new File(this.getSWDFilePathName(fileName));
		file.delete();
	}
	
	public synchronized long getUsedMem(){
		return this.usedMem;
	}

	public String getMetaBlock(int blockNr){
		int currBlock = 0;
		int filesIndex = 0, chunksIndex = 0;
		boolean end = false;
		String block = "";
		String meta;

		while(currBlock <= blockNr){

			while(block.length() < Server.MAX_CHUNK_SIZE){

				if(filesIndex < this.files.size()){
					meta = this.files.get(filesIndex).toMeta();
				}
				else if(chunksIndex < this.chunks.size()){
					meta = this.chunks.get(chunksIndex).toMeta();
				}
				else{
					end = true;
					break;
				}

				if(block.length()+meta.length() > Server.MAX_CHUNK_SIZE){
					block = "";
					break;
				}
				else{
					if(block.compareTo("") == 0)
						block = meta;
					else
						block += "|"+meta;

					if(filesIndex < this.files.size())
						filesIndex++;
					else
						chunksIndex++;
				}
			}
			currBlock++;

			if(end)
				break;
		}

		currBlock--;

		if(currBlock == blockNr)
			return block;
		else
			return "";
	}

	public synchronized String toString(){
		String lineSep = System.lineSeparator();
		String doubleLineSep = lineSep+lineSep;
		return "Memory used: "+this.usedMem+" of "+Server.MAX_MEM+lineSep+
			   "Files:"+lineSep+
			   this.files.stream().map(Object::toString).collect(Collectors.joining(doubleLineSep))+doubleLineSep+
			   "Chunks:"+lineSep+
			   this.chunks.stream().map(Object::toString).collect(Collectors.joining(doubleLineSep));
	}
}