import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.net.*;
import java.io.*;

public class RMIInputStream extends InputStream implements Serializable{
	
	private RMIInputStreamInterf in;
	
	public interface RMIInputStreamInterf extends Remote {
		public byte[] readBytes(int len) throws IOException, RemoteException;
		public int read() throws IOException, RemoteException;
		public void close() throws IOException, RemoteException;
	}
	
	
	public static class RMIInputStreamImpl implements RMIInputStreamInterf {

		private InputStream in;
		private byte[] b;

		public RMIInputStreamImpl(InputStream in) throws IOException {
			this.in = in;
			UnicastRemoteObject.exportObject(this, 1099);
		}

		public void close() throws IOException, RemoteException {
			in.close();
		}	

		public int read() throws IOException, RemoteException {
			return in.read();
		}

		public byte[] readBytes(int len) throws IOException, RemoteException {
			if (b == null || b.length != len)
				b = new byte[len];
            
			int len2 = in.read(b);
			if (len2 == -1)
				return null; // EOF reached
        
			if (len2 != len) {
				// copy bytes to byte[] of correct length and return it
				byte[] b2 = new byte[len2];
				if(len2 != 0)
					System.arraycopy(b, 0, b2, 0, len2);
				return b2;
			}
			else
				return b;
		}
		
	}
	
	public RMIInputStream(RMIInputStreamInterf in) {
        this.in = in;
    }
    
    public int read() throws IOException {
        return in.read();
    }

    public int read(byte[] b, int off, int len) throws IOException {
        byte[] b2 = in.readBytes(len);
        if (b2 == null)
            return -1;
        int i = b2.length;
        System.arraycopy(b2, 0, b, off, i);
        return i;
    }
    
    public void close() throws IOException {
		in.close();
    }
}