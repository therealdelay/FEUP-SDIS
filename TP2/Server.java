import java.io.*;
import java.net.*;
import java.util.*;

public class Server {

	private DatagramSocket socket;
	private MulticastSocket mcastSocket;
	private ArrayList<String> owners = new ArrayList<String>();
	private ArrayList<String> plates = new ArrayList<String>();
	
	public static void main(String[] args) throws IOException{
		if(args.length != 3){
			System.out.println("Wrong number of arguments");
			return;
		}
			
		try{
			Server server = new Server(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));
			server.start();
		}
		catch(SocketException e){
			System.err.println("Failed to create server");
		}
	}

	public Server(int port_number){
		try{
			this.socket = new DatagramSocket(port_number);
			register("13-21-XV", "Danny Soares");
			register("76-16-ZO", "Anabela Pinho");
		}
		catch(SocketException e){
			System.err.println("Failed to create socket");
		}
		
		try{
			this.mcastSocket = new MulticastSocket();
			register("13-21-XV", "Danny Soares");
			register("76-16-ZO", "Anabela Pinho");
		}
		catch(SocketException e){
			System.err.println("Failed to create socket");
		}
	}
		
	private void start() throws IOException{
		MulticastThread mcastThread = new MulticastThread(this);
		mcastThread.start();
		
		while(true){
			byte[] buf = new byte[256];
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			this.socket.receive(packet);
			processRequest(packet);
		}
	}
	
	private void processRequest(DatagramPacket packet){
		String request = new String(packet.getData());
		request = request.trim();
		System.out.println("Request received: " + request);
		String[] res = request.split(" ");
		switch(res[0]){
			case "REGISTER":
				try{
					sendAnswer(String.valueOf(register(res[1],res[2]+" "+res[3])), packet);
				}
				catch(IOException e){
					System.out.println("Error sending response");
				}
				break;
				
			case "LOOKUP":
				try{
					sendAnswer(lookup(res[1]), packet);
				}
				catch(IOException e){
					System.out.println("Error sending response");
				}
				break;
				
			default:
				System.out.println("Request order not recognized");
				break;
		}
		
		System.out.println("----------------------");
	}

	private void sendAnswer(String answer, DatagramPacket packet) throws IOException{
		System.out.println("Answer sent: " + answer);
		byte[] buf = answer.getBytes();
		InetAddress address = packet.getAddress();
		int port = packet.getPort();
		packet = new DatagramPacket(buf, buf.length, address, port);
		socket.send(packet);
	}

	private int register(String plate_number, String owner_name){
		for(int i = 0; i < this.plates.size(); i++){
			if(this.plates.get(i).equals(plate_number)){
				System.out.println("Plate already in the system");
				return -1;
			}
		}

		this.plates.add(plate_number);
		this.owners.add(owner_name);

		/*
		for(String s : this.plates)
			System.out.println(s);

		for(String s : this.owners)
			System.out.println(s);
		*/

		return this.plates.size();
	}

	private String lookup(String plate_number){
		for(int i = 0; i < this.plates.size(); i++){
			if(this.plates.get(i).compareTo(plate_number) == 0)
				return this.owners.get(i);
		}

		return "NOT_FOUND";
	}
}