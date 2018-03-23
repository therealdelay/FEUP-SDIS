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
		String[] header = parts[0].split(" ");
		this.msgType = header[0];
		this.version = header[1];
		this.senderId = header[2];
		this.fileId = header[3];
		this.chunkNr = header[4];
			
		if(this.senderId.compareTo(""+this.server.getId()) == 0){
			//System.out.println("Packet received at MCsocket: " + Arrays.toString(header));
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
				
			default:
				this.printErrMsg("Unkown message");
				break;
		}
	}
	
	private void processStored(){
		ConcurrentHashMap<String,Runnable> requests = this.server.getRequests();
		BackUpProtocol handler = (BackUpProtocol) requests.get("BACKUP"+this.fileId);
		
		if(handler != null){
			//handler.test();
			System.out.println("ControlProtocol: Notifying Backup");
			handler.stored(Integer.parseInt(this.senderId), Integer.parseInt(this.chunkNr.trim()));
		}
		
		/*this.printErrMsg("Null handler");*/
	}
	
	private void processGetChunk(){
		System.out.println("Processing Get Chunk...");
	}
	
	private void processDelete(){
		System.out.println("Processing Delete...");
	}
	
	private void printErrMsg(String err){
		System.err.println("Error in Control Protocol: "+err);
	}
	
	private String getStoredMsg(){
		return "STORED "+this.version+" "+this.server.getId()+" "+this.fileId+" "+this.chunkNr;
	}
}