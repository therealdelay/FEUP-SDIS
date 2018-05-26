	import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import java.security.InvalidKeyException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class RestoreProtocol implements Runnable {
	
	private final static int TIMEOUT = 5000;
	
	private Server server;
	private String fileName;
	private String fileId;
	private int currChunk = 0;
	private boolean received = false;
	private byte[] buf;
	private ReentrantLock lock;

	private SecretKeySpec secretKey;
	private Cipher cipher;
	
	private FileOutputStream outStream;
	
	public RestoreProtocol(Server server, String fileName, String fileId, SecretKeySpec clientKey) throws IOException, UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException{
		this.server = server;
		this.fileName = fileName;
		this.fileId = fileId;
		this.lock = new ReentrantLock();

		this.secretKey = clientKey;
		this.cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
	}
	
	@Override
	public void run (){
		FileManager fileManager = this.server.getFileManager();
		int totalChunks = fileManager.getFileTotalChunks(this.fileName);
		System.out.println("Total Chunks: "+totalChunks);
		this.outStream = fileManager.getOutStream(ServerFile.toRelativeName(this.fileName));

		try {
			this.server.getTCPSocket().accept();
		} catch(IOException e ) {
			System.out.println("Error on accepting TCP Socket.");
			return;
		}
		
		for(int i = 0; i < totalChunks; i++){
			if(!this.receiveChunk()){
				this.exit_err("Unable to restore chunk "+this.currChunk+" of file "+this.fileName);
				return;
			}
			this.saveChunk();
		}

		try {
			this.server.getTCPSocket().close();
		} 
		catch(IOException e ) {
			System.out.println("Error on closing TCP Socket.");
			return;
		}

		this.exit();
	}
	
	private boolean receiveChunk(){
		long start = System.currentTimeMillis();
		
		this.sendGetChunkMsg();
		
		Socket connectionSocket;
		BufferedReader inputStream;

		try {
			connectionSocket = this.server.getTCPSocket().accept();			
			
			inputStream = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
			
		} catch(IOException e ) {
			System.out.println("Error on accepting TCP Socket.");
			return false;
		}
			
		String input = inputStream.readLine();
		System.out.println("Socket Restore " + connectionSocket.getPort());
			
		System.out.println("INPUT " + input);
		
			
		this.buf = input.getBytes();

		return this.buf != null;
/*		
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
*/
		

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
		catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
			System.err.println("MCsocket packet received is insecure");
		}
	}
	
	private String getGetChunkMsg(){
		return "GETCHUNK "+this.server.getVersion()+" "+this.server.getId()+" "+ ServerFile.toEncryptedId(this.fileName, this.secretKey)+" "+this.fileId+" "+this.currChunk;
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
		System.out.println("File "+this.fileName+" restored up with success!");
		
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
	
	public void chunk(int chunk){
		System.out.println("RECEBI chunk " + chunk);
		Socket socket = this.server.getTCPSocket().accept();
		try{
			this.lock.lock();
			if(chunk == this.currChunk && this.received == false){
				this.received = true;
				this.currChunk++;
				
				this.buf = decryptBody(buf);

				this.server.restoreThreads.clear();
			}
		}
		catch(IOException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e){
			this.exit_err("Error decrypting chunk "+this.currChunk + " : " + e);
			return;
		}
		finally{
			this.lock.unlock();
		}
	}

	public byte[] decryptBody(byte[] body) throws IOException,InvalidKeyException,BadPaddingException,IllegalBlockSizeException
	{	
		System.out.println("DECRYPT: " + body.length);
		this.cipher.init(Cipher.DECRYPT_MODE, this.secretKey);
		return this.cipher.doFinal(body);
	}
}