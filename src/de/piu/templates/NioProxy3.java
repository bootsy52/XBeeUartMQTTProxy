package de.piu.examples;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;

/**
 * A class that implements a simple non-blocking Socks 4 Proxy Server Implementing
 * connect command only
 *
 * @author dgreen
 * @date 09/19/2009
 *
**/
public class NioProxy3 implements Runnable {
	int bufferSize = 8192 ;
	/**
	* Port
	*/
	int port ;
	/**
	* Host
	*/
	String host ;
	static final String remoteHost = "192.168.100.89";
	static final int remotePort = 1883;
	//static final String remoteHost = "google.de";
	//static final int remotePort = 80;
	/**
	* Additional information clinging to each key {@link SelectionKey}
	*
	* @author dgreen
	* @date 09/19/2009
	*
	*/
	static class Attachment {
		/**
		* Buffer for reading, at the time of proxying becomes a buffer for
		* entries for key stored in peer
		*
		* IMPORTANT: When parsing Socks4 header, we assume that the size
		* Buffer, larger than normal header size, Mozilla browser
		* Firefox, header size is 12 bytes 1 version + 1 command + 2 port +
		* 4 ip + 3 id (MOZ) + 1 \ 0
		*/

		ByteBuffer in ;
		/**
		* Buffer for writing, at the time of proxying, is equal to read buffer for
		* key stored in peer
		*/
		ByteBuffer out ;
		/**
		* Where are we proxying
		*/
		SelectionKey peer ;

	}

	/**
	* the answer is OK or Service is provided
	*/
	static final byte[] OK = new byte[]{ 0x00, 0x5a, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

	/**
	* The heart of a non-blocking server, practically does not change from application to
	* application, except when using a non-blocking server in
	* multithreaded application, and working with keys from other threads, it is necessary
	* will add some KeyChangeRequest, but we are in this application without
	* needs
	*/
	@ Override
	public void run() {
		try {
			// Create Selector
			Selector selector = SelectorProvider.provider().openSelector();
			// Open the server channel
			ServerSocketChannel serverChannel = ServerSocketChannel.open();
			// Remove the lock
			serverChannel. configureBlocking(false) ;
			// Hang on the port
			serverChannel.socket().bind(new InetSocketAddress(host, port));
			// Register in the selector
			serverChannel. register (selector, serverChannel.validOps());
			// The main cycle of the non-blocking server
			// This cycle will be the same for almost any non-blocking
			// server
			while (selector.select() > -1) {
				// Get the keys on which the events occurred at the moment
				// last sample
				Iterator<SelectionKey> iterator = selector.selectedKeys().iterator() ;
				while (iterator.hasNext()) {
					SelectionKey key = iterator.next();
					iterator.remove();
					if (key.isValid()) {
						// Handle all possible key events
						try {
							if (key.isAcceptable()) {
								// accept connection
								accept(key);
							} else if (key.isConnectable()) {
								// Establish connection
								connect(key) ;
							} else if (key.isReadable()) {
								// Read the data
								read(key) ;
							} else if (key.isWritable()) {
								// Write data
								write(key);
							}
						} catch (Exception e) {
							e.printStackTrace();
							close(key);
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace() ;
			throw new IllegalStateException(e);
		}
	}

	/**
	* The function accepts the connection, registers the key with the action of interest.
	* read data (OP_READ)
	*
	* @param key
	* key on which the event occurred
	* @throws IOException
	* @throws ClosedChannelException
	*/
	private void accept(SelectionKey key) throws IOException, ClosedChannelException {
		// Accepted
		SocketChannel newChannel = ((ServerSocketChannel)key.channel()).accept();
		// Non-blocking
		newChannel.configureBlocking(false);
		// Register in the selector
		newChannel.register(key.selector(), SelectionKey.OP_READ);
	}

	/**
	* We read data available at the moment. The function is in two states -
	* read request header and direct proxying
	*
	* @param key
	* key on which the event occurred
	* @throws IOException
	* @throws UnknownHostException
	* @throws ClosedChannelException
	*/
	private void read(SelectionKey key) throws IOException, UnknownHostException, ClosedChannelException {
		SocketChannel channel = ((SocketChannel)key.channel());
		Attachment attachment = ((Attachment)key.attachment());
		if (attachment == null ) {
			// Lazily initialize the buffers
			key.attach(attachment = new Attachment()) ;
			attachment.in = ByteBuffer.allocate(bufferSize);
		}
		if (channel.read(attachment.in) < 1) {
			// -1  break 
			//  0   there is no space in the buffer, this can only be if
			// header exceeded buffer size
			close(key) ;
		} else if (attachment.peer == null) {
			// if there is no second end :) therefore we read the title
			readHeader(key, attachment) ;
		} else {
			// well, if we proxify, then we add interest to the second end
			// write
			attachment.peer.interestOps(attachment.peer.interestOps() | SelectionKey.OP_WRITE);
			// and remove the interest of the first to read, because it has not yet been recorded
			// current data, we will not read anything
			key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
			// prepare buffer for writing
			attachment.in.flip();
		}
	}

	private void readHeader(SelectionKey key, Attachment attachment) throws IllegalStateException, IOException, UnknownHostException, ClosedChannelException {
				// Create a connection
				SocketChannel peer = SocketChannel.open();
				peer.configureBlocking(false);
				// Get the address and port from the packet
				// Start to connect
				peer.connect(new InetSocketAddress(remoteHost, remotePort));
				// Register in the selector
				SelectionKey peerKey = peer.register(key.selector(), SelectionKey.OP_CONNECT);
				// Hear the requesting connection
				key.interestOps(0);
				// Key exchange :)
				attachment.peer = peerKey;
				Attachment peerAttachement = new Attachment();
				peerAttachement.peer = key;
				peerKey.attach(peerAttachement);
				// Clear the buffer with headers
				attachment.in.clear();
	}

	/**
	* Write data from the buffer
	*
	* @param key
	* @throws IOException
	*/
	private void write(SelectionKey key) throws IOException {
		// Close the socket only by writing all the data
		SocketChannel channel = ((SocketChannel)key.channel());
		Attachment attachment = ((Attachment)key.attachment());
		if (channel.write(attachment.out) == -1) {
			close(key) ;
		} else if (attachment.out.remaining() == 0) {
			if (attachment.peer == null) {
				// Write what was in the buffer and close
				close(key) ;
			} else {
				// if everything is written, clear the buffer
				attachment.out.clear();
				// Add a read interest to the second end
				attachment.peer.interestOps(attachment.peer.interestOps() | SelectionKey.OP_READ);
				// And we remove the interest on the record
				key. interestOps (key.interestOps() ^ SelectionKey.OP_WRITE);
			}
		}
	}

	/**
	* Complete the connection
	*
	* @param key
	* key on which the event occurred
	* @throws IOException
	*/
	private void connect(SelectionKey key) throws IOException {
		SocketChannel channel = ((SocketChannel)key.channel());
		Attachment attachment = ((Attachment)key.attachment());
		// Finish the connection
		channel.finishConnect();
		// Create a buffer and respond OK
		attachment.in = ByteBuffer.allocate(bufferSize);
		attachment.in.flip();
		attachment.out = ((Attachment)attachment.peer.attachment()).in;
		((Attachment)attachment.peer.attachment()).out = attachment.in;
		// Put the second end of the flags on the write and read
		// as soon as it writes OK, it will switch the second end to read and everything
		// will be happy
		attachment.peer.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
		key.interestOps(0);
	}

	/**
	* No Comments
	*
	* @param key
	* @throws IOException
	*/
	private void close(SelectionKey key) throws IOException {
		key.cancel();
		key.channel().close();
		SelectionKey peerKey = ((Attachment)key.attachment()).peer;
		if (peerKey != null) {
			((Attachment)peerKey.attachment()).peer = null ;
			if ((peerKey.interestOps() & SelectionKey.OP_WRITE ) == 0) {
				((Attachment)peerKey.attachment()).out.flip();
			}
			peerKey.interestOps(SelectionKey.OP_WRITE);
		}
	}

	public static void main (String [] args) {
		NioProxy3 server = new NioProxy3();
		server.host = "127.0.0.1";
		server.port = 1882;
		server.run();
	}
}
