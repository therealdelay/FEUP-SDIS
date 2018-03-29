import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.*;
import java.io.*;
import java.net.*;

public class TestApp {
	private String url;
	private String command;
	private String[] args;
	private ServerInterf proxy;
	
	private final static int BUF_SIZE = 1024 * 64;

	public static void main(String[] args){
		int argsNr = args.length;
		if(argsNr < 2){
			TestApp.printUsage();
			return;
		}
		
		TestApp testApp = new TestApp(args);
		testApp.connect();
		testApp.processRequest();
	}

	public TestApp(String[] args){
		
		this.url = "rmi://"+args[0];
		this.command = args[1].toUpperCase();;
		
		int argsLength = args.length-2;
		if(argsLength > 0){
			this.args = new String[argsLength];
			System.arraycopy(args, 2, this.args, 0, argsLength);
		}
	}
	
	private static void printUsage(){
		System.out.println("Wrong number of arguments");
	}
	
	public void connect(){
		try{
			this.proxy = (ServerInterf) Naming.lookup(this.url);
		}
		catch(Exception e){
			System.err.println("Unable to connect to server");
			System.exit(1);
		}
	}
	
	 public void copy(InputStream in, OutputStream out) throws IOException {
        System.out.println("using byte[] read/write");
        byte[] b = new byte[BUF_SIZE];
        int len;
        while ((len = in.read(b)) >= 0) {
			//System.out.println(len);
            out.write(b, 0, len);
        }
        in.close();
        out.close();
    }
	
	
	private void echo(){
		String echoMsg;
		String msg = String.join(" ",this.args);
		try{
			echoMsg = this.proxy.echo(msg);
			System.out.println("Msg from server: "+echoMsg);
		}
		catch(Exception e){
			System.err.println("Failed to send: ECHO");
		}
	}
	
	private void backup(){
		System.out.println("Processing backup...");
		
		FileInputStream inStream = null;
		
		try{
			inStream = new FileInputStream(new File(this.args[0]));
		}
		catch(Exception e){
			System.err.println("File not found");
			return;
		}
		
		try{
			this.copy(inStream, this.proxy.getOutputStream(this.args[0]));
			this.proxy.backup(this.args[0], Integer.parseInt(this.args[1]));
		}
		catch(Exception e){
			System.err.println("Failed to send: BACKUP");
			e.printStackTrace();
		}
	}
	
	private void restore(){
		System.out.println("Processing restore...");
		try{
			InputStream inStream = this.proxy.restore(this.args[0]);
			copy(inStream, new FileOutputStream(new File(this.args[0])));
		}
		catch(Exception e){
			System.err.println("Failed to restore");
			e.printStackTrace();
		}
	}
	
	private void delete(){
		System.out.println("Processing restore...");
		try{
			this.proxy.delete(this.args[0]);
		}
		catch(Exception e){
			System.err.println("Failed to delete");
		}
	}
	
	private void reclaim(){
		System.out.println("Processing reclaim...");
		try{
			this.proxy.delete(this.args[0]);
		}
		catch(Exception e){
			System.err.println("Failed to reclaim");
		}
	}
	
	private void state(){
		System.out.println("Processing state...");
		try{
			String state = this.proxy.state();
			System.out.println(state);
		}
		catch(Exception e){
			System.err.println("Failed to state");
		}
	}
	
	private void processRequest(){
		
		switch(this.command){		
			case "ECHO":
				this.echo();
				break;
				
			case "BACKUP":
			    this.backup();
				break;
				
			case "RESTORE":
			    this.restore();
				break;
				
			case "DELETE":
			    this.delete();
				break;
				
			case "RECLAIM":
				this.reclaim();
				break;
				
			case "STATE":
				this.state();
				break;
			
			/*
			case "EXIT":
				this.disconnect();
				this.pool.shutdownNow();
				System.exit(0);
				break;
			*/
				
			default:
				System.out.println("Request not recognized");
				break;
		}
	}
}