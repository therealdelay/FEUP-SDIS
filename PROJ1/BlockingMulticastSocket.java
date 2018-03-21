import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class BlockingMulticastSocket {
	
	private MulticastSocket mcastSocket;
	private ReentrantLock lock;
	private int port;
	private InetAddress group;
	
	public BlockingMulticastSocket(String compName) throws IOException{
		String[] name = compName.split(":");
		System.out.println(Arrays.toString(name));
		
		this.group = InetAddress.getByName(name[0]);
		
		this.port = Integer.parseInt(name[1]);
	
		this.mcastSocket = new MulticastSocket(this.port);
		
		if(this.mcastSocket == null)
			System.out.println("null socket inside inside");
		 
		this.mcastSocket.joinGroup(this.group);
		
		this.lock = new ReentrantLock();
	}
	
	public int getPort(){
		return this.port;
	}
	
	public InetAddress getGroup(){
		return this.group;
	}
	
	public void receive(DatagramPacket packet) throws IOException{
		try{
			this.lock.lock();
				
			this.mcastSocket.receive(packet);
		}
		finally{
			this.lock.unlock();
		}
	}
	
	public void send(DatagramPacket packet) throws IOException{
		try{
			this.lock.lock();
				
			this.mcastSocket.setTimeToLive(1);
			this.mcastSocket.send(packet);
		}
		finally{
			this.lock.unlock();
		}
	}
	
	public void close(){
		this.mcastSocket.close();
	}
}