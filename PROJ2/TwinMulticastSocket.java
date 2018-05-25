import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;


public class TwinMulticastSocket {
	
	private MulticastSocket in;
	private MulticastSocket out;
	private ReentrantLock lock;
	private int port;
	private InetAddress group;

	private SecretKeySpec secretKey;
	private Cipher cipher;
	
	public TwinMulticastSocket(String compName, byte[] key) throws IOException, UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException
	{
		String[] name = compName.split(":");
		
		this.group = InetAddress.getByName(name[0]);
		
		this.port = Integer.parseInt(name[1]);
	
		this.in = new MulticastSocket(this.port);
		this.out = new MulticastSocket(this.port);
		
		if(this.in == null)
			System.out.println("null socket inside inside");
		 
		this.in.joinGroup(this.group);
		this.out.joinGroup(this.group);
		
		this.lock = new ReentrantLock();

		this.secretKey = new SecretKeySpec(key, "AES");
		this.cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
	}
	
	public int getPort(){
		return this.port;
	}
	
	public InetAddress getGroup(){
		return this.group;
	}
	
	public void receive(DatagramPacket packet) throws IOException,InvalidKeyException,BadPaddingException,IllegalBlockSizeException
	{
		this.in.receive(packet);

		byte[] trimmed = trim(packet.getData());

		packet.setData(decryptPacket(trimmed));
	}

	static byte[] trim(byte[] bytes)
	{
		int i = bytes.length - 1;
		while (i >= 0 && bytes[i] == 0)
		{
			--i;
		}

		return Arrays.copyOf(bytes, i + 1);
	}
	
	public void send(DatagramPacket packet) throws IOException,InvalidKeyException,BadPaddingException,IllegalBlockSizeException
	{	

		packet.setData(encryptPacket(packet.getData()));

		try{
			this.lock.lock();
			this.out.setTimeToLive(1);
			this.out.send(packet);
		}
		finally{
			this.lock.unlock();
		}
	}

	public byte[] encryptPacket(byte[] packetData) throws IOException,InvalidKeyException,BadPaddingException,IllegalBlockSizeException
	{
		this.cipher.init(Cipher.ENCRYPT_MODE, this.secretKey);
		return this.cipher.doFinal(packetData);
	}

	public byte[] decryptPacket(byte[] packetData) throws IOException,InvalidKeyException,BadPaddingException,IllegalBlockSizeException
	{
		this.cipher.init(Cipher.DECRYPT_MODE, this.secretKey);
		return this.cipher.doFinal(packetData);
	}
	
	public void close(){
		this.in.close();
		this.out.close();
	}

}