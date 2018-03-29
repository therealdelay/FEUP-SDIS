/*
import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
*/

public class ServerChunk{
	private String id;
	//private int replicationDeg;
	private long size;
	
	public ServerChunk(String id,/* int replicationDeg,*/ long size){
		this.id  = id;
		// this.replicationDeg = replicationDeg;
		this.size = size;
	}
	
	public String getId(){
		return this.id;
	}
	
	public long getSize(){
		return this.size;
	}
/*
	public int getReplicationsDeg(){
		return replicationDeg;
	}
	
	public String toString(){
		return this.id+":"+this.replicationDeg;
	}*/
	public String toString(){
		return this.id+":"+this.size;
	}
}