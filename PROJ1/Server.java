import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class Server {

	private int id;
	private String version;
	private DatagramSocket serviceSocket;
	
	private TwinMulticastSocket MCsocket;
	private Thread MClistener;
	private TwinMulticastSocket MDBsocket;
	private Thread MDBlistener;
	private TwinMulticastSocket MDRsocket;
	private Thread MDRlistener;

	private ThreadPoolExecutor pool;
	private Path SWD;
	
	private ConcurrentHashMap<String,Runnable> requests;
	private FileManager fileManager;
	
	public final static int MAX_WAIT = 400;
	public final static int MAX_CHUNK_SIZE = 64000;
	private final static int MAX_BUFFER_SIZE = 70000;
	
	public static void main(String[] args){
		if(args.length < 6 || args.length > 7){
			Server.printUsage();
			return;
		}
			
		Server server = new Server(args);
		server.start();
	}
	
	public static void printUsage(){
		System.out.println("Wrong number of arguments");
	}
		
	public Server(String args[]){
		
		this.version = args[0];
		this.id = Integer.parseInt(args[1]);
		
		//Connect serviceSocket
		this.connectServiceSocket(args[2]);
		
		try{
			//Connect MCsocket
			this.MCsocket = new TwinMulticastSocket(args[3]);
	
			//Connect MDBsocket
			this.MDBsocket = new TwinMulticastSocket(args[4]);
		
			//Connect MDRsocket
			this.MDRsocket = new TwinMulticastSocket(args[5]);
		}
		catch(IOException e){
			this.disconnect();
		}
		
		this.fileManager = new FileManager();
		this.requests = new ConcurrentHashMap<String,Runnable>();
		
	    //Create Server Working Directory
		this.createSWD(args);
		this.fileManager.setWDir(this.SWD);
		
		this.pool = new ThreadPoolExecutor(2,4,10,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(2));
		
		//Start multicast channels listener threads
		this.startListenerThreads();
	}
	
	private void connectServiceSocket(String compName){
		String[] name = compName.split(":");
		InetAddress group = null;
		
		
		
		System.out.println(Arrays.toString(name));
		
		try{
			group = InetAddress.getByName(name[0]);
		}
		catch(UnknownHostException e){
			System.out.println("Couldn't find service host");
		}
		
		System.out.println(group.getHostAddress());
		
		try{
			this.serviceSocket = new DatagramSocket(Integer.parseInt(name[1]), group);
		}
		catch(SocketException e){
			System.err.println("Failed to create socket");
		}
	}
	
	private void createSWD(String args[]){
		try{
			this.SWD = Paths.get("server"+this.id);
			Files.createDirectory(this.SWD);
		}
		catch(IOException e){}
		
		if(args.length == 7){
			if(args[6].compareTo("-clean") == 0){
				this.deleteSWDContent();
			}
		}
		else
			this.readSWDFiles();
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
		if(this.serviceSocket != null)
			this.serviceSocket.close();
		
		if(this.MCsocket != null)
			this.MCsocket.close();
		
		if(this.MDBsocket != null)
			this.MDBsocket.close();
		
		if(this.MDRsocket != null)
			this.MDRsocket.close();
	}
	
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
	
	private void processRequest(DatagramPacket packet){
		String request = new String(packet.getData());
		request = request.trim();
		System.out.println("Request received: " + request);
		String[] res = request.split(" ");
		String command = res[0].toUpperCase();
		switch(command){
			case "ECHO":
				String[] msg = new String[res.length-1];
				System.arraycopy(res,1,msg,0,(res.length-1));
				System.out.println("Msg echoed: "+Arrays.toString(msg));
				break;
				
			case "BACKUP":
			    String[] args = new String[res.length-1];
				System.arraycopy(res,1,args,0,(res.length-1));
				this.backupRequest(args);
				break;
				
			case "RESTORE":
			    String[] args2 = new String[res.length-1];
				System.arraycopy(res,1,args2,0,(res.length-1));
				this.restoreRequest(args2);
				break;
				
			case "DELETE":
			    String[] args3 = new String[res.length-1];
				System.arraycopy(res,1,args3,0,(res.length-1));
				this.deleteRequest(args3);
				break;
				
			case "EXIT":
				this.disconnect();
				this.pool.shutdownNow();
				System.exit(0);
				break;
				
			default:
				System.out.println("Request order not recognized");
				break;
		}
		
		System.out.println("----------------------");
	}
	
	private void backupRequest(String[] args){
		Runnable handler = new BackUpProtocol(this, args[0], Integer.parseInt(args[1]));
		this.requests.put("BACKUP"+args[0], handler);
		this.pool.execute(handler);
	}
	
	private void restoreRequest(String[] args){
		Runnable handler = new RestoreProtocol(this, args[0]);
		this.requests.put("RESTORE"+args[0], handler);
		this.pool.execute(handler);
	}
	
	private void deleteRequest(String[] args){
		this.pool.execute(new DeleteProtocol(this, args[0]));
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
	
	private void readSWDFiles(){
		String[] parts;
		File[] files = this.SWD.toFile().listFiles();
		for(File file : files){
			this.fileManager.addFile(new ServerFile(file.getName(),0));
		}
		
		System.out.println(this.fileManager.toString());
	}
	
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