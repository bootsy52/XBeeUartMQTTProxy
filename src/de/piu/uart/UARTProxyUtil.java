package de.piu.uart;

public class UARTProxyUtil {
	// 4 bytes Header = Offset 5 to start at byte 5
	public static final int HEADER_OFFSET = 4;
	public static final byte[] DISCONNECT = new byte[]{0x00, 0x00, 0x00, 0x00, (byte)0xE0, 0x0};
 public static byte[] encodeSequenceHeader(int sequenceNumber) {
		byte[] header = new byte[4];
		header[0] = (byte)((sequenceNumber & 0xFF000000) >> 24);
	    header[1] = (byte)((sequenceNumber & 0xFF0000) >> 16);
	    header[2] = (byte)((sequenceNumber & 0xFF00) >> 8);
	    header[3] = (byte)(sequenceNumber & 0xFF);
	    return header;
 }
 public static void insertSequenceHeader(byte[] data, int sequenceNumber) {
	 byte[] header = encodeSequenceHeader(sequenceNumber);
	 if (data!= null && data.length >= HEADER_OFFSET) {
		 data[0] = header[0];
		 data[1] = header[1];
		 data[2] = header[2];
		 data[3] = header[3];
	 } else {
		 throw new IndexOutOfBoundsException("ByteArray is null or smaller then " + HEADER_OFFSET + " bytes");
	 }
 }
 
 public static int decodeSequenceHeader(byte[] sequenceHeader) {
	 int sequenceNumber = (((int)(sequenceHeader[0]) << 24) & 0xFF000000) |
			 (((int)(sequenceHeader[1]) << 16) & 0xFF0000)	 |
			 (((int)(sequenceHeader[2]) << 8) & 0xFF00)      |
			 ((int)(sequenceHeader[3]) & 0xFF);
	 return sequenceNumber;
 }
 public static byte[] stripHeader(byte[] data) {
	 byte[] noHeaderData = new byte[data.length - HEADER_OFFSET];
	 for (int i = HEADER_OFFSET; i < data.length; i++) {
		 noHeaderData[i - HEADER_OFFSET] = data[i];
	 }
	 return noHeaderData;
 }
 public static boolean isClientDisconnected(byte[] packet) {
	  if (packet != null && packet.length >= 2) {
		  int packetHeader;
		  // the packet may contain our Sequence header which is 4 bytes
		  if (packet.length == 6) {
			  packetHeader = packet[4] & 0xFF;
			  packetHeader += packet[5] & 0xFF;
		  } else {
			  packetHeader = packet[0] & 0xFF;
			  packetHeader += packet[1] & 0xFF;  
		  }
		  // The client disconnect Header is E0 00
		  // E0 = 224
		  // 00 = 0
		  // 224 + 0 = 224
		  if (packetHeader == 224) {
			  return true;
		  }		  
	  }
	  return false;
 }
}
