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
		if(header.length > 4)
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
		FileManager fileManager = this.server.getFileManager();
		
		String[] parts = this.fileId.split("\\.");
		String fileName = parts[0]+"_"+this.chunkNr.trim()+".chunk";
		
		if(!fileManager.contains(fileName)){
			this.printErrMsg("File "+fileName+" not found");
			return;
		}
		
		
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
		System.arraycopy(cleanBuf,0,buf,0,read);
		
		this.sendChunkMsg(cleanBuf);
	}
	
	private void processDelete(){
		System.out.println("Processing Delete...");
		String id = this.fileId.split("\\.")[0];
		FileManager fileManager = this.server.getFileManager();
		fileManager.removeAllChunks(id);
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
	}
	
	private String getChunkHeader(){
		return "CHUNK "+this.version+" "+this.server.getId()+" "+this.fileId+" "+this.chunkNr;
	}
	
	private byte[] getChunkMsg(byte[] body){
		String msg = this.getChunkHeader() + "\r\n" + new String(body);
		return msg.getBytes();
	}
	
	private void printErrMsg(String err){
		System.err.println("Error in Control Protocol: "+err);
	}
}