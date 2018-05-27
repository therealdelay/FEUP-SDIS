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

public class DownProtocol implements Runnable {
	
	private Server server;
	private ServerChunk currChunk;

	public DownProtocol(Server server)
	{
		this.server = server;
	}
	
	@Override
	public void run (){

		ArrayList<ServerChunk> chunks = this.server.getFileManager().getChunks();

		for(ServerChunk chunk : chunks){
			this.currChunk = chunk;
			this.sendRemovedMsg();
		}
	}

	private void sendRemovedMsg(){
		String msg = this.getRemovedMsg();
		TwinMulticastSocket socket = this.server.getMCsocket();
		DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), socket.getGroup(), socket.getPort());
		
		//Send msg
		try{
			socket.send(packet);
		}
		catch(IOException e){
			this.printErrMsg("Unable to send REMOVED message");
		}
		catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
			this.printErrMsg("MCsocket packet received is insecure");
		}
	}
	
	private String getRemovedMsg(){
		return "REMOVED "+this.server.getVersion()+" "+this.server.getId()+" "+this.currChunk.toMsg();
	}

	private void printErrMsg(String err){
		System.err.println("DownProtocol: "+err);
	}	
}