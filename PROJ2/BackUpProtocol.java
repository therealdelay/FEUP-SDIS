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

public class BackUpProtocol implements Runnable {
	
	private final static int[] TIMEOUT_VALS = {1,2,4,8,16};
	private final static int MAX_TRIES = 5;
	
	private Server server;
	private ServerFile serverFile;
	private String fileName;
	private String fileId;
	private String chunkId;
	private int chunkNr;
	private int replicationDeg;

	private SecretKeySpec secretKey;
	private Cipher cipher;
	
	private int currChunk = 0;
	
	private FileInputStream inStream = null;
	
	public BackUpProtocol(Server server, String fileName, int replicationDeg, SecretKeySpec clientKey) throws IOException, UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException
	{
		this.server = server;
		this.fileName = fileName;
		this.chunkNr = -1;
		this.replicationDeg = replicationDeg;

		this.secretKey = clientKey;
		this.cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
	}
	
	public BackUpProtocol(Server server, String fileId, int chunkNr, int replicationDeg){
		this.server = server;
		this.fileId = fileId;
		this.chunkNr = chunkNr;
		this.currChunk = chunkNr;
	}
	
	@Override
	public void run (){
		
		if(this.chunkNr == -1){
			if(!this.addFile()){
				return;
			}
			this.backUpFile();
		}
		else{
			this.readFile();
			this.backUpFileChunk();
		}
	}
	
	private boolean addFile(){
		File file = new File(this.fileName);
		if(!file.exists()){
			this.exit_err("File not found");
			return false;
		}
		
		this.serverFile = new ServerFile(this.fileName, this.replicationDeg, file.lastModified(), this.secretKey, this.server.getId());
		
		//Get file id
		this.fileId = serverFile.getId();
		
		FileManager fileManager = this.server.getFileManager();
		fileManager.addFile(serverFile);
		
		
		return true;
	}
	
	private void readFile(){
		FileManager fileManager = this.server.getFileManager();
		this.serverFile = fileManager.getFile(this.fileId);
		this.chunkId = ServerChunk.toId(this.fileId,this.chunkNr);
		System.out.println("ControlProtocol: ChunkID "+this.chunkId);
		this.fileName = this.chunkId+".chunk";
		this.replicationDeg = fileManager.getFileRepDeg(this.fileId);
	}
	
	
	private void backUpFile(){
		
		this.inStream = this.server.getFileManager().getFileInStream(this.fileName);
		if(this.inStream == null){
			this.exit_err("Unable to open src file");
			return;
		}
		
		int read;
		byte[] buf = new byte[Server.MAX_CHUNK_SIZE];
		
		try{
			while((read = this.inStream.read(buf)) >= 0){
				
				this.chunkId = ServerChunk.toId(this.fileId,this.currChunk);
				
				byte[] body = new byte[read];
				System.arraycopy(buf,0,body,0,read);

				byte[] encryptedBody = encryptBody(body);

				if(!this.backUpChunk(encryptedBody)){
					this.exit_err("Unable to reach required replication degree in chunk "+this.currChunk);
					return;
				}
				
				this.currChunk++;
			}
		}
		catch(IOException e){
			this.exit_err("Unable to read src file in chunk "+this.currChunk);
			return;
		}
		catch(InvalidKeyException | BadPaddingException | IllegalBlockSizeException e){
			this.exit_err("Error encrypting chunk "+this.currChunk);
			return;
		}
		
		this.exit();
	}

	public byte[] encryptBody(byte[] body) throws IOException,InvalidKeyException,BadPaddingException,IllegalBlockSizeException
	{
		this.cipher.init(Cipher.ENCRYPT_MODE, this.secretKey);
		return this.cipher.doFinal(body);
	}

	private void backUpFileChunk(){
		
		this.inStream = this.server.getFileManager().getInStream(this.fileName);
		if(this.inStream == null){
			this.exit_err("Unable to open src file");
			return;
		}
		
		int read;
		byte[] buf = new byte[Server.MAX_CHUNK_SIZE];
		
		try{
			read = this.inStream.read(buf);
			byte[] body = new byte[read];
			System.arraycopy(buf,0,body,0,read);
			if(!this.backUpChunk(body)){
				this.exit_err("Unable to reach required replication degree in chunk "+this.chunkNr);
				return;
			}
		}
		catch(IOException e){
			this.exit_err("Unable to read src file in chunk "+this.chunkNr);
			return;
		}
		
		this.exit();
	}
	
	
	private String getPutChunkHeader(){
		return "PUTCHUNK "+this.server.getVersion()+" "+this.server.getId()+" "+this.serverFile.toMsg()+" "+this.currChunk+" "+this.replicationDeg;
	}
	
	private byte[] getPutChunkMsg(byte[] body){
		byte[] header = (this.getPutChunkHeader()+"\r\n").getBytes();
		byte[] msg = new byte[header.length+body.length];
		System.arraycopy(header,0,msg,0,header.length);
		System.arraycopy(body,0,msg,header.length,body.length);
		return msg;		
	}	
	
	private boolean backUpChunk(byte buf[]){
		
		for(int i = 0; i < BackUpProtocol.MAX_TRIES; i++){
			System.out.println(this.getPutChunkHeader());
			byte[] msg = this.getPutChunkMsg(buf);
			TwinMulticastSocket socket = this.server.getMDBsocket();
			DatagramPacket packet = new DatagramPacket(msg, msg.length, socket.getGroup(), socket.getPort());
			
			try{
				socket.send(packet);
			}
			catch(IOException e){
				this.printErrMsg("Unable to send PUTCHUNK message");
			}
			catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
				System.err.println("Error sending packet through MDBSocket: " + e);
			}
			
			try{
				Thread.sleep(BackUpProtocol.TIMEOUT_VALS[i]*1000);
			}
			catch(InterruptedException e){
				this.printErrMsg("Interrupted sleep");
			}
			
			
			int perceivedRepDeg = this.server.getFileManager().getPerceivedRepDeg(this.chunkId);
			System.out.println("ControlProtocol: ChunkId "+this.chunkId+" PerceivedRepDeg "+perceivedRepDeg);
			if(perceivedRepDeg >= this.replicationDeg)
				return true;
		}
		
		return false;
	}
	
	private void removeRequest(){
		ConcurrentHashMap<String,Runnable> requests = this.server.getRequests();
		if(this.chunkNr != -1)
			requests.remove("BACKUP"+this.fileId+this.chunkNr);
		else
			requests.remove("BACKUP"+this.fileId);
	}
	
	private void exit(){
		
		if(this.inStream != null){
			try{
				this.inStream.close();
			}
			catch(IOException e){
				this.printErrMsg("Unable to close input stream");
			}
		}
		
		this.removeRequest();
		
		if(this.chunkNr != -1)
			System.out.println("Chunk "+this.chunkNr+" of file "+this.fileName+" backed up with success!");
		else
			System.out.println("File "+this.fileName+" backed up with success!");
	}
	
	private void exit_err(String err){
		this.printErrMsg(err);
		try{
			if(this.inStream != null)
				this.inStream.close();
		}
		catch(IOException e){
			this.printErrMsg("Unable to close input stream");
		}
		
		this.removeRequest();
	}
	
	private void printErrMsg(String err){
		System.err.println("Error backing up file "+this.fileName+": "+err);
	}
	
	public void stored(int id, int chunk){
		System.out.println("Id: "+ id + " " + "Chunk: " + chunk + "\n");
		this.server.getFileManager().incFileChunkRepDeg(this.fileId,chunk,id);
	}
}