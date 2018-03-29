import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.Remote;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.net.*;
import java.io.*;

public class RMIOutputStream extends OutputStream implements Serializable{
	
	private RMIOutputStreamInterf out;
	
	public interface RMIOutputStreamInterf extends Remote {
    
		public void write(int b) throws IOException, RemoteException;
		public void write(byte[] b, int off, int len) throws IOException, RemoteException;
		public void close() throws IOException, RemoteException;
	}
	
	
	public static class RMIOutputStreamImpl implements RMIOutputStreamInterf {
		
		private OutputStream out;
    
		public RMIOutputStreamImpl(OutputStream out) throws 
            IOException {
			this.out = out;
			UnicastRemoteObject.exportObject(this, 1099);
		}
    
		public void write(int b) throws IOException {
			out.write(b);
		}

		public void write(byte[] b, int off, int len) throws IOException {
			out.write(b, off, len);
		}

		public void close() throws IOException {
			out.close();
		}

	}
	
	
	public RMIOutputStream(RMIOutputStreamImpl out) {
        this.out = out;
    }
    
    public void write(int b) throws IOException {
        out.write(b);
    }

    public void write(byte[] b, int off, int len) throws 
            IOException {
        out.write(b, off, len);
    }
    
    public void close() throws IOException {
        out.close();
    }
}