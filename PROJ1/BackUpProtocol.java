import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class BackUpProtocol implements Runnable {
	
	private final static int[] TIMEOUT_VALS = {1,2,4,8,16};
	private final static int MAX_TRIES = 5;
	
	private Server server;
	private String fileName;
	private String fileId;
	private int chunkNr;
	private int replicationDeg;
	private ReentrantLock lock;
	
	//private int currTry = 0;
	private int currChunk = -1;
	private ArrayList<ArrayList<Integer>> chunkPeers;
	
	private FileInputStream inStream = null;
	
	public BackUpProtocol(Server server, String fileName, int replicationDeg){
		this.server = server;
		this.fileName = fileName;
		this.chunkNr = -1;
		this.replicationDeg = replicationDeg;
		this.lock = new ReentrantLock();
		this.chunkPeers = new ArrayList<ArrayList<Integer>>();
	}
	
	public BackUpProtocol(Server server, String fileId, int chunkNr, int replicationDeg){
		this.server = server;
		this.fileId = fileId;
		this.chunkNr = chunkNr;
		this.lock = new ReentrantLock();
		this.chunkPeers = new ArrayList<ArrayList<Integer>>();
	}
	
	@Override
	public void run (){
		if(this.chunkNr == -1)
			this.addFile();
		else
			this.readFile();
		
		this.inStream = this.server.getFileManager().getFileInStream(this.fileName);
		if(this.inStream == null){
			this.exit_err("Unable to open src file");
			return;
		}
		
		int read;
		byte[] buf = new byte[Server.MAX_CHUNK_SIZE];
		
		try{
			while((read = this.inStream.read(buf)) >= 0){
				System.out.println("Bytes read: "+read);
				this.currChunk++;
				
				if(this.chunkNr != -1){
					if(this.currChunk != this.chunkNr)
						continue;
				}					
				
				byte[] body = new byte[read];
				System.arraycopy(buf,0,body,0,read);
				//System.out.println("Bytes body: "+body.length);
				if(!this.backUpChunk(body)){
					this.exit_err("Unable to reach required replication degree in chunk "+this.currChunk);
					return;
				}
				
				if(this.isChunkBackUp())
					break;
			}
		}
		catch(IOException e){
			this.exit_err("Unable to read src file in chunk "+this.currChunk);
			return;
		}
		
		this.exit();
		
	}
	
	private void addFile(){
		ServerFile serverFile = new ServerFile(this.fileName, this.replicationDeg);
		this.fileId = serverFile.getId(); 	//Get file id
		
		FileManager fileManager = this.server.getFileManager();
		fileManager.addFile(serverFile);
	}
	
	private void readFile(){
		FileManager fileManager = this.server.getFileManager();
		this.fileName = fileManager.getFilePath(this.fileId);
		this.replicationDeg = fileManager.getFileRepDeg(this.fileId);
	}
		
	private boolean isChunkBackUp(){
		return this.chunkNr != -1 && this.currChunk == this.chunkNr;
	}
	
	private ArrayList<Integer> getCurrChunkPeers(){
		if(this.isChunkBackUp()){
			System.out.println("BACKING UP A CHUNK");
			return this.chunkPeers.get(0);
		}
		else{
			System.out.println("BACKING UP A FILE");
			return this.chunkPeers.get(this.currChunk);
		}
	}
	
	private String getPutChunkHeader(){
		return "PUTCHUNK "+this.server.getVersion()+" "+this.server.getId()+" "+this.fileId+" "+this.currChunk+" "+this.replicationDeg;
	}
	
	private byte[] getPutChunkMsg(byte[] body){
		byte[] header = (this.getPutChunkHeader()+"\r\n").getBytes();
		byte[] msg = new byte[header.length+body.length];
		System.arraycopy(header,0,msg,0,header.length);
		System.arraycopy(body,0,msg,header.length,body.length);
		return msg;
	}	
	
	private boolean backUpChunk(byte buf[]){
		try{
			this.lock.lock();
			//System.out.println("HERE");
			this.chunkPeers.add(new ArrayList<Integer>());
		}
		finally{
			this.lock.unlock();
		}
		
		boolean done = false;
		
		for(int i = 0; i < BackUpProtocol.MAX_TRIES; i++){
			System.out.println(this.getPutChunkHeader());
			byte[] msg = this.getPutChunkMsg(buf);
			System.out.println("Msg size: "+msg.length);
			TwinMulticastSocket socket = this.server.getMDBsocket();
			DatagramPacket packet = new DatagramPacket(msg, msg.length, socket.getGroup(), socket.getPort());
			try{
				socket.send(packet);
			}
			catch(IOException e){
				this.printErrMsg("Unable to send PUTCHUNK message");
			}
			
			try{
				Thread.sleep(BackUpProtocol.TIMEOUT_VALS[i]*1000);
			}
			catch(InterruptedException e){
				this.printErrMsg("Interrupted sleep");
			}
			
			try{
				this.lock.lock();
				ArrayList<Integer> peers = this.getCurrChunkPeers();
				if(peers.size() >= this.replicationDeg){
					done = true;
				}
			}
			finally{
				this.lock.unlock();
			}
			
			if(done)
				return true;
		}
		
		return false;
	}
	
	private void exit(){
		try{
			this.inStream.close();
		}
		catch(IOException e){
			this.printErrMsg("Unable to close input stream");
		}
		System.out.println("File "+this.fileName+" backed up with success");
		
		ConcurrentHashMap<String,Runnable> requests = this.server.getRequests();
		if(this.isChunkBackUp())
			requests.remove("BACKUP"+this.fileId+this.chunkNr);
		else
			requests.remove("BACKUP"+this.fileId);
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
	}
	
	private void printErrMsg(String err){
		System.err.println("Error backing up file "+this.fileName+": "+err);
	}
	
	public void stored(int id, int chunk){
		System.out.println("Id: "+ id + " " + "Chunk: " + chunk);
		try{
			this.lock.lock();
			ArrayList<Integer> peers = this.getCurrChunkPeers();	
			if(!peers.contains(id)){
				peers.add(id);
				this.server.getFileManager().incFileChunkRepDeg(this.fileId,chunk,id);
			}
		}
		finally{
			this.lock.unlock();
		}
	}
}