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
		try{
			this.proxy.backup(this.args[0], Integer.parseInt(this.args[1]));
		}
		catch(Exception e){
			System.err.println("Failed to send: BACKUP");
		}
	}
	
	private void restore(){
		System.out.println("Processing restore...");
		try{
			this.proxy.restore(this.args[0]);
		}
		catch(Exception e){
			System.err.println("Failed to send: RESTORE");
		}
	}
	
	private void delete(){
		System.out.println("Processing restore...");
		try{
			this.proxy.delete(this.args[0]);
		}
		catch(Exception e){
			System.err.println("Failed to request: DELETE");
		}
	}
	
	private void reclaim(){
		System.out.println("Processing reclaim...");
		try{
			this.proxy.delete(this.args[0]);
		}
		catch(Exception e){
			System.err.println("Failed to request: DELETE");
		}
	}
	
	private void state(){
		System.out.println("Processing reclaim...");
		try{
			String state = this.proxy.state();
			System.out.println(state);
		}
		catch(Exception e){
			System.err.println("Failed to request: DELETE");
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