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


		long randomTimeout = (long) (Math.random() * 400);
		System.out.println("\n\nRandom timeout is random " + randomTimeout + "\n\n");
		
		try {
			Thread.sleep(randomTimeout);
		} catch (InterruptedException e) {
			System.out.println(e.toString());
		}

		if (fileManager.getPerceivedRepDeg(chunkId) >= Integer.parseInt(repDeg)) {
			System.out.println("\nFile was already backed up with enough replication degree in other peers.\n");
			return;
		}
		
		this.sendStoredMsg();
		
		if(!fileManager.containsChunk(chunkId))
			this.saveChunk(chunkId);
		else
			this.printErrMsg("Already saved");
		
	}

	private boolean parseRequest(){
		String msg = new String(this.buf);
		String[] parts = msg.split("\r\n");
		
		//Parse header elements
		String[] header = parts[0].split(" ");
		System.out.println(Arrays.toString(header));
		this.version = header[1];
		this.senderId = header[2];
		this.fileId = header[3];
		
		int i = 4;
		String pathName = header[i++];
		for(;i<header.length-3;i++)
			pathName += " "+header[i];
				
		String creationDate = header[i++];
		System.out.println(creationDate);
		this.chunkNr = header[i++];
		this.repDeg = header[i].trim();
		
		this.file = new ServerFile(this.fileId,pathName,Long.parseLong(creationDate),Integer.parseInt(this.repDeg));
		
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
		
		//Send response
		try{
			socket.send(packet);
		}
		catch(IOException e){
			this.printErrMsg("Unable to send STORED message");
		}
	}
	
	private void printErrMsg(String err){
		System.err.println("Error storing chunk "+this.chunkNr+" of file "+this.fileId+": "+err);
	}
	
	private String getStoredMsg(){
		return "STORED "+this.version+" "+this.server.getId()+" "+this.fileId+" "+this.chunkNr;
	}
}