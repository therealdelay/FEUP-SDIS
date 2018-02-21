public class MulticastThread extends Thread{

	private Server server;
	
	public MulticastThread(Server server){
		this.server = server;
	}
	
	public void run(){
		/*
		while(true){
			this.server.multicast();
			
		*/
		
		System.out.println("Hello world");
		}	
	}