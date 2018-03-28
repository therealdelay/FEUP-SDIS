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
	private String fileId;
	private int replicationDeg;
	private ReentrantLock lock;
	
	private int currTry = 0;
	private int currChunk = 0;
	private ArrayList<Integer> peers;
	
	private FileInputStream inStream = null;
	
	public BackUpProtocol(Server server, String fileId, int replicationDeg){
		this.server = server;
		this.fileId = fileId;
		this.replicationDeg = replicationDeg;
		this.lock = new ReentrantLock();
		this.peers = new ArrayList<Integer>();
	}
	
	@Override
	public void run (){
		
		this.inStream = this.openSrcFile();
		if(this.inStream == null){
			this.exit_err("Unable to open src file");
			return;
		}
		
		int read;
		byte[] buf = new byte[Server.MAX_CHUNK_SIZE];
		
		try{
			while((read = this.inStream.read(buf)) >= 0){
				byte[] body = new byte[read];
				System.out.println("Bytes read: "+read);
				System.arraycopy(buf,0,body,0,read);
				//System.out.println("Bytes body: "+body.length);
				if(!this.backUpChunk(body)){
					this.exit_err("Unable to reach required replication degree in chunk "+this.currChunk);
					return;
				}
			}
		}
		catch(IOException e){
			this.exit_err("Unable to read src file in chunk "+this.currChunk);
			return;
		}
		
		this.exit();
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
	
	private FileInputStream openSrcFile(){
		FileInputStream inStream;
		try{
			inStream = new FileInputStream(new File(this.server.getSWD().toString()+"/"+this.fileId));
		}
		catch(FileNotFoundException e){
			System.err.println("File not found");
			return null;
		}
		return inStream;
	}
	
	private boolean backUpChunk(byte buf[]){
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
				if(this.peers.size() >= this.replicationDeg){
					this.currChunk++;
					this.peers.clear();
					done = true;
				}
			}
			finally{
				this.lock.unlock();
			}
			
			if(done)
				return true;
		}
		
		//this.currChunk++;
		//return true;
		return false;
	}
	
	private void exit(){
		try{
			this.inStream.close();
		}
		catch(IOException e){
			this.printErrMsg("Unable to close input stream");
		}
		System.out.println("File "+this.fileId+" backed up with success");
		
		ConcurrentHashMap<String,Runnable> requests = this.server.getRequests();
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
		System.err.println("Error backing up file "+this.fileId+": "+err);
	}
	
	public void stored(int id, int chunk){
		try{
			this.lock.lock();
			
			System.out.println("Id: "+ id + " " + "Chunk: " + chunk);
			if(chunk == this.currChunk){
				if(!this.peers.contains(id))
					this.peers.add(id);
			}
		}
		finally{
			this.lock.unlock();
		}
	}
	
	public void test(){
		System.out.println("THIS IS A TEST");
	}
}