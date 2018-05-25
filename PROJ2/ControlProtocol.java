import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class ControlProtocol implements Runnable {
	
	private byte[] buf;
	private int trueBufLength;
	private byte[] chunkBody;
	private Server server;
	private String msgType;
	private String version;
	private String senderId;
	private String fileId;
	private String chunkNr;
	private String repDeg;
	private boolean sendChunk;
	
	private boolean receivedPutChunk = false;
	
	public ControlProtocol(Server server, byte[] buf){
		this.server = server;
		this.buf = buf;
	}
	
	@Override
	public void run (){		
		if(this.parseRequest())
			return;
		
		this.processMsg();
		
	}
	
	private boolean parseRequest(){
		String msg = new String(this.buf);
		String[] parts = msg.split("\r\n");
		
		//Parse header elements
		String[] header = parts[0].trim().split(" ");
		this.msgType = header[0];
		this.version = header[1];
		this.senderId = header[2];
		this.fileId = header[3];
		if(header.length > 4){
			this.chunkNr = header[4];
			if(header.length > 5)
				this.repDeg = header[5];
		}
		
		if(this.senderId.compareTo(""+this.server.getId()) == 0){
			return true;
		}
			
		return false;
	}
	
	private void processMsg(){
		switch(this.msgType){
			case "STORED":
				this.processStored();
				break;
				
			case "GETCHUNK":
				this.processGetChunk();
				break;
				
			case "DELETE":
				this.processDelete();
				break;
				
			case "REMOVED":
				this.processRemoved();
				break;
				
			default:
				this.printErrMsg("Unkown message");
				break;
		}
	}
	
	private void processStored(){
		
		ConcurrentHashMap<String,Runnable> requests = this.server.getRequests();
		
		//Lookup file backup processes
		BackUpProtocol handler = (BackUpProtocol) requests.get("BACKUP"+this.fileId);

		if(handler != null){
			System.out.println("ControlProtocol: Notifying Backup\n");
			handler.stored(Integer.parseInt(this.senderId), Integer.parseInt(this.chunkNr));
		}
		
		//Lookup chunk backup processes
		handler = (BackUpProtocol) requests.get("BACKUP"+this.fileId+this.chunkNr);
		
		if(handler != null){
			System.out.println("ControlProtocol: Notifying Chunk Backup\n");
			handler.stored(Integer.parseInt(this.senderId), Integer.parseInt(this.chunkNr));
		}
		
		int chunkNr = Integer.parseInt(this.chunkNr);
		int senderId = Integer.parseInt(this.senderId);
		int repDeg = Integer.parseInt(this.repDeg);
		String chunkId = ServerChunk.toId(this.fileId,chunkNr);
		this.server.getFileManager().incChunkRepDeg(chunkId,repDeg,senderId);
		
	}
	
	private void processGetChunk(){
		this.sendChunk = true;
		System.out.println("Processing Get Chunk...");
		FileManager fileManager = this.server.getFileManager();
		
		String[] parts = this.fileId.split("\\.");
		String chunkId = parts[0]+"_"+this.chunkNr;
		
		if(!fileManager.containsChunk(chunkId)){
			this.printErrMsg("Chunk "+chunkId+" not found");
			return;
		}
		
		String fileName = chunkId+".chunk";
		FileInputStream inStream = fileManager.getInStream(fileName);

		//Read
		byte[] buf = new byte[Server.MAX_CHUNK_SIZE];
		int read;
		try{
			read = inStream.read(buf);
			inStream.close();
		}
		catch(IOException e){
			this.printErrMsg("Unable to read chunk "+this.chunkNr+" of file "+this.fileId);
			return;
		}

		byte[] cleanBuf = new byte[read];
		System.arraycopy(buf,0,cleanBuf,0,read);
		
		this.sleepRandom();

		if(!this.server.restoreThreads.containsKey("CHUNK"+this.fileId+"_"+this.chunkNr)){
			this.sendChunkMsg(cleanBuf);
		}
		this.server.restoreThreads.clear();
	}

	private int getRandomTime(){
		Random r = new Random();
   		int n = r.nextInt(400);
     	return n;
	}
	
	private void processDelete(){
		System.out.println("Processing Delete...");
		this.server.getFileManager().removeAllChunks(this.fileId);
		System.out.println("File deleted!");
	}

	private void processRemoved(){
		
		//System.out.println("Processing Removed...");
		if(this.server.getFileManager().decFileChunkRepDeg(this.fileId, Integer.parseInt(this.chunkNr), Integer.parseInt(this.senderId))){
			String handlerId = "REMOVED"+this.fileId+"_"+this.chunkNr;
			System.out.println("Starting removed treatment");
			this.server.removedThreads.put(handlerId, this);
			this.sleepRandom();
			System.out.println("Replication degree below required on chunk nr "+this.chunkNr+" of file "+this.fileId);
			//System.out.println("Starting chunk back up");
			if(!this.receivedPutChunk)
				this.server.backupChunk(this.fileId,Integer.parseInt(this.chunkNr));
			
			this.server.removedThreads.remove(handlerId);
		}
	}
	

	private void sendChunkMsg(byte[] buf){
		byte[] msg = this.getChunkMsg(buf);
		TwinMulticastSocket socket = this.server.getMDRsocket();
		DatagramPacket packet = new DatagramPacket(msg, msg.length, socket.getGroup(), socket.getPort());
		
		//Send msg
		try{
			socket.send(packet);
		}
		catch(IOException e){
			this.printErrMsg("Unable to send CHUNK message");
		}

		this.server.restoreThreads.put("CHUNK"+this.fileId+"_"+this.chunkNr, this);
	}
	
	private String getChunkHeader(){
		return "CHUNK "+this.version+" "+this.server.getId()+" "+this.fileId+" "+this.chunkNr;
	}
	
	private byte[] getChunkMsg(byte[] body){
		byte[] header = (this.getChunkHeader()+"\r\n").getBytes();
		byte[] msg = new byte[header.length+body.length];
		System.arraycopy(header,0,msg,0,header.length);
		System.arraycopy(body,0,msg,header.length,body.length);
		return msg;
	}
	
	private void printErrMsg(String err){
		System.err.println("Error in Control Protocol: "+err);
	}
	
	private void sleepRandom(){
		int delay = this.getRandomTime();
		System.out.println("ControlProtocol: delay "+delay);
		try{
			TimeUnit.MILLISECONDS.sleep(delay);
		}
		catch(InterruptedException e){
			System.out.println(e);
		}
	}
	
	public void notifyPutChunk(String fileId, String chunkNr){
		if(this.fileId.compareTo(fileId)==0 && this.chunkNr.compareTo(chunkNr) == 0);
			this.receivedPutChunk = true;
	}
}