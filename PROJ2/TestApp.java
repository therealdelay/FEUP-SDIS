import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.*;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.*;
import java.util.Scanner;

import javax.crypto.spec.SecretKeySpec;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class TestApp {
	private String url;
	private String command;
	private String[] args;
	private ServerInterf proxy;

	private static SecretKeySpec clientKey;
	
	private final static int BUF_SIZE = 1024 * 64;

	public static void main(String[] args){
		int argsNr = args.length;
		if(argsNr < 2){
			TestApp.printUsage();
			return;
		}
		
		TestApp testApp = new TestApp(args);
		testApp.processRequest();
	}

	public TestApp(String[] args){
		
		this.url = "rmi://"+args[0];
		this.command = args[2].toUpperCase();
		
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			byte[] key = sha.digest(args[1].getBytes("UTF-8"));
			key = Arrays.copyOf(key, 16);

			clientKey = new SecretKeySpec(key, "AES");

		} catch(NoSuchAlgorithmException | UnsupportedEncodingException e){
			System.err.println("Error creating client key");
		}
		
		int argsLength = args.length-3;
		if(argsLength > 0){
			this.args = new String[argsLength];
			System.arraycopy(args, 3, this.args, 0, argsLength);
		}
		else
			this.args = new String[0];
	}
	
	private static void printUsage(){
		String lineSep = System.lineSeparator();
		String doubleLineSep = lineSep+lineSep;
		String usage =  lineSep+
					    "    TestApp <peer_ap> <sub_protocol> [opnd_1] [opnd_2]"+doubleLineSep+
						"      peer_ap: peer's access point in RMI format"+doubleLineSep+
						"      sub_protocol: the operation the target peer service must execute, may be BACKUP, RESTORE, RECLAIM, STATE"+doubleLineSep+
						"      opnd_1: if BACKUP/RESTORE, represents the relative or absolute file name"+lineSep+
						"              if RECLAIM, represents the amount of used memory allowed to be in the target peer"+doubleLineSep+
						"      opnd_2: if BACKUP, represents the desired replication degree in the system";
		System.out.println(usage);
	}
	
	private void connect(){
		try{
			this.proxy = (ServerInterf) Naming.lookup(this.url);
		}
		catch(Exception e){
			System.err.println("Unable to connect to server");
			System.exit(1);
		}
	}
	
	private void echo(){
		
		this.connect();
		
		String echoMsg;
		String msg = String.join(" ",this.args);
		System.out.println("Echoing message "+msg);
		try{
			echoMsg = this.proxy.echo(msg);
			System.out.println("Msg from server: "+echoMsg);
		}
		catch(Exception e){
			System.err.println("Failed echo");
		}
	}
	
	private boolean checkBackUpArgs(){
		if(this.args.length != 2){
			System.err.println("Wrong number or arguments for BACKUP");
			return false;
		}
			
		if(!this.args[1].matches("^[1-9][0-9]*")){
			System.err.println("Replication degree is not a valid number, must be greater than 0");
			return false;
		}
		
		return true;
	}
	
	private void backup(){
		if(!this.checkBackUpArgs())
			return;
		
		this.connect();
		
		System.out.println("Backing up file " + this.args[0] + " with replication degree of " + this.args[1] + ".");
		try{
			this.proxy.backup(clientKey, args[0], Integer.parseInt(this.args[1]));
		}
		catch(Exception e){
			System.err.println("Failed to send: BACKUP");
			e.printStackTrace();
		}
		System.out.println("Backup processed.");
	}
	
	private boolean checkRestoreArgs(){
		if(this.args.length != 1){
			System.err.println("Wrong number or arguments for RESTORE");
			return false;
		}
		
		return true;
	}
	
	private void restore(){
		if(!this.checkRestoreArgs())
			return;
		
		this.connect();

		ArrayList<String> versions = new ArrayList<String>();

		try{
			versions = this.proxy.showVersions(this.args[0]);
		}
		catch (Exception e) {
			System.err.println("Failed to delete");
		}

		int intOption = 0;

		System.out.println(versions.size());
		if(versions.size() > 0){
			System.out.println("Previous versions of file " + this.args[0]);
			System.out.println("Version | Date");
			System.out.println("------------------------------");
			for(int i=0; i < versions.size(); i++){
				System.out.printf("%-7d | %-30s", i+1, versions.get(i));
				System.out.println();
			}

			System.out.println("Which version do you want to restore?");
			System.out.print("> ");

			Scanner reader = new Scanner(System.in);
			intOption = reader.nextInt();
			
			System.out.println("Restore version " + intOption);
			
		}		
		System.out.println("Restoring file " + this.args[0] + ".");
		try{
			String lastModified = new String();
			if(intOption >= 1)
				lastModified = versions.get(intOption-1);
			else
				lastModified = "";
			this.proxy.restore(clientKey, this.args[0], intOption, lastModified);
		}
		catch(Exception e){
			System.err.println("Failed to restore");
			e.printStackTrace();
		}
		System.out.println("Restore processed.");
	}
	
	private boolean checkDeleteArgs(){
		if(this.args.length != 1){
			System.err.println("Wrong number or arguments for DELETE");
			return false;
		}
		
		return true;
	}
	private void delete(){
		if(!this.checkDeleteArgs())
			return;
		
		this.connect();

		ArrayList<String> versions = new ArrayList<String>();

		try{
			versions = this.proxy.showVersions(this.args[0]);
		}
		catch (Exception e) {
			System.err.println("Failed to delete");
		}

		int intOption = 0;

		System.out.println(versions.size());
		if(versions.size() > 0){
			System.out.println("Previous versions of file " + this.args[0]);
			System.out.println("Version | Date");
			System.out.println("------------------------------");
			for(int i=0; i < versions.size(); i++){
				System.out.printf("%-7d | %-30s", i+1, versions.get(i));
				System.out.println();
			}

			System.out.println("Which version do you want to delete?");
			System.out.print("> ");

			Scanner reader = new Scanner(System.in);
			intOption = reader.nextInt();
			if(intOption <= 0 || intOption >= versions.size()+1){
				System.out.println("Invalid option. Exiting...");
				return;
			}
			else{			
				System.out.println("Delete version " + intOption);
			}
		}
		
		System.out.println("Deleting file " + this.args[0] + " - version " + intOption + ".");
		try{
			String lastModified = new String();
			if(intOption >= 1)
				lastModified = versions.get(intOption-1);
			else
				lastModified = "";
			this.proxy.delete(clientKey, this.args[0], intOption, lastModified);
		}
		catch(Exception e){
			System.err.println("Failed to delete");
		}
		System.out.println("Delete processed.");
	}

	
	private boolean checkReclaimArgs(){
		if(this.args.length != 1){
			System.err.println("Wrong number or arguments for RECLAIM");
			return false;
		}
			
		if(!this.args[0].matches("^\\d+")){
			System.err.println("Max memory is not a valid number, must be positive");
			return false;
		}
		
		return true;
	}
	
	private void reclaim(){
		if(!this.checkReclaimArgs())
			return;
		
		this.connect();
		String answer = "";
		
		int mem = 8*1024 - Integer.parseInt(this.args[0]);
		System.out.println("Reclaiming " + mem + "KBytes of disk space.");
		try{
			answer = this.proxy.reclaim(clientKey, Integer.parseInt(this.args[0]));
		}
		catch(Exception e){
			System.err.println("Failed to reclaim");
		}
		System.out.println(answer);
	}

	private void list(){

		this.connect();
		
		System.out.println("Retrieving user files");
		try{
			String list = this.proxy.list(clientKey);
			System.out.println(list);
		}
		catch(Exception e){
			System.err.println("Failed to list");
		}
	}

	private void state(){
		this.connect();
		
		System.out.println("Retrieving peer state");
		try{
			String state = this.proxy.state(this.clientKey);
			System.out.println(state);
		}
		catch(Exception e){
			System.err.println("Failed to state");
		}
	}

	private void shutdown(){
		this.connect();
		
		System.out.println("Shutting down");
		try{
			this.proxy.shutdown(this.clientKey);
		}
		catch(Exception e){
			//System.err.println("Failed to shutdown");
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

			case "LIST":
				this.list();
				break;
				
			case "STATE":
				this.state();
				break;
			
			case "SHUTDOWN":
				this.shutdown();
				break;
				
			default:
				System.out.println("Request not recognized");
				break;
		}
	}
}