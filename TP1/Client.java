import java.io.*;
import java.net.*;

public class Client {

	public static void main(String[] args){
		//Client c = new Client(args[0],(Integer.parseInt(args[1])),args[2],args[3]);
		Client c = new Client(args);
	}

	//public Client(String host_name, int port_number, String operator, String args){
	public Client(String[] args){
		// send request
		DatagramSocket socket = null;
		try {
	        socket = new DatagramSocket();
	    }   
	    catch (SocketException e){
	        System.err.println("Couldn't open quote file.  Serving time instead.");
	    }

		byte[] sbuf = args[1].getBytes();

		InetAddress address = null;

		try{
			address = InetAddress.getByName(args[0]);
		}
		catch(UnknownHostException e){
			System.err.println("Cenas");
		}

		DatagramPacket packet = null;
	    packet = new DatagramPacket(sbuf, sbuf.length, address, 4445);   
		try{
			socket.send(packet);
		}
		catch (IOException e) {
	    	System.err.println("Cenas 2");			
		}
	
		// get response
		byte[] rbuf = new byte[sbuf.length];
		packet = new DatagramPacket(rbuf, rbuf.length);
	
		try{
			socket.receive(packet);
		}
		catch (IOException e) {
	    	System.err.println("Cenas 2");			
		}
		// display response
		String received = new String(packet.getData());
		System.out.println("Echoed Message: " + received);
		socket.close();
	}
}