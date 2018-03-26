/*
import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
*/

public class ServerFile{
	private String id;
	private int replicationDeg;
	
	public ServerFile(String id, int replicationDeg){
		this.id  = id;
		this.replicationDeg = replicationDeg;
	}
	
	public String getId(){
		return this.id;
	}
	
	public int getReplicationsDeg(){
		return replicationDeg;
	}
	
	public String toString(){
		return this.id+":"+this.replicationDeg;
	}
}