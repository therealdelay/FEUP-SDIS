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
	private String version;
	private String senderId;
	private String fileId;
	private String chunkNr;
	
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
				
		ConcurrentHashMap<String,Runnable> requests = this.server.getRequests();
		RestoreProtocol handler = (RestoreProtocol) requests.get("RESTORE"+this.fileId);
		
		if(handler != null){
			System.out.println("ControlProtocol: Notifying Restore");
			/*
			System.out.println("ChunkBody: ");
			System.out.println(new String(this.chunkBody));
			*/
			handler.chunk(Integer.parseInt(this.chunkNr.trim()), this.chunkBody);
		}
	}
	
	
	private boolean parseRequest(){
		//System.out.println("Buf size: "+this.buf.length);
		//System.out.println("Actual buf size: "+this.trueBufLength);
		String msg = new String(this.buf);
		//System.out.println(msg);
		String[] parts = msg.split("\r\n");
		
		//Parse header elements
		String[] header = parts[0].split(" ");
		this.version = header[1];
		this.senderId = header[2];
		this.fileId = header[3];
		this.chunkNr = header[4];
		
		//Copy actual body
		int headerLength = parts[0].length()+2;
		int bodyLength = this.trueBufLength - headerLength;
		//System.out.println("Actual body copied size: "+ bodyLength);
		this.chunkBody = new byte[bodyLength];
		System.arraycopy(this.buf, headerLength, this.chunkBody, 0, bodyLength);
		
		if(this.senderId.compareTo(""+this.server.getId()) != 0){
			//System.out.println("Packet received at MDBsocket: " + Arrays.toString(header)+" with size "+this.chunkBody.length);
			return false;
		}
		else
			return true;
	}
	
	private void printErrMsg(String err){
		System.err.println("Error storing chunk "+this.chunkNr+" of file "+this.fileId+": "+err);
	}
}