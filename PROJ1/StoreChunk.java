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
	private String fileId;
	private String chunkNr;
	
	public StoreChunk(Server server, byte[] buf, int length){
		this.server = server;
		this.buf = buf;
		this.trueBufLength = length;
	}
	
	@Override
	public void run (){
		/*System.out.println("Packet received at MDBsocket: " + new String(buf).trim());*/
		
		if(this.parseRequest())
			return;
		
		CopyOnWriteArrayList<String> files = this.server.getFiles();
	
		String[] parts = this.fileId.split("\\.");
		String chunkFileName = parts[0]+"_"+this.chunkNr+".chunk";
		
		if(!files.contains(chunkFileName))
			this.saveChunk(chunkFileName);
		else
			this.printErrMsg("Already saved");
		
		this.sendStoredMsg();
	}
	
	
	private boolean parseRequest(){
		//System.out.println("Buf size: "+this.buf.length);
		//System.out.println("Actual buf size: "+this.trueBufLength);
		String msg = new String(this.buf);
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
	
	private void saveChunk(String chunkFileName){
		CopyOnWriteArrayList<String> files = this.server.getFiles();
		String filePathName = this.server.getSWD().toString()+"/"+ chunkFileName;
		
		try{
			File outFile = new File(filePathName);
			outFile.createNewFile();
			FileOutputStream outStream = new FileOutputStream(outFile);
			outStream.write(this.chunkBody,0,this.chunkBody.length);
			outStream.close();
		}
		catch(IOException e){
			this.printErrMsg("Unable to save chunk");
		}
		
		files.add(chunkFileName);
		System.out.println(files.toString());
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
	
	private void printErrMsg(String err){
		System.err.println("Error storing chunk "+this.chunkNr+" of file "+this.fileId+": "+err);
	}
	
	private String getStoredMsg(){
		return "STORED "+this.version+" "+this.server.getId()+" "+this.fileId+" "+this.chunkNr;
	}
}