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

public class InitProtocol implements Runnable {
	
	private final static int[] TIMEOUT_VALS = {1,2,4,8,16};
	private final static int MAX_TRIES = 5;
	
	private Server server;
	private String metaData = "";
	private String currData;
	private int currBlock;
	private boolean newData = false;

	public InitProtocol(Server server) throws IOException, UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException
	{
		this.server = server;
	}
	
	@Override
	public void run (){
		getMetaData();
	}
	
	private void getMetaData(){

		String block;
		this.currBlock = 0;
		while(getMetaBlock()){
			this.newData = false;
			this.currBlock++;
		}

		System.out.println("InitThread: Finished retreiving metaData");
		this.server.ready = true;

		this.initFileManager();
	}
	
	private boolean getMetaBlock(){
		
		for(int i = 0; i < InitProtocol.MAX_TRIES; i++){
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
				Thread.sleep(InitProtocol.TIMEOUT_VALS[i]*1000);
			}
			catch(InterruptedException e){
				this.printErrMsg("Interrupted sleep");
			}
			
			if(this.newData){
				this.metaData += this.currData;

				if(this.currData.compareTo("") == 0)
					return false;
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
		for(int i=0;i<peersStr.length;i++)
			peers.add(Integer.parseInt(peersStr[i]));

		int replicationDeg = Integer.parseInt(attrs[4]);

		ServerChunk chunk = new ServerChunk(attrs[1],attrs[2],peers,replicationDeg);
		this.server.getFileManager().addChunk(chunk);
	}

	private void initFileManager(){
		String[] elements = this.metaData.split("\\|");

		System.out.println("InitProtocol: elements"+elements.length+"\n"+Arrays.toString(elements));

		String[] attrs;
		for(int i = 0; i < elements.length; i++){
			attrs = elements[i].split(" ");

			System.out.println("InitProtocol: Attrs - "+Arrays.toString(attrs));
			if(attrs[0].compareTo("FILE") == 0)
				this.parseFile(attrs);

			if(attrs[0].compareTo("CHUNK") == 0)
				this.parseChunk(attrs);
		}
	}
	
	private void printErrMsg(String err){
		//System.err.printlncurrData("Error backing up file "+this.fileName+": "+err);
	}
	
	public void meta(int blockNr, String data){

		System.out.println("InitProtocol: data "+data+" "+data.length());
		if(blockNr == this.currBlock){
			if(!this.newData){
				this.newData = true;
				this.currData = data;
			}
		}
	}
}