
import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class Chunk implements Runnable {
	
	private byte[] buf;
	private int trueBufLength;
	private byte[] chunkBody;
	private Server server;
	private String msgType;
	private String version;
	private String senderId;
	private String fileId;
	private String chunkNr;
	private String blockNr;
	private String metaData;
	
	public Chunk(Server server, byte[] buf, int length){
		this.server = server;
		this.buf = buf;
		this.trueBufLength = length;
	}
	
	@Override
	public void run (){
		/*System.out.println("Packet received at MDBsocket: " + new String(buf).trim());*/
		
		if(this.parseRequest())
			return;

		this.processMsg();
	}

	private void processMsg(){
		switch(this.msgType){
			case "CHUNK":
				this.processChunk();
				break;
				
			case "META":
				this.processMeta();
				break;
				
			default:
				this.printErrMsg("Unkown message");
				break;
		}
	}	
	
	private boolean parseRequest(){
		String msg = new String(this.buf);

		String[] parts = msg.split("\r\n");
		
		//Parse header elements
		String[] header = parts[0].split(" ");
		this.msgType = header[0];
		this.version = header[1];
		this.senderId = header[2];


		if(header.length > 4){
			this.fileId = header[3];
			this.chunkNr = header[4];

			//Copy actual body
			int headerLength = parts[0].length()+2;
			int bodyLength = this.trueBufLength - headerLength;
			//System.out.println("Actual body copied size: "+ bodyLength);
			this.chunkBody = new byte[bodyLength];
			System.arraycopy(this.buf, headerLength, this.chunkBody, 0, bodyLength);
		}
		else{

			this.blockNr = header[3];

			if(parts.length == 1)
				this.metaData = "";
			else
				this.metaData = parts[1];
			System.out.println(this.metaData);
		}
		
		if(this.senderId.compareTo(""+this.server.getId()) != 0){
			System.out.println("Packet received at MDBsocket: " + Arrays.toString(header));
			return false;
		}
		else
			return true;
	}


	private void processChunk(){
		ConcurrentHashMap<String,Runnable> restoreThreads = this.server.getRestoreThreads(); 
		ControlProtocol handlerRestore = (ControlProtocol) restoreThreads.get("GETCHUNK"+this.fileId+"_"+this.chunkNr); 
	 
		if(handlerRestore != null){ 
		  System.out.println("ControlProtocol: Notifying Restore GetChunk\n"); 
		  handlerRestore.notifyGetChunk(this.fileId, this.chunkNr); 
		} 
	}


	private void processMeta(){
		this.server.initThread.meta(Integer.parseInt(this.blockNr),this.metaData);
	}
	
	private void printErrMsg(String err){
		System.err.println("Error storing chunk "+this.chunkNr+" of file "+this.fileId+": "+err);
	}
}
