import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class ReclaimProtocol implements Runnable {
	
	private Server server;
	private int size;
	private ArrayList<ServerChunk> deletedChunks;
	
	public ReclaimProtocol(Server server, int size){
		this.server = server;
		this.size = size;
	}
	
	@Override
	public void run (){
		this.reclaimSpace();
		this.sendRemovedMsgs();
	}
	
	private void reclaimSpace(){
		int usedMem = this.server.getFileManager().getUsedMem();
		if(this.size >= usedMem){
			System.out.println("No need to delete files. " + this.size + " " + usedMem);
		}
		else
			System.out.println("Deleting files and freeing at least " +(usedMem-this.size));
		FileManager fileManager = this.server.getFileManager();
		
		this.deletedChunks = fileManager.freeMem(this.size);
	}
	
	private void sendRemovedMsgs(){
		ServerChunk chunk;
		for(int i = 0; i < this.deletedChunks.size(); i++){
			chunk = this.deletedChunks.get(i);
			this.sendRemovedMsg(chunk.getFileId(), chunk.getChunkNr());
			System.out.println("Sent REMOVED for chunk: "+chunk.getId());
		}
	}
	
	private void sendRemovedMsg(String fileId, int chunkNr){
		String msg = this.getRemovedMsg(fileId, chunkNr);
		TwinMulticastSocket socket = this.server.getMCsocket();
		DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), socket.getGroup(), socket.getPort());
		
		//Send msg
		try{
			socket.send(packet);
		}
		catch(IOException e){
			this.printErrMsg("Unable to send REMOVED message");
		}
	}
	
	private String getRemovedMsg(String fileId, int chunkNr){
		return "REMOVED "+this.server.getVersion()+" "+this.server.getId()+" "+fileId + " " + chunkNr;
	}
	
	private void printErrMsg(String err){
		System.err.println("Error removing chunk " /*+this.chunkNr*/+": "+err);
	}
}