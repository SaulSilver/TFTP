import sun.plugin.javascript.navig.Array;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class TFTPServer {
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "F:\\Computer Networks\\Assignment 3\\read\\";
	public static final String WRITEDIR = "F:\\Computer Networks\\Assignment 3\\write\\";
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final short OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	public static void main(String[] args) {
		if (args.length > 0) {
			System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		try {
			TFTPServer server = new TFTPServer();
			server.start();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	private void start() throws SocketException {
		byte[] buf = new byte[BUFSIZE];
		
		/* Create socket */
		DatagramSocket socket = new DatagramSocket(null);
		
		/* Create local bind point */
		SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

		short counter = 1;
		while (true) {        /* Loop to handle various requests */
			final InetSocketAddress clientAddress = receiveFrom(socket, buf);

			if (clientAddress == null) /* If clientAddress is null, an error occurred in receiveFrom()*/
				continue;

			final StringBuffer requestedFile = new StringBuffer();
			final int requestType = ParseRQ(buf, requestedFile, counter);

			System.out.println("File name: " + requestedFile.toString());

			new Thread() {
				public void run() {
					try {
						DatagramSocket sendSocket = new DatagramSocket(0);
						boolean terminationFlag = false;

						System.out.printf("%s request for %s from using port %s\n", (requestType == OP_RRQ) ? "Read" : "Write", clientAddress.getHostName(), clientAddress.getPort());

						if (requestType == OP_RRQ) {      /* read request */
							requestedFile.insert(0, READDIR);
							terminationFlag = HandleRQ(sendSocket, clientAddress, requestedFile.toString(), OP_RRQ, counter);
						} else {                       /* write request */
							requestedFile.insert(0, WRITEDIR);
							terminationFlag = HandleRQ(sendSocket, clientAddress, requestedFile.toString(), OP_WRQ, counter);
						}

						receiveFrom(sendSocket, buf);
						ParseACK(buf, counter);

						if(terminationFlag == true)			//To check if the last packet was sent and termination permission is given
							sendSocket.close();
					} catch (SocketException e) {
						e.printStackTrace();
					}
				}
			}.start();
		}
	}

	/**
	 * Reads the first block of data, i.e., the request for action (read or write).
	 *
	 * @param socket socket to read from
	 * @param buf    where to store the read data
	 * @return the Internet socket address of the client
	 */

	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
		DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
		try {
			socket.receive(receivePacket);
			System.out.println("Received Packet: " + new String(receivePacket.getData()));
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (receivePacket.getPort() >= 0) {
			InetSocketAddress remoteBindPoint = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
			return remoteBindPoint;
		} else return null;
	}

	private int ParseRQ(byte[] buf, StringBuffer requestedFile, int blockCounter) {

		ByteBuffer wrap = ByteBuffer.wrap(buf);
		short opcode = wrap.getShort();

		if (opcode == OP_RRQ) {
			String request = new String(buf, 2, buf.length - 2);
			String[] requestArray = request.split("\u0000");    //Splits the request string at '0' byte
			String fileName = requestArray[0];
			String mode = requestArray[1];

			if (!mode.equals("octet"))            //Checks if the mode is not Octet
				throw new IllegalArgumentException("This server handles Octet mode only.");

			requestedFile.insert(0, fileName);
		}
		return opcode;
	}

	private boolean HandleRQ(DatagramSocket sendSocket, InetSocketAddress client, String requestedFile, int opRrq, short blockCounter) {
		if (opRrq == OP_RRQ) {
			byte[] buf = new byte[BUFSIZE];
			short opCodeData = OP_DAT;        //assigning op code DATA

			ByteBuffer wrap = ByteBuffer.wrap(buf);
			wrap.putShort(opCodeData);                    //Adding the
			wrap.putShort(blockCounter);                //OP code and Block # to the wrap

			Path filePath = Paths.get(requestedFile);    //the file path
			try {
				byte[] fileContents = Files.readAllBytes(filePath);    //reading from the file requested
				wrap.put(fileContents);            //putting the read contents into the wrap
				buf = wrap.array();                                //converting the 'wrap' back to a byte array 'buf'

				System.out.println(new String(buf));

				DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, client);

				sendSocket.send(sendPacket);
				blockCounter++;                    //incrementing the Block #
				System.out.println("Packet sent");
				if (fileContents.length < 512)                        //To check if this is the last block and terminate the connection
					return true;
			} catch (NoSuchFileException e) {
				System.err.println("File not found");
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (opRrq == OP_WRQ) {

		}
		return false;
	}

	/**
	 * a method to accept ACK requests sent by client, and correct the block # in case of retransmission
	 * @param buf: to read the request from
	 * @param blockCounter: the Block #
	 */
	private void ParseACK(byte[] buf, int blockCounter) {
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		short opcode = wrap.getShort();
		if (opcode == OP_ACK) {
			int blockNumber = wrap.getShort(2);
			if (blockCounter != blockNumber)			//If the previous packet was lost and needs to be resent
				blockCounter = blockNumber;
		} else throw new IllegalArgumentException("ACKNOWLEDGMENT is expected");
	}
}



