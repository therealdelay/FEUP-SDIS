import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import java.security.InvalidKeyException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public class ControlProtocol implements Runnable {
	
	private byte[] buf;
	private int trueBufLength;
	private byte[] chunkBody;
	private Server server;
	private String msgType;
	private String version;
	private String senderId;
	private String blockNr;
	private String fileId;
	private String fileEncryptedId;
	private String chunkNr;
	private String repDeg;
	private boolean sendChunk;
	private int portToSend;
	private String addressToSend;



	private boolean receivedPutChunk = false;
	private boolean receivedChunk = false;
	
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

		if(header.length > 4){
			this.fileEncryptedId = header[3];
			this.fileId = header[4];

			if(header.length > 5){
				this.chunkNr = header[5];
				if(header.length > 6)
					this.repDeg = header[6];
			}
  			if (header.length > 7) {
				
				this.addressToSend = header[6];
				this.portToSend = Integer.parseInt(header[7]);
			}
		}
		else
			this.blockNr = header[3].trim();

		System.out.println("ControlProtocol: blockNr "+ this.blockNr);
		
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

			case "GETMETA":
				this.processGetMeta();
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
		this.server.getFileManager().incChunkRepDeg(chunkId,fileEncryptedId, repDeg,senderId);
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
		System.out.println("filename " + fileName);
		FileInputStream inStream = fileManager.getInStream(fileName);
		
		//Read
		byte[] buf = new byte[Server.MAX_CHUNK_SIZE_ENCRYPTED];
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

		System.out.println("ProcessGetChunk: " + cleanBuf.length  + " : Read: " + read);
		
		String handlerId = "GETCHUNK"+this.fileId+"_"+this.chunkNr;
		this.server.restoreThreads.put(handlerId, this);

		this.sleepRandom();

		if(!this.receivedChunk){
			this.sendChunkMsg(null);
		}
		else {
			System.out.println("Another peer is already handling this task, exiting");
			return;
		}
		System.out.println("Attempting to send with " + Arrays.toString(parts));

		try {
			InetAddress address = InetAddress.getByName(addressToSend);
			
			this.sendThroughTCP(cleanBuf, address);
		} catch (UnknownHostException e) {
			System.out.println("Error on getting address from GETCHUNK");
		}
		
		this.server.restoreThreads.clear();
	}

	private void sendThroughTCP(byte[] buf, InetAddress address) {
		try {
			Socket connectionSocket = new Socket(address, portToSend);
			
			DataOutputStream outputStream = new DataOutputStream(connectionSocket.getOutputStream());
					
			outputStream.write(buf);

			connectionSocket.close();
		}
		catch(IOException e){
			this.printErrMsg("Error on sending CHUNK Message.");
			return;
		}
		
		this.server.restoreThreads.put("CHUNK"+this.fileId+"_"+this.chunkNr, this);
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
		catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
			System.err.println("MCsocket packet received is insecure");
		}

		this.server.restoreThreads.put("CHUNK"+this.fileId+"_"+this.chunkNr, this);
	}
	
	
	private String getChunkHeader(){
		return "CHUNK "+this.version+" "+this.server.getId()+" "+this.fileId+" "+this.chunkNr;
	}

	
	private void processDelete(){
		System.out.println("Processing Delete...");
		this.server.getFileManager().removeAllChunks(this.fileId, this.fileEncryptedId); //fileEncrypted is the secretKey
		System.out.println("File deleted!");
	}

	private void processRemoved(){
		
		//System.out.println("Processing Removed...");
		if(this.server.getFileManager().decFileChunkRepDeg(this.fileId, Integer.parseInt(this.chunkNr), Integer.parseInt(this.senderId))){
			String handlerId = "REMOVED"+this.fileId+"_"+this.chunkNr;
			this.server.removedThreads.put(handlerId, this);
			this.sleepRandom();
			System.out.println("Replication degree below required on chunk nr "+this.chunkNr+" of file "+this.fileId);
			//System.out.println("Starting chunk back up");
			if(!this.receivedPutChunk){
				System.out.println("Starting removed chunk backup");
				this.server.backupChunk(this.fileId,Integer.parseInt(this.chunkNr));
			}
			
			this.server.removedThreads.remove(handlerId);
		}
	}

	private void processGetMeta(){
		System.out.println("TEST");
		int blockNr = Integer.parseInt(this.blockNr);
		String meta = this.server.getFileManager().getMetaBlock(blockNr);
		System.out.println("ControlProtocol: Block "+meta);
		this.sendMetaMsg(meta);
	}

	private String getMetaHeader(){
		return "META "+this.version+" "+this.server.getId()+" "+this.blockNr;
	}
	
	private byte[] getMetaMsg(String data){
		String msg = this.getMetaHeader()+"\r\n"+data;
		return msg.getBytes();
	}

	private void sendMetaMsg(String data){
		byte[] msg = this.getMetaMsg(data);
		TwinMulticastSocket socket = this.server.getMDRsocket();
		DatagramPacket packet = new DatagramPacket(msg, msg.length, socket.getGroup(), socket.getPort());
		
		//Send msg
		try{
			socket.send(packet);
		}
		catch(IOException e){
			this.printErrMsg("Unable to send META message");
		}
		catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
			System.err.println("MCsocket packet received is insecure");
		}
	}
		
	private byte[] getChunkMsg(byte[] body){
		byte[] header = (this.getChunkHeader()+"\r\n").getBytes();
		byte[] msg;
		if (body != null)
			msg = new byte[header.length+body.length];
		else 
			msg = new byte[header.length];
		
		System.arraycopy(header,0,msg,0,header.length);
		if (body != null)
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

	private int getRandomTime(){
		Random r = new Random();
   		int n = r.nextInt(400);
     	return n;
	}
	
	public void notifyPutChunk(String fileId, String chunkNr){
		if(this.fileId.compareTo(fileId)==0 && this.chunkNr.compareTo(chunkNr) == 0);
			this.receivedPutChunk = true;
	}

	public void notifyGetChunk(String fileId, String chunkNr) {
		if(this.fileId.compareTo(fileId)==0 && this.chunkNr.compareTo(chunkNr) == 0); {
			this.receivedChunk = true;
		}
	}
}
