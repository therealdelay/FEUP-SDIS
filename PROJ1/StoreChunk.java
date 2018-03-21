import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class StoreChunk implements Runnable {
	
	private byte[] buf;
	private String chunkBody;
	private Server server;
	private String version;
	private String senderId;
	private String fileId;
	private String chunkNr;
	
	public StoreChunk(Server server, byte[] buf){
		this.server = server;
		this.buf = buf;
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
		System.out.println("Buf size: "+this.buf.length);
		String msg = new String(this.buf);
		String[] parts = msg.split("\r\n");
		System.out.println("Parts nr: "+parts.length);
		System.out.println("Header size: "+parts[0].length());
		System.out.println("Chunk size: "+parts[1].length());
		//System.out.println("Parts: " +  Arrays.toString(parts));
		String[] header = parts[0].split(" ");
		//System.out.println("Header: " + Arrays.toString(header));
		this.version = header[1];
		this.senderId = header[2];
		this.fileId = header[3];
		this.chunkNr = header[4];
		int trueLength = parts.length-1;
		for(int i = 1; i < trueLength; i++){
			if(i == trueLength-1)
				this.chunkBody += parts[i];
			else
				this.chunkBody += parts[i]+"\r\n";
			/*
			System.out.println("Part size i= "+i+" : "+parts[i].length());
			this.chunkBody += parts[i];
			*/
		}
		
		System.out.println("Chunk body size: "+this.chunkBody.length());
		if(this.senderId.compareTo(""+this.server.getId()) != 0){
			System.out.println("Packet received at MDBsocket: " + Arrays.toString(header)+" with size "+this.chunkBody.length());
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
			outStream.write(this.chunkBody.getBytes(),0,this.chunkBody.length());
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