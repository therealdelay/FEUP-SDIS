 import java.rmi.Remote;
 import java.rmi.RemoteException;
 import java.io.*;
 
 public interface ServerInterf extends Remote {
	public String echo(String msg) throws RemoteException;
	public void backup(String fileName, int repDegree) throws RemoteException;
	public void restore(String fileName) throws RemoteException;
	public void delete(String fileName) throws RemoteException;
	public void reclaim(int mem) throws RemoteException;
	public String state() throws RemoteException; 
}