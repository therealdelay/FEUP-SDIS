import java.util.Timer;
import java.util.TimerTask;

public class MulticastThread extends TimerTask{

	private Server server;
	
	public MulticastThread(Server server){
		this.server = server;
	}
	
	public void run(){
		this.server.multicast();
		//System.out.println("Hello world");
		}	
	}