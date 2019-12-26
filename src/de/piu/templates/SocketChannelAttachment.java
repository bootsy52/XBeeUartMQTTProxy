package de.piu.examples;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public class SocketChannelAttachment {
		/**
		* Buffer for reading, at the time of proxying becomes a buffer for
		* entries for key stored in peer
		*
		*/
		ByteBuffer in ;
		/**
		* Buffer for writing, at the time of proxying, is equal to read buffer for
		* key stored in peer
		*/
		ByteBuffer out;
		/**
		* Where are we proxying
		*/
		SelectionKey remoteKey ;
}
