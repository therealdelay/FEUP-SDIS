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

import java.security.InvalidKeyException;
import java.security.MessageDigest;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class Server implements ServerInterf {
	
	private int id;
	private String version;
	private int port;
	private String address;
	
	private Registry rmiRegistry;
	
	private TwinMulticastSocket MCsocket;
	private MCListener MClistener;
	private TwinMulticastSocket MDBsocket;
	private MDBListener MDBlistener;
	private TwinMulticastSocket MDRsocket;
	private MDRListener MDRlistener;

	private ServerSocket tcpSocket; 

	private ThreadPoolExecutor pool;
	private Path SWD;

	public boolean ready = false;

	public UpProtocol initThread;
	private ConcurrentHashMap<String,Runnable> requests;
	public ConcurrentHashMap<String,Runnable> restoreThreads;
	public ConcurrentHashMap<String,Runnable> removedThreads;
	private FileManager fileManager;
		
	public final static int MAX_WAIT = 400;
	public final static int MAX_CHUNK_SIZE = 64000;
	public final static int MAX_CHUNK_SIZE_ENCRYPTED = 64096;
	private final static int MAX_BUFFER_SIZE = 70000;
	public final static int MAX_MEM = 8388608;


	private final static String keyString = "d1nnyomelhorfeiticeirodehogw4rts";
	private static byte[] key;
	private static SecretKeySpec adminKey;

	
	public static void main(String[] args){
		if(args.length != 8){
			Server.printUsage();
			return;
		}
		
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			key = sha.digest(keyString.getBytes("UTF-8"));
			key = Arrays.copyOf(key, 16);

		} catch(NoSuchAlgorithmException | UnsupportedEncodingException e){
			System.err.println("Error creating server key");
		}
			
		Server server = new Server(args);

		Runtime.getRuntime().addShutdownHook(new Thread() {
		    public void run() {
		       	server.exit();
		    }
		});
	}
	
	public static void printUsage(){
		String lineSep = System.lineSeparator();
		String doubleLineSep = lineSep+lineSep;
		String usage =  lineSep+
						"   Server <version> <id> <admin_id> <MC> <MDB> <MDR>"+doubleLineSep+
						"      version: version of the protocol with the format <n>.<m>"+doubleLineSep+
						"      id: server and rmi identifier"+doubleLineSep+
						"      MC,MDB,MDR: multicast channels with the format <ip>/<port>" +
						"      Port for TCP Socket <port>";
						
		System.out.println(usage);
	}
		
	public Server(String args[]){
		
		this.version = args[0];
		this.id = Integer.parseInt(args[1]);

		// this.adminKey = args[2];

		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			byte[] key = sha.digest(args[2].getBytes("UTF-8"));
			key = Arrays.copyOf(key, 16);

			this.adminKey = new SecretKeySpec(key, "AES");
			

		} catch(NoSuchAlgorithmException | UnsupportedEncodingException e){
			System.err.println("Error creating client key");
		}
		//Connect to RMI
		this.connectRMI();
		
		try{
			//Connect MCsocket
			this.MCsocket = new TwinMulticastSocket(args[3], key);
	
			//Connect MDBsocket
			this.MDBsocket = new TwinMulticastSocket(args[4], key);
		
			//Connect MDRsocket
			this.MDRsocket = new TwinMulticastSocket(args[5], key);
		}
		catch(IOException | NoSuchAlgorithmException | NoSuchPaddingException e){
			System.err.println("Error setting up multicast sockets");
			this.disconnect();
			System.exit(1);
		}

		this.fileManager = new FileManager(this.id);
		this.requests = new ConcurrentHashMap<String,Runnable>();
		this.restoreThreads = new ConcurrentHashMap<String,Runnable>();
		this.removedThreads = new ConcurrentHashMap<String,Runnable>();
		
	    //Create Server Working Directory
		this.createSWD();
		
		this.pool = new ThreadPoolExecutor(5,30,10,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(10));
		this.pool.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
		
		//Start multicast channels listener threads
		this.startListenerThreads();

		configureTCPSocket(args);

		printMsg("Set up and running");
		/*
		}
		catch(Exception e){
			System.err.println("Failed to initialize initial protocol");
			this.disconnect();
			System.exit(1);
		}*/
		this.initThread = new UpProtocol(this);
		this.pool.execute(this.initThread);
	}
	
	private void configureTCPSocket(String[] args) {
		address = args[6];
		port = Integer.parseInt(args[7]);
		System.out.println("YOU ARE NOW LISTENING ON RADIO " + port + " " + address);
		try {
			this.tcpSocket = new ServerSocket(port);
		} catch(IOException e) {
			System.out.println("Error creating TCP socket on server.");
		}
		
	}
	
	private void connectRMI(){
		int port = Registry.REGISTRY_PORT;
		this.rmiRegistry = null;
		try{
			this.rmiRegistry = LocateRegistry.createRegistry(port);
		}
		catch(Exception e){
			try{
				this.rmiRegistry = LocateRegistry.getRegistry(port);
			}
			catch(Exception e2){
				System.err.println("Unable to locate registry");
				e.printStackTrace();
				System.exit(1);
			}
		}
		try{
            ServerInterf proxy = (ServerInterf) UnicastRemoteObject.exportObject(this, 0);
			String name = Integer.toString(this.id);
            this.rmiRegistry.rebind(name, proxy);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Unable to set up RMI");
			System.exit(1);
		}
	}
	
	private void createSWD(){
		try{
			this.SWD = Paths.get("server"+this.id);
			Files.createDirectory(this.SWD);
		}
		catch(IOException e){}

		this.fileManager.setWDir(this.SWD);
	}
	
	private void startListenerThreads(){
		this.MClistener = new MCListener(this);
		this.MDBlistener = new MDBListener(this);
		this.MDRlistener = new MDRListener(this);
		(new Thread(this.MClistener)).start();
		(new Thread(this.MDBlistener)).start();
		(new Thread(this.MDRlistener)).start();
	}

	private void terminateListenerThreads(){
		this.MClistener.terminate();
		this.MDBlistener.terminate();
		this.MDRlistener.terminate();
	}
	

	private void disconnect(){
		
		if(this.MCsocket != null)
			this.MCsocket.close();
		
		if(this.MDBsocket != null)
			this.MDBsocket.close();
		
		if(this.MDRsocket != null)
			this.MDRsocket.close();
		

		//TODO: fix this
		/*
		if(this.rmiRegistry != null){
			try{
				this.rmiRegistry.unbind("server");
				UnicastRemoteObject.unexportObject(this, true);
				UnicastRemoteObject.unexportObject(this.rmiRegistry, true);
			}
			catch(Exception e){
				e.printStackTrace();
				System.err.println("Error disconnecting Server");
				System.exit(1);
			}
		}
		*/
	}

	private void exit(){

		this.printMsg("Shutting down...");

		try{
			Thread down = new Thread(new DownProtocol(this));
			down.start();
			down.join();
		}
		catch(Exception e){
			e.printStackTrace();
		}

		this.pool.shutdown();

		while(!this.pool.isShutdown()){
			try{
				System.out.println("Sleeping...");
				Thread.sleep(10000);
			}
			catch(InterruptedException e){
				System.err.println("Interrupted sleep");
			}
		}

		this.terminateListenerThreads();
		this.disconnect();

		this.printMsg("Shut down");
		//System.exit(0);
	}

	private boolean authenticateAdmin(SecretKeySpec clientKey){
		String sA = new String(this.adminKey.getEncoded());
		String sB = new String(clientKey.getEncoded());
		return sA.equals(sB);
	}
	
	public String echo(String msg){
		this.printRequest("ECHO "+msg);
		return msg;
	}
	
	public void backup(SecretKeySpec clientKey, String fileName, int repDegree) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException{
		this.printRequest("BACKUP "+fileName+" "+repDegree);

		if(!ready)
			return;

		Runnable handler = new BackUpProtocol(this, fileName, repDegree, clientKey);
		this.requests.put("BACKUP"+ServerFile.toId(fileName), handler);
		this.pool.execute(handler);
		
	}
	
	public void backupChunk(String fileId, int chunkNr){
		Runnable handler = new BackUpProtocol(this, fileId, chunkNr, 0);
		this.requests.put("BACKUP"+fileId+chunkNr, handler);
		this.pool.execute(handler);
	}
	
	public void restore(SecretKeySpec clientKey, String fileName) throws RemoteException, IOException,NoSuchPaddingException, NoSuchAlgorithmException {
		this.printRequest("RESTORE "+fileName);

		if(!ready)
			return;
		// TODO: verify this
		String fileId = ServerFile.toId(fileName);
		Runnable handler = new RestoreProtocol(this, fileName, fileId, clientKey);
		this.requests.put("RESTORE"+ServerFile.toId(fileName), handler);
		this.pool.execute(handler);
	}
	
	public void delete(SecretKeySpec clientKey, String fileName) throws NoSuchPaddingException, NoSuchAlgorithmException{
		this.printRequest("DELETE "+fileName);

		if(!ready)
			return;

		this.pool.execute(new DeleteProtocol(this, fileName, clientKey));
	}
	
	public String reclaim(SecretKeySpec clientKey, int mem) throws NoSuchPaddingException, NoSuchAlgorithmException{
		this.printRequest("RECLAIM "+mem);
		
		if(!ready)
			return "Server not ready";

		String answer = "";
		if(this.authenticateAdmin(clientKey)){
			this.pool.execute(new ReclaimProtocol(this, mem, clientKey));
			answer = "Reclaim processed.";
		}
		else{
			answer = "Authentication failed!";
		}
		return answer;
	}
	
	private String listFiles(SecretKeySpec clientKey, ArrayList<ServerFile> files){

		ArrayList<ServerFile> userFiles = new ArrayList<ServerFile>();
		for(ServerFile file : files){
			if(file.testKey(clientKey))
				userFiles.add(file);
		}

		Collections.sort(userFiles);

		String newLine = System.lineSeparator();

		String list = "-------------------------------------------------------------------"+newLine+
					  "                               Files                               "+newLine+newLine;


		if(userFiles.size() == 0){
			list +="                           Empty                               "+newLine+newLine;
		}
		else{
			for(ServerFile file : userFiles)
				list += file.toList()+newLine;
		}

		list += "-------------------------------------------------------------------";

		return list;
	}

	public String list(SecretKeySpec clientKey){
		this.printRequest("LIST");

		ArrayList<ServerFile> files = this.fileManager.getFiles();
		return this.listFiles(clientKey,files);
	}

	public String state(SecretKeySpec clientKey){
		this.printRequest("STATE");
		String answer = "";
		if(authenticateAdmin(clientKey))
			answer = this.fileManager.toString();
		else
			answer = "Authentication failed!";

		return answer;
	}

	public void shutdown(SecretKeySpec clientKey){
		this.printRequest("SHUTDOWN");
		if(authenticateAdmin(clientKey))
			System.exit(0);
	};
	
	public void printMsg(String msg){
		System.out.println("Server: "+msg);
	}

	private void printRequest(String request){
		System.out.print("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");
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

	public int getPort() {
		return port;
	}

	public String getAddress() {
		return address;
	}
	
	public ConcurrentHashMap<String,Runnable> getRequests(){
		return this.requests;
	}

	public ConcurrentHashMap<String,Runnable> getRestoreThreads(){
		return this.restoreThreads;
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
	
	public ServerSocket getTCPSocket() { return tcpSocket; }
	
	class MCListener implements Runnable{
		private Server server;
		private volatile boolean running = true;
		
		public MCListener(Server server){
			this.server = server;
		}
		
		public void terminate(){
			running = false;
		}
	
		@Override
		public void run() {
			while(running){
				byte buf[] = new byte[300];
				DatagramPacket packet = new DatagramPacket(buf,buf.length);
		
				try{
					MCsocket.receive(packet);
				}
				catch(IOException e){
					if(running)
						System.err.println("Error receiving MCsocket packet");
				}
				catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
					if(running)
						System.err.println("MCsocket packet received is insecure: " + e);
				}
				
				if(running){
					System.out.println("Packet received at MCsocket: " + new String(packet.getData()).trim() + "\n");
					pool.execute(new ControlProtocol(this.server, packet.getData()));
				}
			}
		}

	}
	
	class MDBListener implements Runnable{
		private Server server;
		private volatile boolean running = true;
		
		public MDBListener(Server server){
			this.server = server;
		}

		public void terminate(){
			running = false;
		}
	
		@Override
		public void run() {
			
			while(running){
				byte buf[] = new byte[Server.MAX_BUFFER_SIZE];
				DatagramPacket packet = new DatagramPacket(buf,buf.length);
				
				try{
					MDBsocket.receive(packet);
				}
				catch(IOException e){
					if(running)
						System.err.println("Error receiving MDBsocket packet");
				}
				catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
					if(running)
						System.err.println("MDBsocket packet received is insecure: " + e);
				}

				if(running){
					pool.execute(new StoreChunk(this.server, packet.getData(), packet.getLength()));
				}
			}
		}
		
		
	}
	
	class MDRListener implements Runnable{
		private Server server;
		private volatile boolean running = true;
		
		public MDRListener(Server server){
			this.server = server;
		}

		public void terminate(){
			running = false;
		}
	
		@Override
		public void run() {

			while(running){
				byte buf[] = new byte[Server.MAX_BUFFER_SIZE];
				DatagramPacket packet = new DatagramPacket(buf,buf.length);
		
				try{
					MDRsocket.receive(packet);
				}
				catch(IOException e){
					if(running)
						System.err.println("Error receiving MDRsocket packet");
				}
				catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
					if(running)
						System.err.println("MDRsocket packet received is insecure: " + e);
				}

				if(running){
					pool.execute(new Chunk(this.server, packet.getData(), packet.getLength()));
				}
			}

		}
	}
}