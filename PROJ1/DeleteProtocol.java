import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.TimeUnit;

public class DeleteProtocol implements Runnable {
	
	private Server server;
	private String fileName;
	private String fileId;
	
	public DeleteProtocol(Server server, String fileName){
		this.server = server;
		this.fileName = fileName;
		this.fileId = ServerFile.toId(fileName);
	}
	
	@Override
	public void run (){
		this.server.getFileManager().removeAllChunks(this.fileId);
		int n = 3;
		while(n > 0){
			this.sendDeleteMsg();

			int delay = this.getRandomFrequency();
			try{
				System.out.println("Waiting for " + delay + " seconds.");
				TimeUnit.SECONDS.sleep(delay);
			}
			catch(InterruptedException e){
				System.out.println(e);
			}
			n--;
		}
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
			this.printErrMsg("Unable to send DELETE message");
		}
	}

	private int getRandomFrequency(){
		Random r = new Random();
		int n = r.nextInt(3)+1;
		return n;
	}
	
	private String getDeleteMsg(){
		return "DELETE "+this.server.getVersion()+" "+this.server.getId()+" "+this.fileId;
	}
	
	private void printErrMsg(String err){
		System.err.println("Error deleting file "+this.fileName+": "+err);
	}
}