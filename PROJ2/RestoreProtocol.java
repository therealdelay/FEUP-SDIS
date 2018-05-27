import java.io.*;
import java.nio.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.io.DataInputStream;

import java.security.InvalidKeyException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class RestoreProtocol implements Runnable {

	private final static int TIMEOUT = 1000;

	private Server server;
	private String fileName;
	private String fileId;
	private int currChunk = 0;
	private boolean received = false;
	private byte[] buf;
	private ReentrantLock lock;

	private SecretKeySpec secretKey;
	private Cipher cipher;

	private FileOutputStream outStream;

	private byte[] totalFile;

	public RestoreProtocol(Server server, String fileName, String fileId, SecretKeySpec clientKey)
			throws IOException, UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException {
		this.server = server;
		this.fileName = fileName;
		this.fileId = fileId;
		this.lock = new ReentrantLock();

		this.secretKey = clientKey;
		this.cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
	}

	@Override
	public void run() {
		FileManager fileManager = this.server.getFileManager();
		int totalChunks = fileManager.getFileTotalChunks(this.fileName);
		System.out.println("Total Chunks: " + totalChunks);
		outStream = fileManager.getOutStream(ServerFile.toRelativeName(this.fileName));
		
		//setOutputStream(fileManager);
		
		Socket connectionSocket;

		for (int i = 0; i < totalChunks; i++) {
			this.sendGetChunkMsg();
			++currChunk;
			try {
				connectionSocket = this.server.getTCPSocket().accept();
			} catch (IOException e) {
				System.out.println("Error on accepting TCP Socket");
				return;
			}

			if (!this.receiveChunk(connectionSocket)) {
				this.exit_err("Unable to restore chunk " + this.currChunk + " of file " + this.fileName);
				return;
			}
			try {
				connectionSocket.close();
			} catch (IOException e) {
				System.out.println("Error on closing TCP Socket.");
				return;
			}
			
		}
		
		try {
			outStream.write(totalFile);
		} catch (IOException e) {
			System.out.println("Error writing to file : " + e);
		}

		this.exit();
	}

	private boolean receiveChunk(Socket connectionSocket) {
		try {
			DataInputStream in = new DataInputStream(new BufferedInputStream(connectionSocket.getInputStream()));

			int count;
			byte[] buffer = new byte[Server.MAX_CHUNK_SIZE_ENCRYPTED];
			byte[] total = null;
			while ((count = in.read(buffer)) > 0) {
				byte[] tmp = new byte[count];
				System.arraycopy(buffer, 0, tmp, 0, count);
			
				byte[] decrypted = decryptBody(tmp);

				if (totalFile != null) {
					total = new byte[decrypted.length + totalFile.length];

					System.arraycopy(totalFile, 0, total, 0, totalFile.length);
					System.arraycopy(decrypted, 0, total, totalFile.length, decrypted.length);

					totalFile = total;
				} else {
					totalFile = decrypted;
				}
			}
			
			in.close();

		} catch (IOException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
			System.out.println("Error on reading from socket. " + e);
			return false;
		}

		this.received = false;
		return true;
	}

	private void sendGetChunkMsg() {
		System.out.println("\nSending Get Chunk for chunk nr " + this.currChunk+ "\n");
		String msg = this.getGetChunkMsg();
		TwinMulticastSocket socket = this.server.getMCsocket();
		DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), socket.getGroup(), socket.getPort());

		// Send msg
		try {
			socket.send(packet);
		} catch (IOException e) {
			this.printErrMsg("Unable to send STORED message");
		} catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
			System.err.println("MCsocket packet received is insecure");
		}
	}

	private String getGetChunkMsg() {
		return "GETCHUNK " + this.server.getVersion() + " " + this.server.getId() + " "
				+ ServerFile.toEncryptedId(this.fileName, this.secretKey) + " " + this.fileId + " " + this.currChunk
				+ " " + this.server.getAddress() + " " + this.server.getPort();
	}

	private void removeRequest() {
		ConcurrentHashMap<String, Runnable> requests = this.server.getRequests();
		requests.remove("RESTORE" + this.fileId);
	}

	private void exit() {
		try {
			this.outStream.close();
		} catch (IOException e) {
			this.printErrMsg("Unable to close input stream");
		}
		System.out.println("File " + this.fileName + " restored up with success!");

		this.removeRequest();
	}

	private void exit_err(String err) {
		this.printErrMsg(err);
		try {
			if (this.outStream != null)
				this.outStream.close();
		} catch (IOException e) {
			this.printErrMsg("Unable to close output stream");
		}

		this.server.getFileManager().deleteSWDFile(ServerFile.toRelativeName(this.fileName));
		this.removeRequest();
	}

	private void printErrMsg(String err) {
		System.err.println("Error restoring file " + this.fileName + ": " + err);
	}

	public byte[] decryptBody(byte[] body)
			throws IOException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
		
		System.out.println("DECRYPT: "+ body.length);
		this.cipher.init(Cipher.DECRYPT_MODE, this.secretKey);
		return this.cipher.doFinal(body);
	}
}