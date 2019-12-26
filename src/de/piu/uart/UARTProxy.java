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
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentHashMap;

import com.digi.xbee.api.XBeeDevice;
/**
 * This class implements a simple single-threaded proxy server.
 **/
public class UARTProxy {
  private static boolean debug = false;
  private static XBeeDevice xbeeDevice = null;
  private static Boolean isConnected = false;
  private static String host = "localhost";
  private static String remoteNodeID = "";
  private static int adminPort = 1881;
  private static int localPort = 1882;
  private static final int SO_TIMEOUT = 30000;
  private static String uartDevice = "/dev/ttyS4";
  private static final int UART_BAUD_RATE = 9600;
  private static final int UART_TIMEOUT = 3000;
  private static final int BUFFER_SIZE = 512;
  private static final int HEADER_OFFSET = UARTProxyUtil.HEADER_OFFSET;
  public static ConcurrentHashMap<Integer, byte[]> dataInBuffer = new ConcurrentHashMap<Integer, byte[]>();
  /** The main method parses arguments and passes them to runServer */
  public static void main(String[] args) throws IOException {
    try {
      // Check the number of arguments
      if (args.length < 5) 
        throw new IllegalArgumentException("Wrong number of arguments.");

      // Get the command-line arguments: the host and port we are proxy for
      // and the local port that we listen for connections on
      uartDevice = args[0];
      remoteNodeID = args[1];
      host = args[2];
      localPort = Integer.parseInt(args[3]);
      adminPort = Integer.parseInt(args[4]);
		if (args.length > 5) {
			if (args[5].equals("-v")) {
				debug = true;
			}
		}
      // Print a start-up message
      System.out.println("Starting proxy on " + host + " port " + localPort + " using UART device: " + uartDevice);
      System.out.println("admin interface on port " + adminPort);
      // Init the XBeeDevice and open it
      xbeeDevice = XBeeController.init(uartDevice, UART_BAUD_RATE, UART_TIMEOUT);
      // And start running the server
      runServer(host, localPort);   // never returns
    } 
    catch (Exception e) {
      System.err.println(e);
      System.err.println("Usage: java UARTProxy <UART device> <xbee remote nodeID> <host> <localport> <admin port>");
      System.err.println("or");
      System.err.println("Usage: java UARTProxy <UART device> <xbee remote nodeID> <host> <localport> <admin port> -v");
      System.err.println();
      System.err.println("whereas: UART device is something like /dev/ttyS1 and xbee remote nodeID is the Value of the NI Parameter stored in the remote XBee device");
    }
  }

  /**
   * This method runs a single-threaded proxy server for 
   * host:remoteport on the specified local port.  It never returns.
   **/
  public static void runServer(String host, int localport) throws IOException {
    // Create a ServerSocket to listen for connections with
    @SuppressWarnings("resource")
	ServerSocket serverSocket = new ServerSocket(localport, 1);
    serverSocket.setSoTimeout(SO_TIMEOUT);
    @SuppressWarnings("resource")
	ServerSocket serverAdminSocket = new ServerSocket(adminPort);
    serverAdminSocket.setSoTimeout(SO_TIMEOUT);
    final XBeeController xbee = new XBeeController(xbeeDevice, remoteNodeID, debug);
    
    // As the XBee device is opened exclusive there are no interactions
    // possible from outside this program, therefore we can only provide
    // access to the device through this part
    Thread adminChannel = new Thread() {
		private static final String CMD_GETID = "GETID";
    	public void run() {
    		String command = "";
    		Socket adminLocal = null;
    		InputStream in = null;
			OutputStream out = null;
    		while (true) {
    			try {
    				adminLocal = serverAdminSocket.accept();
    				in = adminLocal.getInputStream();
    				out = adminLocal.getOutputStream();
    				byte[] requestAdmin = new byte[BUFFER_SIZE];
    				int bytes_read = 0;
    				while((bytes_read = in.read(requestAdmin)) != -1) {
    					command = new String(requestAdmin, 0, bytes_read, Charset.forName("US-ASCII"));
    					command = command.replace("\n", "").replace("\r", "");
    					switch (command) {
    						case CMD_GETID:
    							debugMsg("Command is: " + command);
    							out.write(xbee.getLocalDeviceID().getBytes());
    							out.flush();
    							break;
    						default:
    							debugMsg("Unsupported Command: " + command);
    							out.write(("Unsupported Command: " + command + "\n").getBytes());
    							out.flush();
    					}
    					
    				}

    			} 
    			catch (SocketTimeoutException e) {} 
    			catch (Exception e) {
    				debugMsg(e);
    				try {
    					if (in != null) {
    						in.close();
    					}
    					if (out != null) {
        					out.close();	
    					}
    					adminLocal.close();
    				} catch (Exception e2) {}
    			}
    		}
    	}
    };
    adminChannel.start();
    
    // This is a server that never returns, so enter an infinite loop.
    while(true) { 
      // Variables to hold the sockets to the client and to the server.
      Socket local = null;
      int currentSeqNumber = 0;
      int nextReadableSeqNumber = 0;
      try {
        // Wait for a connection on the local port
        local = serverSocket.accept();
        dataInBuffer.clear();
        isConnected = true;
        // Get client streams.  Make them final so they can
        // be used in the anonymous thread below.
        final InputStream receiveLocal = local.getInputStream();
        final OutputStream sendLocal= local.getOutputStream();
        
        
        // Make a thread to read the client's requests and pass them to the 
        // server.  We have to use a separate thread because requests and
        // responses may be asynchronous.
        Thread writeRemote = new Thread() {
        	public void run() {
        		int bytes_read;
        		int writeSequenceNumber = 0;
        		// Create buffers for client-to-server and server-to-client communication.
        	    // We make one final so it can be used in an anonymous class below.
        	    // Note the assumptions about the volume of traffic in each direction...
        	    byte[] request = new byte[BUFFER_SIZE];
        		try {
        			synchronized(isConnected) {
        				if (isConnected == false) {
        					receiveLocal.close();
        					return;
        				}
        			} 
        			while((bytes_read = receiveLocal.read(request, HEADER_OFFSET, (request.length - HEADER_OFFSET))) != -1) {
        				UARTProxyUtil.insertSequenceHeader(request, writeSequenceNumber);
        				debugMsg("Next Sequence Number is: " + writeSequenceNumber);
        				xbee.send(request, (HEADER_OFFSET + bytes_read));
        				debugMsg("Sent Remote " + (request[HEADER_OFFSET] & 0xFF) + " length: " + request.length);
        				if(UARTProxyUtil.isPeerDisconnected(request, true)) {
        					debugMsg("Sent disconnect to peer by local client");
        					synchronized(isConnected) {
        						isConnected = false;
        					}
        				}
        			// not necessarily needed as always there will be just read bytes_read
        			// and thus remaining bytes from a former longer read would not affect the operation
        		    // but we want a clean byte array
        		    request = new byte[BUFFER_SIZE];
        			writeSequenceNumber++;        			
        			}
        		}
        		catch (IOException e) {
        			debugMsg(e);
        		}
        	}
        };

        // Start the client-to-server request thread running
        writeRemote.start();  

        // Meanwhile, in the main thread, read the server's responses
        // and pass them back to the client.  This will be done in
        // parallel with the client-to-server request thread above.
        int idle = 0;
        while (true) {
        	try {
        		synchronized(isConnected) {
        			if (isConnected == false) {
        				break;
        			}
        		}
        		// if there is no disconnect sent, then we have to detect a stale connection
        		// with this method
        		if (idle > 10) {
        			synchronized(isConnected) {
            			isConnected = false;
            		}
        			break;
        		}
        		if (local.isOutputShutdown()) {
        			debugMsg("Output Shutdown");
        			break;
        		}
        		if (local.isConnected() == false) {
        			debugMsg("Not connected");
        			break;
        		}
        		if (local.isClosed()) {
        			debugMsg("Local closed");
        			break;
        		}
        		byte[] received = xbee.receive();
        		// All packets which do not at lesat have the 1 byte more then our HEADER are
        		// invalid or no packet has been received, xbee.receive() returns an empty byte array if no data is received
        		if (received.length > HEADER_OFFSET) {
        			// if the Peer is disconnected we don't need to send any more data nor expect to receive something
        			// further more as the disconnect command is just implemented for client -> server sending the packet
        			// to our local client will do nothing as this is not understood on the client side, so we first check
        			// for disconnect and terminate the connection if the remote peer is disconnected and after that write to the client
        			if (UARTProxyUtil.isPeerDisconnected(received, true)) {
            			debugMsg("Connecton terminated by remote peer");
            			local.close();
            			break;
            		}
        			currentSeqNumber = UARTProxyUtil.decodeSequenceHeader(received);
        			dataInBuffer.put(currentSeqNumber, received);
        			for (int i = nextReadableSeqNumber; i <= currentSeqNumber; i++) {
        				// remvoe returns the value associated to the key or null if not existent
        				received = dataInBuffer.remove(i);
        				if (received != null) {
        					debugMsg("Write local, sequence Number: " + nextReadableSeqNumber);
        					idle = 0;
        					sendLocal.write(received, HEADER_OFFSET, (received.length - HEADER_OFFSET));
        					sendLocal.flush();
        				} else {
        					break;
        				}
        				nextReadableSeqNumber++;
        			}
        		}
        		
        		idle++;
        	}
        	// catch (InterruptedException e) {}
        	catch(IOException e) {
        		debugMsg(e);
        		break;
        	}
        }
   		debugMsg("Exit while");
        // The server closed its connection to us, so close our 
        // connection to our client.  This will make the other thread exit.
        sendLocal.close();
        local.close();
      }
      catch (SocketTimeoutException e) {}
      catch (IOException e) { debugMsg(e); }
      catch (ArrayIndexOutOfBoundsException e) { debugMsg(e); }
      // Close the sockets no matter what happens each time through the loop.
      finally { 
        try { 
          if (local != null) local.close(); 
        }
        catch(Exception e) {}
      }
    }
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
