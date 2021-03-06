import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.stream.*;

public class FileManager{
	private ArrayList<ServerFile> files;
	private ArrayList<ServerChunk> chunks;
	private Path WDir;
	private int usedMem = 0;
	
	public FileManager(){
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
	
	public synchronized void addChunk(ServerChunk chunk){
		this.chunks.add(chunk);
		this.usedMem += chunk.getSize();
		System.out.println(this.toString());
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
			if(file.getId().compareTo(fileId) == 0){
				return file.decChunksRepDeg(chunkNr,peerId);
			}
		}
		
		return false;
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
		synchronized(this.chunks){
			ServerChunk chunk;
			while(this.usedMem > maxMem){
				System.out.println("UsedMem: "+this.usedMem+" maxMem: "+maxMem);
				chunk = this.chunks.get(0);
				this.chunks.remove(0);
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
	
	public void removeAllChunks(String fileId){
		
		//Delete file if exists
		synchronized(this.files){
			ServerFile serverFile;
			for(int i = 0; i < this.files.size(); i++){
				serverFile = this.files.get(i);
				if(serverFile.getId().compareTo(fileId) == 0){
					this.files.remove(i);
					break;
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
					this.chunks.remove(i);
					this.usedMem -= chunk.getSize();
					chunksToRemove.add(chunk.getId());
					i--;
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

	public synchronized boolean containsChunk(String chunkId){
		ServerChunk chunk;
		for(int i = 0; i < this.chunks.size(); i++){
			chunk = this.chunks.get(i);
			if(chunk.getId().compareTo(chunkId) == 0)
				return true;
		}
		return false;
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
		catch(IOException e){}
		
		return outStream;
	}
	
	public void deleteSWDFile(String fileName){
		File file = new File(this.getSWDFilePathName(fileName));
		file.delete();
	}
	
	public synchronized int getUsedMem(){
		return this.usedMem;
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