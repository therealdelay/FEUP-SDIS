import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.lang.*;
import java.util.*;

public class FileManager{
	private ArrayList<ServerFile> files;
	private Path WDir;
	private ArrayList<ServerChunk> chunks;
	
	public FileManager(){
		this.files = new ArrayList<ServerFile>();
	}
	
	public void setWDir(Path WDir){
		this.WDir = WDir;
	}
	
	public synchronized void addFile(ServerFile file){
		this.files.add(file);
	}
	
	public synchronized void addChunks(ServerChunk chunk){
		this.chunks.add(chunk);
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
					filesToRemove.add(chunks.getId());
					i--;
				}
			}
		}
		
		File file;
		for(int i = 0; i < filesToRemove.size(); i++){
			System.out.println("Deleting file: "+filesToRemove.get(i));
			file = new File(this.getFilePathName(filesToRemove.get(i)));
			file.delete();
		}
	}
	
	public synchronized boolean containsFile(String fileName){
		ServerFile file;
		for(int i = 0; i < this.files.size(); i++){
			file = this.files.get(i);
			if(file.getId().compareTo(fileName) == 0)
				return true;
		}
		return false;
	}

	public synchronized boolean containsChunk(String chunkName){
		ServerChunk chunk;
		for(int i = 0; i < this.chunks.size(); i++){
			chunk = this.chunks.get(i);
			if(chunk.getId().compareTo(chunkName) == 0)
				return true;
		}
		return false;
	}
	
	public synchronized String toString(){
		return this.files.toString();
	}
	
	public int getFileTotalChunks(String fileName){
		File file = new File(this.getFilePathName(fileName));
		float size = (float) file.length();
		
		int total = (int) size/Server.MAX_CHUNK_SIZE+1;
		
		return total;
	}
	
	public FileInputStream getInStream(String fileName){
		FileInputStream inStream = null;
		try{
			File inFile = new File(this.getFilePathName(fileName));
			inStream = new FileInputStream(inFile);
		}
		catch(IOException e){}
		
		return inStream;
	}
	
	public FileOutputStream getOutStream(String fileName){
		FileOutputStream outStream = null;
		try{
			File outFile = new File(this.getFilePathName(fileName));
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