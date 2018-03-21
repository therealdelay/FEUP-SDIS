import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class TwinMulticastSocket {
	
	private MulticastSocket in;
	private MulticastSocket out;
	private ReentrantLock lock;
	private int port;
	private InetAddress group;
	
	public TwinMulticastSocket(String compName) throws IOException{
		String[] name = compName.split(":");
		System.out.println(Arrays.toString(name));
		
		this.group = InetAddress.getByName(name[0]);
		
		this.port = Integer.parseInt(name[1]);
	
		this.in = new MulticastSocket(this.port);
		this.out = new MulticastSocket(this.port);
		
		if(this.in == null)
			System.out.println("null socket inside inside");
		 
		this.in.joinGroup(this.group);
		this.out.joinGroup(this.group);
		
		this.lock = new ReentrantLock();
	}
	
	public int getPort(){
		return this.port;
	}
	
	public InetAddress getGroup(){
		return this.group;
	}
	
	public void receive(DatagramPacket packet) throws IOException{
		this.in.receive(packet);
	}
	
	public void send(DatagramPacket packet) throws IOException{
		try{
			this.lock.lock();
			this.out.setTimeToLive(1);
			this.out.send(packet);
		}
		finally{
			this.lock.unlock();
		}
	}
	
	public void close(){
		this.in.close();
		this.out.close();
	}
}