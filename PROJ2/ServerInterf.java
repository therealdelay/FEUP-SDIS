 import java.rmi.Remote;
 import java.rmi.RemoteException;
 import java.io.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.NoSuchPaddingException;
 
 public interface ServerInterf extends Remote {
	public String echo(String msg) throws RemoteException;
	public void backup(byte[] clientKey, String fileName, int repDegree) throws RemoteException, IOException, NoSuchAlgorithmException, NoSuchPaddingException;
	public void restore(byte[] clientKey, String fileName) throws RemoteException, IOException, NoSuchAlgorithmException, NoSuchPaddingException;
	public void delete(byte[] clientKey, String fileName) throws RemoteException, IOException, NoSuchAlgorithmException, NoSuchPaddingException;
	public void reclaim(byte[] clientKey, int mem) throws RemoteException, IOException, NoSuchAlgorithmException, NoSuchPaddingException;
	public String state() throws RemoteException; 
}