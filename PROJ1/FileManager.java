import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.lang.*;
import java.util.*;

public class FileManager{
	private ArrayList<ServerFile> files;
	private ArrayList<ServerChunk> chunks;
	private Path WDir;
	
	public FileManager(){
		this.files = new ArrayList<ServerFile>();
		this.chunks = new ArrayList<ServerChunk>();
	}
	
	public void setWDir(Path WDir){
		this.WDir = WDir;
	}
	
	public synchronized void addFile(ServerFile file){
		this.files.add(file);
		System.out.println(this.toString());
	}
	
	public synchronized void addChunk(ServerChunk chunk){
		this.chunks.add(chunk);
		System.out.println(this.toString());
	}

	/*
	public void removeFile(String fileName){
		
		ArrayList<String> filesToRemove = new ArrayList<String>();
		synchronized(this.files){
			ServerFile file;
			for(int i = 0; i < this.files.size(); i++){
				file = this.files.get(i);
				if(file.getId().matches("(.*)"+id+"(.*)")){
					this.files.remove(i);
					filesToRemove.add(file.getId());
					i--;
				}
			}
		}
		
		File file = new File(this.getFilePathName(fileName));
		file.delete();
	}
	*/

	public void freeMem(int memToFree){
		ArrayList<String> filesToRemove = new ArrayList<String>();
		synchronized(this.chunks){
			ServerChunk chunk;
			while(memToFree > 0){
				chunk = this.chunks.get(0);
				this.chunks.remove(0);
				//decChunkRepDeg
				filesToRemove.add(chunk.getId());
				memToFree -= chunk.getSize();
			}
		}
		
		File file;
		for(int i = 0; i < filesToRemove.size(); i++){
			System.out.println("Deleting file: "+filesToRemove.get(i));
			file = new File(this.getFilePathName(filesToRemove.get(i)));
			file.delete();
		}
	}
	
	public void removeAllChunks(String id){
		
		ArrayList<String> filesToRemove = new ArrayList<String>();
		synchronized(this.chunks){
			ServerChunk chunk;
			for(int i = 0; i < this.chunks.size(); i++){
				chunk = this.chunks.get(i);
				if(chunk.getId().matches("(.*)"+id+"(.*)")){
					this.chunks.remove(i);
					//decChunkRepDeg
					filesToRemove.add(chunk.getId());
					i--;
				}
			}
			this.toString();
		}
		
		File file;
		for(int i = 0; i < filesToRemove.size(); i++){
			System.out.println("Deleting file: "+filesToRemove.get(i));
			file = new File(this.getFilePathName(filesToRemove.get(i)));
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
		System.out.println("Inside containsChunk");
		ServerChunk chunk;
		for(int i = 0; i < this.chunks.size(); i++){
			chunk = this.chunks.get(i);
			if(chunk.getId().compareTo(chunkId) == 0)
				return true;
		}
		return false;
	}
	
	public synchronized String toString(){
		return this.files.toString()+System.lineSeparator()+this.chunks.toString();
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
	
	public FileInputStream getInStream(String fileId){
		FileInputStream inStream = null;
		try{
			File inFile = new File(this.getFilePathName(fileId));
			inStream = new FileInputStream(inFile);
		}
		catch(IOException e){}
		
		return inStream;
	}
	
	public FileOutputStream getOutStream(String fileId){
		FileOutputStream outStream = null;
		try{
			File outFile = new File(this.getFilePathName(fileId));
			outFile.createNewFile();
			outStream = new FileOutputStream(outFile);
		}
		catch(IOException e){}
		
		return outStream;
	}
	
	private String getFilePathName(String fileName){
		return this.WDir.toString()+"/"+fileName;
	}
}