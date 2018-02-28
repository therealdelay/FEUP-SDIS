import java.io.*;
import java.net.*;

public class Client {
	
	private DatagramSocket socket;
	private MulticastSocket mcastSocket;
	private InetAddress address;
	private InetAddress group;
	private String hostName;
	private String mcast_addr;
	private int portNumber;
	private int mcast_port;
	private String op;
	private String[] args;

	public static void main(String[] args){
		Client c = new Client(args);
		c.connect();
		//c.request();
		//c.disconnect();
	}

	public Client(String[] args){
		
		this.mcast_addr = args[0];
		this.mcast_port = Integer.parseInt(args[1]);
		op = args[2];
		
		int argsLength = args.length-3;
		this.args = new String[argsLength];
		System.arraycopy(args, 3, this.args, 0, argsLength);
	}
	
	private void connectMulticast(){
		try{
			this.mcastSocket = new MulticastSocket(this.mcast_port);
		}
		catch(IOException e){
			System.err.println("Failed to create multicast socket");
		}
		
		try{
			this.group = InetAddress.getByName(this.mcast_addr);
		}
		catch(UnknownHostException e){
			System.out.println("Failed to find multicast group");
		}
		
		try{ 
			this.mcastSocket.joinGroup(this.group);
		}
		catch(IOException e){
			System.err.println("Error joining group");
		}
		
		byte buf[] = new byte[100];
		DatagramPacket packet = new DatagramPacket(buf,buf.length);
		
		try{
			this.mcastSocket.setSoTimeout(15*1000);
			this.mcastSocket.receive(packet);
		}	
		catch(IOException e){
			System.err.println("Failed to receive multicast info");
			this.mcastSocket.close();
			System.exit(1);
		}
		
		System.out.println((new String(packet.getData())).trim());
		
		try{
			this.mcastSocket.leaveGroup(this.group);
		}
		catch(IOException e){
			System.err.println("Failed to leave multicast group");
		}
		
		this.mcastSocket.close();
		
		System.out.println("Closed with success");
		
	}
	
	
	private void connectService(){
		try{
			this.socket = new DatagramSocket();
			this.socket.setSoTimeout(1000);
		}
		catch(SocketException e){
			System.err.println("Failed to create server");
			System.exit(1);
		}
		
		this.address = null;

		try{
			this.address = InetAddress.getByName(this.hostName);
			System.out.println(this.address.getHostName()+" - "+this.address.getHostAddress());
		}
		catch(UnknownHostException e){
			System.err.println("Unknown Host");
			System.exit(1);
		}
	}
		
	
	public void connect(){
		connectMulticast();
		//connectService();
	}
	
	public void disconnect(){
		this.socket.close();
		System.out.println("Disconnected");
	}
	
	public void request(){
		String msgStr = this.op.toUpperCase();
		for(int i = 0; i < this.args.length; i++)
			msgStr += " " + this.args[i];
		
		System.out.println("Message sent: " + msgStr);
		
		byte[] msg = msgStr.getBytes();
					
	    DatagramPacket sPacket = new DatagramPacket(msg, msg.length, this.address, this.portNumber);
		try{
			this.socket.send(sPacket);
		}
		catch (IOException e) {
	    	System.err.println("Cenas 2");
			return;
		}
		
		
		// get response
		byte[] rbuf = new byte[50];
		DatagramPacket rPacket = new DatagramPacket(rbuf, rbuf.length);
	
		try{
			this.socket.receive(rPacket);
		}
		catch (IOException e) {
	    	System.err.println("Cenas 2");
			return;
		}
		
		// display response
		String res = new String(rPacket.getData());
		System.out.println("Echoed Message: " + res.trim());
	}
}