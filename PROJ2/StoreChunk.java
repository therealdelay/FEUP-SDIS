import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class StoreChunk implements Runnable {
	
	private byte[] buf;
	private int trueBufLength;
	private byte[] chunkBody;
	private Server server;
	private String version;
	private String senderId;
	private ServerFile file;
	private String fileId;
	private String chunkNr;
	private String repDeg;
	
	public StoreChunk(Server server, byte[] buf, int length){
		this.server = server;
		this.buf = buf;
		this.trueBufLength = length;
	}
	
	@Override
	public void run (){	
		if(this.parseRequest())
			return;
		
		FileManager fileManager = this.server.getFileManager();
	
		String chunkId = ServerChunk.toId(this.fileId,Integer.parseInt(this.chunkNr));
		
		if(!this.server.getFileManager().addFile(this.file))
			System.out.println("File already added");
		else
			System.out.println("New File added");
		
		if(fileManager.canSaveChunk(chunkId)){
			this.saveChunk(chunkId);
			this.sendStoredMsg();
		}
		else
			this.printErrMsg("Chunk already saved/owner of file");
		
		this.notifyRemovedThread();
	}
	
	
	private boolean parseRequest(){
		String msg = new String(this.buf);
		String[] parts = msg.split("\r\n");
		
		//Parse header elements
		String[] header = parts[0].split(" ");
		this.version = header[1];
		this.senderId = header[2];
		this.fileId = header[3];
		
		int i = 4;
		String pathName = header[i++];
		for(;i<header.length-4;i++)
			pathName += " "+header[i];
		
		String creationDate = header[i++];
		//System.out.println("Creation date: "+creationDate);
		String peerId = header[i++];
		//System.out.println("Peer ID: "+peerId);
		this.chunkNr = header[i++];
		this.repDeg = header[i].trim();
		
		this.file = new ServerFile(this.fileId,pathName,Long.parseLong(creationDate),Integer.parseInt(this.repDeg),Integer.parseInt(peerId));
		
		//Copy actual body
		int headerLength = parts[0].length()+2;
		int bodyLength = this.trueBufLength - headerLength;
		this.chunkBody = new byte[bodyLength];
		System.arraycopy(this.buf, headerLength, this.chunkBody, 0, bodyLength);
		
		if(this.senderId.compareTo(""+this.server.getId()) != 0){
			return false;
		}
		else
			return true;
	}
	
	private void saveChunk(String chunkId){
		String chunkFileName = chunkId+".chunk";
		FileManager fileManager = this.server.getFileManager();
		FileOutputStream outStream = fileManager.getOutStream(chunkFileName);
		
		try{
			outStream.write(this.chunkBody,0,this.chunkBody.length);
			outStream.close();
		}
		catch(IOException e){
			this.printErrMsg("Unable to save chunk");
		}
		
		fileManager.addChunk(chunkId,this.chunkBody.length,Integer.parseInt(this.repDeg),this.server.getId());
		System.out.println("Chunk nr "+this.chunkNr+" of file "+this.fileId+" saved");
	}
	
	private void sendStoredMsg(){
		String msg = this.getStoredMsg();
		TwinMulticastSocket socket = this.server.getMCsocket();
		DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), socket.getGroup(), socket.getPort());
		
		//Sleep between 0 and MAX_WAIT
		Random rand = new Random();
		int waitTime = rand.nextInt(Server.MAX_WAIT+1);
		
		try{
			Thread.sleep(waitTime);
		}
		catch(InterruptedException e){
			this.printErrMsg("Sleep interrupted");
		}
		
		//Send response
		try{
			socket.send(packet);
		}
		catch(IOException e){
			this.printErrMsg("Unable to send STORED message");
		}
	}
	
	private void notifyRemovedThread(){
		ControlProtocol handler = (ControlProtocol) this.server.removedThreads.get("REMOVED"+this.fileId+"_"+this.chunkNr);
		if(handler != null){
			System.out.println("StoreChunk: Notifying removed thread for chunk "+this.fileId+" "+this.chunkNr);
			handler.notifyPutChunk(this.fileId,this.chunkNr);
		}
	}
	
	private void printErrMsg(String err){
		System.err.println("Error storing chunk "+this.chunkNr+" of file "+this.fileId+": "+err);
	}
	
	private String getStoredMsg(){
		return "STORED "+this.version+" "+this.server.getId()+" "+this.fileId+" "+this.chunkNr+" "+this.repDeg;
	}
}