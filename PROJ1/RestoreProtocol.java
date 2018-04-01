import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class RestoreProtocol implements Runnable {
	
	private final static int TIMEOUT = 5000;
	
	private Server server;
	private String fileName;
	private String fileId;
	private int currChunk = 0;
	private boolean received = false;
	private byte[] buf;
	private ReentrantLock lock;
	
	private FileOutputStream outStream;
	
	public RestoreProtocol(Server server, String fileName, String fileId){
		this.server = server;
		this.fileName = fileName;
		this.fileId = fileId;
		this.lock = new ReentrantLock();
	}
	
	@Override
	public void run (){
		FileManager fileManager = this.server.getFileManager();
		int totalChunks = fileManager.getFileTotalChunks(this.fileName);
		System.out.println("Total Chunks: "+totalChunks);
		this.outStream = fileManager.getOutStream(ServerFile.toRelativeName(this.fileName));
		
		for(int i = 0; i < totalChunks; i++){
			if(!this.receiveChunk()){
				this.exit_err("Unable to restore chunk "+this.currChunk+" of file "+this.fileName);
				return;
			}
			this.saveChunk();
		}
		
		this.exit();
	}
	
	private boolean receiveChunk(){
		this.sendGetChunkMsg();
		
		long start = System.currentTimeMillis();
		boolean done = false;
		while(true){
			
			try{
				this.lock.lock();
				if(this.received){
					this.received = false;
					done = true;
				}
			}
			finally{
				this.lock.unlock();
			}
			
			if(done)
				break;
			
			if(System.currentTimeMillis() - start > RestoreProtocol.TIMEOUT)
				break;
		}
		
		return done;
	}
	
	private void saveChunk(){
		System.out.println("RestoreProt BufLength: "+this.buf.length);
		try{
			this.outStream.write(this.buf,0,this.buf.length);
		}
		catch(IOException e){
			this.printErrMsg("Unable to save chunk "+this.currChunk+" of file "+this.fileName);
		}
	}
	
	private void sendGetChunkMsg(){
		String msg = this.getGetChunkMsg();
		TwinMulticastSocket socket = this.server.getMCsocket();
		DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), socket.getGroup(), socket.getPort());
		
		//Send msg
		try{
			socket.send(packet);
		}
		catch(IOException e){
			this.printErrMsg("Unable to send STORED message");
		}
	}
	
	private String getGetChunkMsg(){
		return "GETCHUNK "+this.server.getVersion()+" "+this.server.getId()+" "+this.fileId+" "+this.currChunk;
	}
	
	private void removeRequest(){
		ConcurrentHashMap<String,Runnable> requests = this.server.getRequests();
		requests.remove("RESTORE"+this.fileId);
	}
	
	private void exit(){
		try{
			this.outStream.close();
		}
		catch(IOException e){
			this.printErrMsg("Unable to close input stream");
		}
		System.out.println("File "+this.fileName+" restored up with success");
		
		this.removeRequest();
	}
	
	private void exit_err(String err){
		this.printErrMsg(err);
		try{
			if(this.outStream != null)
				this.outStream.close();
		}
		catch(IOException e){
			this.printErrMsg("Unable to close output stream");
		}
		
		this.server.getFileManager().deleteSWDFile(ServerFile.toRelativeName(this.fileName));
		this.removeRequest();
	}
	
	private void printErrMsg(String err){
		System.err.println("Error restoring file "+this.fileName+": "+err);
	}
	
	public void chunk(int chunk, byte[] buf){
		try{
			this.lock.lock();
			if(chunk == this.currChunk && this.received == false){
				this.received = true;
				this.currChunk++;
				this.buf = buf;
			}
		}
		finally{
			this.lock.unlock();
		}
	}
}