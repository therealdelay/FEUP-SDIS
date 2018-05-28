 import java.rmi.Remote;
 import java.rmi.RemoteException;
 import java.io.*;
 import java.util.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
 
 public interface ServerInterf extends Remote {
	public String echo(String msg) throws RemoteException;
	public void backup(SecretKeySpec clientKey, String fileName, int repDegree) throws RemoteException, IOException, NoSuchAlgorithmException, NoSuchPaddingException;
	public void restore(SecretKeySpec clientKey, String fileName, int option) throws RemoteException, IOException, NoSuchAlgorithmException, NoSuchPaddingException;
	public void delete(SecretKeySpec clientKey, String fileName, int option) throws RemoteException, IOException, NoSuchAlgorithmException, NoSuchPaddingException;
	public String reclaim(SecretKeySpec clientKey, int mem) throws RemoteException, IOException, NoSuchAlgorithmException, NoSuchPaddingException;
	public String list(SecretKeySpec clientKey) throws RemoteException;
	public String state(SecretKeySpec clientKey) throws RemoteException;
	public void shutdown(SecretKeySpec clientKey) throws RemoteException;
	public ArrayList<String> showVersions(String fileName) throws RemoteException; 
}