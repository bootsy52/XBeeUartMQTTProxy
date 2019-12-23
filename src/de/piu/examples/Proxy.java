package de.piu.examples;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import de.piu.examples.NioProxy.Attachment;
import de.piu.log.Log;

public class Proxy  {
	static final String localHost = "127.0.0.1";
	static final int localPort = 1882;
	static final String remoteHost = "google.de";
	static final int remotePort = 80;
	private final static int WRITE_BUFFER_SIZE = 8192;
	private final static int READ_BUFFER_SIZE = 8192;
	private static SocketChannel remoteChannel = null;
	private static InetSocketAddress remoteAddr = new InetSocketAddress(remoteHost, remotePort);
	// The buffer into which we'll read data when it's available
	private static ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);

	private static Selector selector = null;
	private static Log log = new Log("MQTT Proxy");
	public static void main(String[] args) throws IOException {
		selector = Selector.open();
		startServer(localHost, localPort);
		
		while(selector.select() > -1) {
			
			Iterator<SelectionKey> selectedChannel = selector.selectedKeys().iterator();
			while (selectedChannel.hasNext()) {
				SelectionKey selectedKey = selectedChannel.next();
				selectedChannel.remove();
				if (!selectedKey.isValid()) {
					continue;
				}
				try {
					if (selectedKey.isAcceptable()) {
						// accept connection
						accept(selectedKey);
					} else if (selectedKey.isConnectable()) {
						// Establish connection
						connect(selectedKey) ;
					} else if (selectedKey.isReadable()) {
						// Read the data
						receive(selectedKey) ;
					} else if (selectedKey.isWritable()) {
						// Write data
						send(selectedKey);
					}
				} catch (Exception e) {
					e.printStackTrace();
					close(selectedKey);
				}
			}
			selector.selectedKeys().clear();
			selector.wakeup();
		}
	}

public static boolean startServer(String host, int port) {
	try {    
		System.out.println(String.format("Binding to host %s on port %d", host, port));
		InetSocketAddress address = new InetSocketAddress(host, port);
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(address);
		server.configureBlocking(false);
		server.register(selector, SelectionKey.OP_ACCEPT);
		return true;
	} catch (IOException e) {
		log.write("error", "Could not bind Server " + host + ":" + port + " " + e.getLocalizedMessage(), true);
		return false;
		}
}
private static void connect(SelectionKey key) throws IOException {

	// Finish the connection
	remoteChannel.finishConnect();
	// Put the second end of the flags on the write and read
	// as soon as it writes OK, it will switch the second end to read and everything
	// will be happy
	SelectionKey remoteKey = remoteChannel.keyFor(selector); 
	remoteKey.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
	key.interestOps(0);
	System.out.println("Connected to Server " + remoteHost + " on Port " + remotePort + " Connected: " + remoteChannel.isConnected());
}
private static void accept(SelectionKey selectedKey) throws IOException {
    // For an accept to be pending the channel must be a server socket channel.
    ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectedKey.channel();

    // Accept the connection and make it non-blocking
    SocketChannel localChannel = serverSocketChannel.accept();
    // Socket socket = socketChannel.socket();
    localChannel.configureBlocking(false);
    System.out.println("Accepted connection");
    SocketChannelAttachment attachment = new SocketChannelAttachment();
    // Register the new SocketChannel with our Selector, indicating
    // we'd like to be notified when there's data waiting to be read
    try {
    	remoteChannel = SocketChannel.open();
    	remoteChannel.configureBlocking(false);
    	remoteChannel.connect(remoteAddr);
    	SelectionKey remoteKey = remoteChannel.register(selector, SelectionKey.OP_CONNECT);
    	localChannel.register(selector, SelectionKey.OP_READ);
		// Hear the requesting connection
		selectedKey.interestOps(0);
		attachment.remoteKey = remoteKey;
		SocketChannelAttachment remoteAttachement = new SocketChannelAttachment();
		remoteAttachement.remoteKey = selectedKey;
		remoteKey.attach(remoteAttachement);
		// Clear the buffer with headers
		attachment.in.clear();
    	
    } catch (Exception e) {
    	System.out.println("Could not connect to Server "+ remoteHost + " on Port " + remotePort);
    	selectedKey.cancel();
    	localChannel.close();
    }
  }
//read from the socket channel
private static void receive(SelectionKey selectedKey) throws IOException {
	SocketChannel localChannel = (SocketChannel)selectedKey.channel();
	readBuffer.clear();
	int bytesRead = -1;
	try {
		if ((bytesRead = localChannel.read(readBuffer)) > 0) {
			selectedKey.interestOps(SelectionKey.OP_WRITE);
			selectedKey.interestOps(selectedKey.interestOps() ^ SelectionKey.OP_READ);
			System.out.println("Read: " + bytesRead + " bytes");
		} else {
			SocketAddress remoteAddr = localChannel.getRemoteAddress();
			System.out.println("Connection closed by client: " + remoteAddr);
			selectedKey.cancel();
			localChannel.close();
		}
	}catch (IOException e) {
		e.printStackTrace();
		// The remote forcibly closed the connection, cancel
		// the selection key and close the channel.
		selectedKey.cancel();
		localChannel.close();
		return;
	} 
	selector.wakeup();
	return;
}
private static void send(SelectionKey selectedKey) throws IOException {
	SocketChannel localChannel = (SocketChannel)selectedKey.channel();
	int localWrite = -1;
	try {
		readBuffer.flip();
		localWrite = remoteChannel.write(readBuffer);
		System.out.println("Wrote: " + localWrite + " bytes");
	}  catch (IOException e) {
		e.printStackTrace();
		selectedKey.cancel();
		localChannel.close();
	}
	selectedKey.interestOps(SelectionKey.OP_READ);
	selector.wakeup();
}

private static void close(SelectionKey key) throws IOException {
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

}
