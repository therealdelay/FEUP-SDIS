import java.io.*;
import java.net.*;
import java.util.*;

public class Server {

	private DatagramSocket socket;
	private ArrayList<String> owners = new ArrayList<String>();
	private ArrayList<String> plates = new ArrayList<String>();
	
	public static void main(String[] args) throws IOException{
		try{
			Server server = new Server(Integer.parseInt(args[0]));
			server.start();
		}
		catch(SocketException e){
			System.err.println("Failed to create server");
		}
	}

	public Server(int port_number) throws SocketException{
		try{
			this.socket = new DatagramSocket(port_number);
			register("13-21-XV", "Danny Soares");
			register("76-16-ZO", "Anabela Pinho");
			System.out.println(lookup("13-21-XV"));
		}
		catch(SocketException e){
			System.err.println("Failed to create socket");
		}
	}
		
	private void start() throws IOException{
		int notOver = 1;
		while(notOver == 1){
			byte[] buf = new byte[256];
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			this.socket.receive(packet);
			String command = new String(packet.getData());
			System.out.println("command " + command);

			notOver = 0;

		}
	}

	private void sendAnswer(String answer, DatagramPacket packet) throws IOException{
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

		for(String s : this.plates)
			System.out.println(s);

		for(String s : this.owners)
			System.out.println(s);

		return this.plates.size();
	}

	private String lookup(String plate_number){
		for(int i = 0; i < this.plates.size(); i++){
			if(this.plates.get(i).equals(plate_number))
				return this.owners.get(i);
		}

		return "NOT_FOUND";
	}
}