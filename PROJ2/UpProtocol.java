import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;

import java.security.InvalidKeyException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class UpProtocol implements Runnable {
	
	private final static int[] TIMEOUT_VALS = {1,2,4,8,16};
	private final static int MAX_TRIES = 5;
	
	private Server server;
	private boolean receivedMetaData = false;   //true if all of the metadata was received successfully
	private String metaData = "";				//contains the metadata
	private String currData;
	private int currBlock;
	private boolean newData = false;

	private ServerChunk currChunk;

	public UpProtocol(Server server)
	{
		this.server = server;
	}
	
	@Override
	public void run (){

		this.server.printMsg("Synchronizing...");
		getMetaData();

		this.server.ready = true;

		if(receivedMetaData){
			this.server.printMsg("Reading SWD");
			this.initFileManager();
		}
		else{
			this.server.printMsg("Cleaning SWD");
			this.server.getFileManager().cleanSWD();
		}

		this.server.printMsg("Ready");
	}
	
	private void getMetaData(){

		String block;
		this.currBlock = 0;
		while(getMetaBlock()){
			this.newData = false;
			this.currBlock++;
		}
	}
	
	private boolean getMetaBlock(){
		
		for(int i = 0; i < UpProtocol.MAX_TRIES; i++){
			byte[] msg = this.getGetMetaMsg().getBytes();
			TwinMulticastSocket socket = this.server.getMCsocket();
			DatagramPacket packet = new DatagramPacket(msg, msg.length, socket.getGroup(), socket.getPort());
			
			try{
				socket.send(packet);
			}
			catch(IOException e){
				this.printErrMsg("Unable to send GETMETA message");
			}
			catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
				System.err.println("Error sending packet through MDBSocket: " + e);
			}
			
			try{
				Thread.sleep(UpProtocol.TIMEOUT_VALS[i]*1000);
			}
			catch(InterruptedException e){
				this.printErrMsg("Interrupted sleep");
			}
			
			if(this.newData){
				this.metaData += this.currData;

				if(this.currData.compareTo("") == 0){
					receivedMetaData = true;
					return false;
				}
				else
					return true;
			}
		}
		
		return false;
	}
	
	
	private String getGetMetaMsg(){
		return "GETMETA "+this.server.getVersion()+" "+this.server.getId()+" "+this.currBlock;
	}

	private void parseFile(String[] attrs){
		long lastModified = Long.parseLong(attrs[4]);
		int replicationDeg = Integer.parseInt(attrs[5]);
		int initPeerId = Integer.parseInt(attrs[6]);
		ServerFile file = new ServerFile(attrs[2],attrs[1],attrs[3],lastModified,replicationDeg,initPeerId);
		this.server.getFileManager().addFile(file);
	}

	private void parseChunk(String[] attrs){
		ArrayList<Integer> peers = new ArrayList<Integer>();

		String[] peersStr = attrs[3].split(",");
		for(int i=0;i<peersStr.length;i++){
			if(peersStr[i].compareTo("") != 0)
				peers.add(Integer.parseInt(peersStr[i]));
		}

		int replicationDeg = Integer.parseInt(attrs[4]);

		ServerChunk chunk = new ServerChunk(attrs[1],attrs[2],peers,replicationDeg);
		this.server.getFileManager().addChunk(chunk);
	}

	private void initFileManager(){
		String[] elements = this.metaData.split("\\|");

		//System.out.println("UpProtocol: elements "+elements.length);//+"\n"+Arrays.toString(elements));

		String[] attrs;
		for(int i = 0; i < elements.length; i++){
			attrs = elements[i].split(" ");

			//System.out.println("UpProtocol: Attrs - "+Arrays.toString(attrs));
			if(attrs[0].compareTo("FILE") == 0)
				this.parseFile(attrs);

			if(attrs[0].compareTo("CHUNK") == 0)
				this.parseChunk(attrs);
		}

		ArrayList<ServerChunk> chunksOnDisk = this.server.getFileManager().readSWD();
		for(ServerChunk chunk : chunksOnDisk){
			this.currChunk = chunk;
			this.sendStoreMsg();
		}
	}

	private void sendStoreMsg(){
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
		catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
			System.err.println("MCsocket packet received is insecure");
		}
	}

	private String getStoredMsg(){
		return "STORED "+this.server.getVersion()+" "+this.server.getId()+" "+this.currChunk.toMsg()+" "+this.currChunk.getReplicationDeg();
	}

	private void printErrMsg(String err){
		System.err.println("UpProtocol: "+err);
	}	
	
	public void meta(int blockNr, String data){

		//System.out.println("UpProtocol: data "+data+" "+data.length());
		if(blockNr == this.currBlock){
			if(!this.newData){
				this.newData = true;
				this.currData = data;
			}
		}
	}
}