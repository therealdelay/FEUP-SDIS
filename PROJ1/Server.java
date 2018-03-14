import java.io.*;
import java.net.*;
import java.lang.*;
import java.util.*;

public class Server {

	private int id;
	private String version;
	private DatagramSocket serviceSocket;
	private MulticastSocket MCsocket;
	private MulticastSocket MDBsocket;
	private MulticastSocket MDRsocket;
	private InetAddress group;
	private int val;
	
	private final static int MAX_WAIT = 400;
	
	public static void main(String[] args){
		if(args.length != 6){
			Server.printUsage();
			return;
		}
			
		Server server = new Server(args);
		//server.disconnect();
		server.start();
	}
	
	public static void printUsage(){
		System.out.println("Wrong number of arguments");
	}
		
	public Server(String args[]){
		
		this.version = args[0];
		this.id = Integer.parseInt(args[1]);
		
		//Connect Sockets variables
		String[] name = args[2].split(":");
		InetAddress group = null;
		
		System.out.println(Arrays.toString(name));
		
		//Connect serviceSocket
		try{
			group = InetAddress.getByName(name[0]);
		}
		catch(UnknownHostException e){
			System.out.println("Couldn't find service host");
		}
		
		try{
			this.serviceSocket = new DatagramSocket(Integer.parseInt(name[1]), group);
		}
		catch(SocketException e){
			System.err.println("Failed to create socket");
		}
		
		name = args[3].split(":");
		System.out.println(Arrays.toString(name));
		
		//Connect MCsocket
		try{
			group = InetAddress.getByName(name[0]);
		}
		catch(UnknownHostException e){
			System.out.println("Couldn't find multicast group");
			this.disconnect();
			System.exit(1);
		}
				
		try{
			this.MCsocket = new MulticastSocket(Integer.parseInt(name[1]));
		}
		catch(IOException e){
			System.err.println("Failed to create multicast socket");
			this.disconnect();
			System.exit(1);
		}
		
		try{ 
			this.MCsocket.joinGroup(group);
		}
		catch(IOException e){
			System.err.println("Error joining group");
			this.disconnect();
			System.exit(1);
		}
		
		//Connect MDBsocket
		name = args[4].split(":");
		
		try{
			group = InetAddress.getByName(name[0]);
		}
		catch(UnknownHostException e){
			System.out.println("Couldn't find multicast group");
			this.disconnect();
			System.exit(1);
		}
		
		try{
			this.MDBsocket = new MulticastSocket(Integer.parseInt(name[1]));
		}
		catch(IOException e){
			System.err.println("Failed to create multicast socket");
			this.disconnect();
			System.exit(1);
		}
		
		try{ 
			this.MDBsocket.joinGroup(group);
		}
		catch(IOException e){
			System.err.println("Error joining group");
			this.disconnect();
			System.exit(1);
		}
		
		//Connect MDRsocket
		name = args[5].split(":");
		
		try{
			group = InetAddress.getByName(name[0]);
		}
		catch(UnknownHostException e){
			System.out.println("Couldn't find multicast group");
			this.disconnect();
			System.exit(1);
		}
		
		try{
			this.MDRsocket = new MulticastSocket(Integer.parseInt(name[1]));
		}
		catch(IOException e){
			System.err.println("Failed to create multicast socket");
			this.disconnect();
			System.exit(1);
		}
		
		try{ 
			this.MDRsocket.joinGroup(group);
		}
		catch(IOException e){
			System.err.println("Error joining group");
			this.disconnect();
			System.exit(1);
		}
	}
	
	private void disconnect(){
		if(this.serviceSocket != null)
			this.serviceSocket.close();
		
		if(this.MCsocket != null)
			this.MCsocket.close();
		
		if(this.MDBsocket != null)
			this.MDBsocket.close();
		
		if(this.MDRsocket != null)
			this.MDRsocket.close();
	}
		
	
	/*
	public void execute(){
		this.val = 0;
		Thread test = new Thread(new StoredSubService());
		test.start();
		try{
			test.join();
		}
		catch(InterruptedException e){
			System.err.println("Interrupted");
		}
		System.out.println(this.val);
	}
	*/
		
	private void start(){
		while(true){
			byte[] buf = new byte[256];
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			try{
				this.serviceSocket.receive(packet);
			}
			catch(IOException e){
				System.out.println("Error receiving message");
			}
			processRequest(packet);
		}
	}
	
	/*
	public void multicast(){
		DatagramPacket packet = new DatagramPacket(this.mcast_msg.getBytes(), this.mcast_msg.length(), this.group, this.mcast_port);
		try{
			this.mcastSocket.setTimeToLive(1);
			this.mcastSocket.send(packet);
			System.out.println("Multicast sent");
		}
		catch(IOException e){
			System.err.println("Error sending multicast");
		}
	}
	*/
	
	private void processRequest(DatagramPacket packet){
		String request = new String(packet.getData());
		request = request.trim();
		System.out.println("Request received: " + request);
		String[] res = request.split(" ");
		switch(res[0]){
			case "echo":
				String[] msg = new String[res.length-1];
				System.arraycopy(res,1,msg,0,(res.length-1));
				System.out.println("Msg echoed: "+Arrays.toString(msg));
				break;
				
			case "exit":
				this.disconnect();
				System.exit(0);
				break;
				
			default:
				System.out.println("Request order not recognized");
				break;
		}
		
		System.out.println("----------------------");
	}

	/*
	private void sendAnswer(String answer, DatagramPacket packet) throws IOException{
		System.out.println("Answer sent: " + answer);
		byte[] buf = answer.getBytes();
		InetAddress address = packet.getAddress();
		int port = packet.getPort();
		packet = new DatagramPacket(buf, buf.length, address, port);
		socket.send(packet);
	}
	*/
	
	class StoredSubService implements Runnable{
		public void StoredSubService(){}
	
		@Override
		public void run() {
			Random rand = new Random();
			int waitTime = rand.nextInt(Server.MAX_WAIT+1);
			
			try{
				Thread.sleep(waitTime);
			}
			catch(InterruptedException e){
				System.err.println("Interrupted");
			}
			val++;
		}
	}
}