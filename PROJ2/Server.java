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
		if(args.length != 6){
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
	}
	
	public static void printUsage(){
		String lineSep = System.lineSeparator();
		String doubleLineSep = lineSep+lineSep;
		String usage =  lineSep+
						"   Server <version> <id> <admin_id> <MC> <MDB> <MDR>"+doubleLineSep+
						"      version: version of the protocol with the format <n>.<m>"+doubleLineSep+
						"      id: server and rmi identifier"+doubleLineSep+
						"      MC,MDB,MDR: multicast channels with the format <ip>/<port>";
						
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
		this.createSWD(args);
		this.fileManager.setWDir(this.SWD);
		
		this.pool = new ThreadPoolExecutor(5,30,10,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(10));
		
		//Start multicast channels listener threads
		this.startListenerThreads();
		
		System.out.println("Server set up and running");
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
		
		System.exit(1);
	}
	
	public String echo(String msg){
		this.printRequest("ECHO "+msg);
		return msg;
	}
	
	public void backup(SecretKeySpec clientKey, String fileName, int repDegree) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException{
		this.printRequest("BACKUP "+fileName+" "+repDegree);
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
		// TODO: verify this
		String fileId = ServerFile.toId(fileName);
		Runnable handler = new RestoreProtocol(this, fileName, fileId, clientKey);
		this.requests.put("RESTORE"+ServerFile.toId(fileName), handler);
		this.pool.execute(handler);
	}
	
	public void delete(SecretKeySpec clientKey, String fileName) throws NoSuchPaddingException, NoSuchAlgorithmException{
		this.printRequest("DELETE "+fileName);
		this.pool.execute(new DeleteProtocol(this, fileName, clientKey));
	}
	
	public String reclaim(SecretKeySpec clientKey, int mem) throws NoSuchPaddingException, NoSuchAlgorithmException{
		String sA = new String(this.adminKey.getEncoded());
		String sB = new String(clientKey.getEncoded());
		String answer = "";
		if(!sA.equals(sB)){
			answer = "Authentication failed!";
		}
		else{
			this.printRequest("RECLAIM "+mem);
			this.pool.execute(new ReclaimProtocol(this, mem, clientKey));
			answer = "Reclaim processed.";
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

	public String state(){
		this.printRequest("STATE");
		return this.fileManager.toString();
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
	
	public ConcurrentHashMap<String,Runnable> getRequests(){
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
	
	class MCListener implements Runnable{
		private Server server;
		
		public MCListener(Server server){
			this.server = server;
		}
		
	
		@Override
		public void run() {
			while(true){
				byte buf[] = new byte[200];
				DatagramPacket packet = new DatagramPacket(buf,buf.length);
		
				try{
					MCsocket.receive(packet);
				}
				catch(IOException e){	
					System.err.println("Error receiving MCsocket packet");
				}
				catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
					System.err.println("MCsocket packet received is insecure: " + e);
				}
				
				
				System.out.println("Packet received at MCsocket: " + new String(packet.getData()).trim() + "\n");
				pool.execute(new ControlProtocol(this.server, packet.getData()));
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
				catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
					System.err.println("MDBsocket packet received is insecure: " + e);
				}
				
				pool.execute(new StoreChunk(this.server, packet.getData(), packet.getLength()));
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
				catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
					System.err.println("MDRsocket packet received is insecure: " + e);
				}
				
				pool.execute(new Chunk(this.server, packet.getData(), packet.getLength()));
			}
		}
	}
}