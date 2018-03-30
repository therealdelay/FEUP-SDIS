import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;


public class ServerFile{
	private String id;
	private ArrayList<Integer> chunksRepDeg;
	private int replicationDeg;
	
	public ServerFile(String id, int replicationDeg){
		this.id  = id;
		this.replicationDeg = replicationDeg;
		this.chunksRepDeg = new ArrayList<Integer>();
	}
	
	public String getId(){
		return this.id;
	}

	public int getReplicationDeg(){
		return this.replicationDeg;
	}

	public ArrayList<Integer> getChunksRepDeg(){
		return this.chunksRepDeg;
	}

	public void incChunksRepDeg(int index){
		this.chunksRepDeg.set(index,this.chunksRepDeg.get(index)+1);
	}

	public void decChunksRepDeg(int index){
		this.chunksRepDeg.set(index,this.chunksRepDeg.get(index)-1);
	}
	
	public String toString(){
		return this.id+":"+this.replicationDeg;
	}
}