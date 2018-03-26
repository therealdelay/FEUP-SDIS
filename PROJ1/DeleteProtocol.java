import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class DeleteProtocol implements Runnable {
	
	private Server server;
	private String fileId;
	
	public DeleteProtocol(Server server, String fileId){
		this.server = server;
		this.fileId = fileId;
	}
	
	@Override
	public void run (){
		this.deleteOwnFiles();
		this.sendDeleteMsg();
	}
	
	private void deleteOwnFiles(){
		String id = this.fileId.split("\\.")[0];
		FileManager fileManager = this.server.getFileManager();
		fileManager.removeAllChunks(id);
	}
	
	private void sendDeleteMsg(){
		String msg = this.getDeleteMsg();
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
	
	private String getDeleteMsg(){
		return "DELETE "+this.server.getVersion()+" "+this.server.getId()+" "+this.fileId;
	}
	
	private void printErrMsg(String err){
		System.err.println("Error backing up file "+this.fileId+": "+err);
	}
}