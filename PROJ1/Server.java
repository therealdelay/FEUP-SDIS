import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.lang.*;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class Server implements ServerInterf {

	private int id;
	private String version;
	
	private Registry rmiRegistry;
	
	private TwinMulticastSocket MCsocket;
	private Thread MClistener;
	private TwinMulticastSocket MDBsocket;
	private Thread MDBlistener;
	private TwinMulticastSocket MDRsocket;
	private Thread MDRlistener;

	private ThreadPoolExecutor pool;
	private Path SWD;
	
	private ConcurrentHashMap<String,Runnable> requests;
	public ConcurrentHashMap<String,Runnable> restoreThreads;
	private FileManager fileManager;
	
	public final static int MAX_WAIT = 400;
	public final static int MAX_CHUNK_SIZE = 64000;
	private final static int MAX_BUFFER_SIZE = 70000;
	public final static int MAX_MEM = 8388608;
	public static int usedMem = 0;

	
	public static void main(String[] args){
		if(args.length != 5){
			Server.printUsage();
			return;
		}
			
		Server server = new Server(args);
	}
	
	public static void printUsage(){
		System.out.println("Wrong number of arguments");
	}
		
	public Server(String args[]){
		
		this.version = args[0];
		this.id = Integer.parseInt(args[1]);
		
		//Connect to RMI
		this.connectRMI();
		
		try{
			//Connect MCsocket
			this.MCsocket = new TwinMulticastSocket(args[2]);
	
			//Connect MDBsocket
			this.MDBsocket = new TwinMulticastSocket(args[3]);
		
			//Connect MDRsocket
			this.MDRsocket = new TwinMulticastSocket(args[4]);
		}
		catch(IOException e){
			this.disconnect();
		}
		
		this.fileManager = new FileManager();
		this.requests = new ConcurrentHashMap<String,Runnable>();
		this.restoreThreads = new ConcurrentHashMap<String,Runnable>();
		
	    //Create Server Working Directory
		this.createSWD(args);
		this.fileManager.setWDir(this.SWD);
		
		this.pool = new ThreadPoolExecutor(2,4,10,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(2));
		
		//Start multicast channels listener threads
		this.startListenerThreads();
	}
	
	
	private void connectRMI(){
		int port = Registry.REGISTRY_PORT;
		
		InetAddress localAddress = null;
		try{
			InetAddress address = InetAddress.getByName("localhost");
			localAddress = InetAddress.getLocalHost();
			System.out.println(address.getHostAddress()+" "+address.getHostName());
			System.out.println(localAddress.getHostAddress()+" "+localAddress.getHostName());
		}
		catch(Exception e){
			e.printStackTrace();
		}
		
		this.rmiRegistry = null;
		try{
			this.rmiRegistry = LocateRegistry.createRegistry(port);
		}
		catch(Exception e){
			
			System.out.println("Registry already exists new registry");
			
			try{
				this.rmiRegistry = LocateRegistry.getRegistry(port);
			}
			catch(Exception e2){
				System.err.println("Server exception: " + e.toString());
				e.printStackTrace();
			}
		}
		
		try{
            ServerInterf proxy = (ServerInterf) UnicastRemoteObject.exportObject(this, 0);
			String name = Integer.toString(this.id);
			System.out.println(name);
            this.rmiRegistry.rebind(name, proxy);
		} catch (Exception e) {
			System.err.println("Unable to set up RMI");
			System.exit(1);
        }
	}
	
	private void createSWD(String args[]){
		try{
			this.SWD = Paths.get("server"+this.id);
			Files.createDirectory(this.SWD);
		}
		catch(IOException e){}
		
		this.deleteSWDContent();
		
		//this.readSWDFiles();
		/*
		if(args.length == 6){
			if(args[5].compareTo("-clean") == 0){
				
			}
		}
		else
			this.readSWDFiles();
		*/
		System.out.println(this.fileManager.toString());
	}
	
	private void startListenerThreads(){
		this.MClistener = new Thread(new MCListener(this));
		this.MDBlistener = new Thread(new MDBListener(this));
		this.MDRlistener = new Thread(new MDRListener(this));
		this.MClistener.start();
		this.MDBlistener.start();
		this.MDRlistener.start();
	}
	
	private void disconnect(){
		
		if(this.MCsocket != null)
			this.MCsocket.close();
		
		if(this.MDBsocket != null)
			this.MDBsocket.close();
		
		if(this.MDRsocket != null)
			this.MDRsocket.close();
		
		if(this.rmiRegistry != null){
			try{
				this.rmiRegistry.unbind("server");
				UnicastRemoteObject.unexportObject(this, true);
				UnicastRemoteObject.unexportObject(this.rmiRegistry, true);
			}
			catch(Exception e){
				System.err.println("Error disconnecting Server");
			}
		}
	}
	
	public String echo(String msg){
		this.printRequest("ECHO "+msg);
		return msg;
	}
	
	public void backup(String fileName, int repDegree){
		this.printRequest("BACKUP "+fileName+" "+repDegree);
		Runnable handler = new BackUpProtocol(this, fileName, repDegree);
		this.requests.put("BACKUP"+ServerFile.toId(fileName), handler);
		this.pool.execute(handler);
	}
	
	public void restore(String fileName) throws RemoteException {
		this.printRequest("RESTORE "+fileName);
		String fileId = ServerFile.toId(fileName);
		Runnable handler = new RestoreProtocol(this, fileName, fileId);
		this.requests.put("RESTORE"+ServerFile.toId(fileName), handler);
		this.pool.execute(handler);
	}
	
	public void delete(String fileName){
		this.printRequest("DELETE "+fileName);
		this.pool.execute(new DeleteProtocol(this, fileName));
	}
	
	public void reclaim(int mem){
		this.printRequest("RECLAIM "+mem);
		this.pool.execute(new ReclaimProtocol(this, mem));
	}
	
	public String state(){
		this.printRequest("STATE");
		return this.fileManager.toString();
	}
	
	private void printRequest(String request){
		System.out.println("----------------------");
		System.out.println("Request received: "+request);
		System.out.println("----------------------");
	}
	
	public String getVersion(){
		return this.version;
	}
	
	public int getId(){
		return this.id;
	}
	
	public Path getSWD(){
		return this.SWD;
	}
	
	public ConcurrentHashMap<String,Runnable> getRequests(){
		//System.out.println(this.requests.toString());
		return this.requests;
	}
	
	public TwinMulticastSocket getMCsocket(){
		return this.MCsocket;
	}
	
	public TwinMulticastSocket getMDBsocket(){
		return this.MDBsocket;
	}
	
	public TwinMulticastSocket getMDRsocket(){
		return this.MDRsocket;
	}
	
	public FileManager getFileManager(){
		return this.fileManager;
	}
	
	private void deleteSWDContent(){
		File[] files = this.SWD.toFile().listFiles();
		for(File file : files){
			if(!file.isDirectory())
				file.delete();
		}
	}
	
	/*
	private void readSWDFiles(){
		String[] parts;
		File[] files = this.SWD.toFile().listFiles();
		for(File file : files){
			String[] nameParts = file.getName().split("\\.");
			if(nameParts[0].compareTo("chunk") == 0){
				this.fileManager.addChunk(new ServerChunk(file.getName(), file.length()));
				if(this.usedMem + file.length() <= this.MAX_MEM)
					this.usedMem += file.length();
				else
					System.out.println("Not enough space to store this file.");
			}
			else{
				this.fileManager.addFile(new ServerFile(file.getName(),0));
			}
		}
	}
	*/
	
	class MCListener implements Runnable{
		private Server server;
		
		public MCListener(Server server){
			this.server = server;
		}
		
	
		@Override
		public void run() {
			while(true){
				byte buf[] = new byte[100];
				DatagramPacket packet = new DatagramPacket(buf,buf.length);
		
				try{
					MCsocket.receive(packet);
				}
				catch(IOException e){	
					System.err.println("Error receiving MCsocket packet");
				}
				
				
				System.out.println("Packet received at MCsocket: " + new String(packet.getData()).trim());
				Thread handler = new Thread(new ControlProtocol(this.server, packet.getData()));
				handler.start();
			}
		}

	}
	
	class MDBListener implements Runnable{
		private Server server;
		
		public MDBListener(Server server){
			this.server = server;
		}
	
		@Override
		public void run() {
			
			while(true){
				byte buf[] = new byte[Server.MAX_BUFFER_SIZE];
				DatagramPacket packet = new DatagramPacket(buf,buf.length);
				
				try{
					MDBsocket.receive(packet);
				}
				catch(IOException e){
					System.err.println("Error receiving MDBsocket packet");
				}
				
				Thread handler = new Thread(new StoreChunk(this.server, packet.getData(), packet.getLength()));
				handler.start();
				//System.out.println("Packet received at MDBsocket: " + new String(packet.getData()).trim());
			}
		}
		
		
	}
	
	class MDRListener implements Runnable{
		private Server server;
		
		public MDRListener(Server server){
			this.server = server;
		}
	
		@Override
		public void run() {
			while(true){
				byte buf[] = new byte[Server.MAX_BUFFER_SIZE];
				DatagramPacket packet = new DatagramPacket(buf,buf.length);
		
				try{
					MDRsocket.receive(packet);
				}
				catch(IOException e){
					System.err.println("Error receiving MDRsocket packet");
				}
				
				Thread handler = new Thread(new Chunk(this.server, packet.getData(), packet.getLength()));
				handler.start();
				//System.out.println("Packet received at MDRsocket: " + new String(packet.getData()).trim());
			}
		}
	}
}