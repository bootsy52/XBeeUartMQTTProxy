/*
 	This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
    
    Author: Carsten Menke on behalf of P-i-U UG & Co. KG
    
    following are the original Notes on which this program is based on
    
	This example is from _Java Examples in a Nutshell_. (http://www.oreilly.com)
	Copyright (c) 1997 by David Flanagan
	This example is provided WITHOUT ANY WARRANTY either expressed or implied.
	You may study, use, modify, and distribute it for non-commercial purposes.
	For any commercial use, see http://www.davidflanagan.com/javaexamples
*/

package de.piu.uart;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.XBeeDevice;
import com.digi.xbee.api.models.XBeeMessage;

/**
 * This class implements a simple single-threaded proxy server.
 **/
abstract class SocketThread extends Thread {
	public Socket socket = null;
	public SocketThread(Socket socket) {
		this.socket = socket;
	}
	
	public void run() {		
	}

} 
public class UARTProxyGateway {
	private static boolean debug = false;
	private static XBeeController xbee = null;
	private static XBeeDevice xbeeDevice = null;
	private static String host = "localhost";
	private static int remotePort = 1883;
	private static final int SO_TIMEOUT = 25000;
	private static final int BUFFER_SIZE = 512;
	private static String uartDevice = "/dev/ttyS4";
	private static final int UART_TIMEOUT = 2000;
	private static final int UART_BAUD_RATE = 9600;
	private static final int HEADER_OFFSET = UARTProxyUtil.HEADER_OFFSET;
	private static final byte[] DISCONNECT = UARTProxyUtil.DISCONNECT;
	private static ConcurrentHashMap<String, XBeeQueue> queue = new ConcurrentHashMap<String, XBeeQueue>();
	/** The main method parses arguments and passes them to runServer */
	public static void main(String[] args) throws IOException {
		try {
			// Check the number of arguments
			if (args.length < 3) 
				throw new IllegalArgumentException("Wrong number of arguments.");

			// Get the command-line arguments: the host and port we are proxy for
			// and the local port that we listen for connections on
			uartDevice = args[0];
			host = args[1];
			remotePort = Integer.parseInt(args[2]);
			if (args.length > 3) {
				if (args[3].equals("-v")) {
					debug = true;
				}
			}
			// Print a start-up message
			System.out.println("Starting proxy for " + host + ":" + remotePort + " using UART device: " + uartDevice);
			xbeeDevice = XBeeController.init(uartDevice, UART_BAUD_RATE, UART_TIMEOUT);
			xbee = new XBeeController(xbeeDevice, null, debug);
			// And start running the server
			reader(host, remotePort);
			worker();
		} 
		catch (Exception e) {
			System.err.println(e);
			System.err.println("Usage: java UARTProxyGateway <UART device> <host> <remoteport>");
			System.err.println("or");
			System.err.println("Usage: java UARTProxyGateway <UART device> <host> <remoteport> -v");
		}
	}
/*
 * the reader threat handles responses from the MQTT Server on the IP Socket
 */
	public static void reader(String host, int remoteport) throws IOException {
		 Thread readRemote = new Thread() {
			@Override
			public void run() {
				try {
					while (true) {
						XBeeMessage message = xbee.receiveMessage();
						XBeeQueue queueItem;
						byte[] data = null;
						int seqNumber = 0;
						if (message != null) {
							debugMsg("Queue Remote");
							String macAddress = String.valueOf(message.getDevice().get64BitAddress());
							// check if an object is associated to the sending macAddress
							queueItem = queue.get(macAddress);
							data = message.getData();
							seqNumber = UARTProxyUtil.decodeSequenceHeader(data);
							debugMsg("Sequence Number is: " + seqNumber);
							// we just need to synchronize if the object exists
							if (queueItem != null) {
								synchronized(queueItem) {
									queueItem.lastReceived = System.currentTimeMillis();
									// 0 is the starting sequence Number so clear all out all remaining artifacts
									// as a new connection has begun, though all old data is irrelevant now as the connection is gone
									// on the other side of the end
									if (seqNumber == 0) {
										queueItem.dataOutBuffer.clear();
									}
									queueItem.dataOutBuffer.put(seqNumber, data);
									queueItem.currentRemoteSeqNumber = seqNumber;
									queue.put(macAddress, queueItem);
									continue; 
								}
							}
							// There is no active object associated to the remoteMacAddress
							// instantiate a new object and initialize initial values
							queueItem = new XBeeQueue();
							queueItem.lastReceived = System.currentTimeMillis();
							queueItem.remoteDevice = message.getDevice();
							queueItem.dataOutBuffer.put(seqNumber, data);
							queueItem.currentRemoteSeqNumber = seqNumber;
							try {
								queueItem.server = new Socket(host, remoteport);
								queueItem.server.setSoTimeout(SO_TIMEOUT);
							} catch (IOException e) {
								debugMsg("Proxy server cannot connect to " + host + ":" + remoteport + ":\n" + e);
								synchronized(queueItem) {
									xbee.send(queueItem.remoteDevice, DISCONNECT, DISCONNECT.length);
								}
								continue;
							}
						  queue.put(macAddress, queueItem);
						}	

					}
				} catch (IOException e) {
					debugMsg(e);
				}
			}
		 };
		// Start the client-to-server request thread running
		readRemote.start(); 
	}
/*
 * The worker threat is the workhorse which does the actual processing of the queue and hands
 * off to the corresponding write() and read() threats
 */
	public static void worker() {
		String macAddress;
		XBeeQueue queueItem;
		while (true) {
			for (Map.Entry<String, XBeeQueue> entry : queue.entrySet()) {
				macAddress = entry.getKey();
				queueItem = entry.getValue();
				try {
					synchronized(queueItem) {
						// Server connection did never happen,
						// so just remove it from the queue nothing more is needed
						if (queueItem.server == null) {
							debugMsg("Server is null");
							queue.remove(macAddress);
							continue;
						}
						if (!queueItem.server.isConnected()) {
							queue.remove(macAddress);
							queueItem.server.close();
							continue;
						}
						if (queueItem.server.isClosed()) {
							queue.remove(macAddress);
							continue;
						}
						// Remove stale connections
						if (queueItem.lastReceived < (System.currentTimeMillis() - SO_TIMEOUT)) {
							queue.remove(macAddress);
							continue;
						}
						// Check if the current message received with current sequence number is a client disconnect packet
						// Then we can cancel out all pending action but the write actions (as this may be the last valid write operation)
						// due to that the client has gone away and won't receive any further messages from us
						if (UARTProxyUtil.isClientDisconnected(queueItem.dataOutBuffer.get(queueItem.currentRemoteSeqNumber))) {
							debugMsg("Received disconnect");
							queue.remove(macAddress);
							// if there are not outstanding write operations
							// close socket to free it up as early as possible
							// as we will not receive any more data from the client
							if (queueItem.isSocketWriting == false) {
								queueItem.server.close();
								continue;
							}
						}
						// Inspect if the nextWriteableSeqNumber 
						// a) does actually exists and 
						// b) has any valid data, otherwise we needn't to call the write() Thread
						if (queueItem.isSocketWriting == false 
								&& queueItem.dataOutBuffer.get(queueItem.nextWriteableSeqNumber) != null 
								&& queueItem.dataOutBuffer.get(queueItem.nextWriteableSeqNumber).length > HEADER_OFFSET) 
						{
							queueItem.isSocketWriting = true;
							writeLocal(macAddress, queueItem);
						}
						if (queueItem.isSocketReading == false) {
							queueItem.isSocketReading = true;
							readLocal(macAddress, queueItem);
						}
					}
					
				} // catch (InterruptedException e) {} 
				catch (Exception e) {
					debugMsg(e);
				}
			}
		}
	}
	public static void writeLocal(String macAddress, XBeeQueue queueItem) {
		Thread writeLocal = new Thread() {
			public void run() {
				Socket socket = null;
				byte[] data = null;
				RemoteXBeeDevice remoteDevice = null;
				ConcurrentHashMap<Integer, byte[]> dataOutBuffer = null;
				int nextWriteableSeqNumber = 0;
				int currentSeqNumber = 0;
				// copy the objects to local variables, as we just hold the lock on queueItem
				// and want to release it as soon as possible (dataOut and data are primitive types,
				// setting queueItem.dataOut = null works as it is not a reference to an object
				synchronized(queueItem) {
					dataOutBuffer = queueItem.dataOutBuffer;
					nextWriteableSeqNumber = queueItem.nextWriteableSeqNumber;
					currentSeqNumber = queueItem.currentRemoteSeqNumber;
					socket = queueItem.server;
					remoteDevice = queueItem.remoteDevice;
				}
				OutputStream sendLocal = null;
				try {
					for (int i = nextWriteableSeqNumber; i <= currentSeqNumber; i++) {
						// remove returns the value associated to the key or null
						data = dataOutBuffer.remove(i);
						nextWriteableSeqNumber = i;
						if (data != null) {
							debugMsg("Writing data for sequence Number: " + nextWriteableSeqNumber + " first byte: " + (data[HEADER_OFFSET] & 0xFF));
							sendLocal = socket.getOutputStream();
							sendLocal.write(data, HEADER_OFFSET, (data.length - HEADER_OFFSET));
							sendLocal.flush();
						} else {
							debugMsg("Missing Sequence Number: " + i);
							// TODO Maybe insert check for containsKey() as it is uncertain if the value of
							// get(i) is null or the key is not found
							break;
						}
					}
					nextWriteableSeqNumber++;
					debugMsg("Wrote local, Next Sequence Number: " + nextWriteableSeqNumber);
				} catch (Exception e) {
					debugMsg(e);
					// if there was an error in the write operation nextWriteableSeqNumber is still
					// at the current writing number
					nextWriteableSeqNumber++;
					try {
						xbee.send(remoteDevice, DISCONNECT, DISCONNECT.length);
						sendLocal.close();
						socket.close();

					} catch (Exception e2) {}
				}
				synchronized (queueItem) {
					queueItem.isSocketWriting = false;
					queueItem.nextWriteableSeqNumber = nextWriteableSeqNumber;
				}
			}
		};
		writeLocal.start();
	} 
	public static void readLocal(String macAddress, XBeeQueue queueItem) {
		Thread writeRemote = new Thread() {
			public void run() {
				final byte[] reply = new byte[BUFFER_SIZE];
				int bytes_read;
				int sequenceNumber = 0;
				InputStream receiveRemote = null;
				Socket socket = null;
				RemoteXBeeDevice remoteDevice = null;
				try {
					// copy the objects to local variables as we hold a lock on queueItem and want
					// to release it as soon as possible
					synchronized(queueItem) {
						socket = queueItem.server;
						remoteDevice = queueItem.remoteDevice;
						receiveRemote = socket.getInputStream();
						sequenceNumber = queueItem.currentLocalSeqNumber;
					}
					while((bytes_read = receiveRemote.read(reply, HEADER_OFFSET, (reply.length - HEADER_OFFSET))) != -1) {
						UARTProxyUtil.insertSequenceHeader(reply, sequenceNumber);
	    				debugMsg("Next local sequence Number is: " + sequenceNumber);
						xbee.send(remoteDevice, reply, (HEADER_OFFSET + bytes_read));
						sequenceNumber++;
					}
					debugMsg("Read local");
				} catch (IOException e) {
					debugMsg(e);
					try {
						receiveRemote.close();
						socket.close();
					} catch (Exception e2) {}
				}
				synchronized(queueItem) {
					queueItem.isSocketReading = false;
					queueItem.currentLocalSeqNumber = sequenceNumber;
				}
			}
		};
		writeRemote.start();
	}
	private static void debugMsg(Exception e) {
		if (!debug) {
			return;
		}
		e.printStackTrace();
	}
	private static void debugMsg(String message) {
		if (!debug) {
			return;
		}
		System.out.println(message);
	}
}
