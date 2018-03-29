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
	
	public ReclaimProtocol(Server server, int size){
		this.server = server;
		this.size = size;
	}
	
	@Override
	public void run (){
		this.reclaimSpace();
		this.sendReclaimMsg();
	}
	
	private void reclaimSpace(){
		if(this.size >= this.server.usedMem){
			System.out.println("No need to delete files. " + this.size + " " + this.server.usedMem);
		}
		System.out.println("Delete files. " + this.size + " " + this.server.usedMem);
		FileManager fileManager = this.server.getFileManager();
		
		fileManager.freeMem(this.server.usedMem - this.size);
		
		//fileManager.removeAllChunks(id); // aqui devia ser removeChunk(id,chunkNr);
		System.out.println("Deleting chunk " + " from file ");
	}
	
	private void sendReclaimMsg(){
		String msg = this.getReclaimMsg();
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
	
	private String getReclaimMsg(){
		return "RECLAIM "+this.server.getVersion()+" "+this.server.getId()+" "/*+this.fileId + " " + this.chunkNr*/;
	}
	
	private void printErrMsg(String err){
		System.err.println("Error removing chunk " /*+this.chunkNr*/+": "+err);
	}
}