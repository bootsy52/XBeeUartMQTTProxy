/*
* Copyright 2019, Carsten Menke on behalf of P-i-U UG & Co. KG
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
* 
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package de.piu.uart;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.XBeeDevice;
import com.digi.xbee.api.XBeeNetwork;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.models.XBeeMessage;
import com.digi.xbee.api.utils.HexUtils;

public class XBeeController{
	private boolean debug = false;
	private XBeeDevice localDevice = null;
	private XBeeNetwork xbeeNetwork = null;
	private RemoteXBeeDevice remoteDevice = null;
	private String remoteMacAddress = null;
	private String remoteDeviceID = null;
	private static String localDeviceID = null;
	private InputStream receiveRemote = null;
	private OutputStream sendRemote = null;
	private XBeeReceiveListener receiveListener = null;
	private static int packetSize = 100;
	public static XBeeDevice init(String port, int baudRate, int timeout) throws IOException {
		XBeeDevice thisDevice = new XBeeDevice(port, baudRate);
		try {
			thisDevice.setReceiveTimeout(timeout);
			thisDevice.open();
			byte[] maxBytes = thisDevice.getParameter("NP");
			localDeviceID = thisDevice.getNodeID();
			System.out.println("Local Device ID is: " + localDeviceID);
			packetSize = Integer.parseInt(HexUtils.byteArrayToHexString(maxBytes));
			System.out.println("Packet Size is: " + packetSize + " bytes");
			
		} catch (XBeeException e) {
			throw new IOException(e);
		}
	  return thisDevice;
	}
	public XBeeController(XBeeDevice localDevice, String remoteDeviceID, boolean debug) throws IOException {
		this.debug = debug;
		this.remoteDeviceID = remoteDeviceID;
		this.localDevice = localDevice;
		try {
			// Obtain the remote XBee device from the XBee network.
			xbeeNetwork = localDevice.getNetwork();
			if (remoteDeviceID != null) {
				remoteDevice = xbeeNetwork.discoverDevice(remoteDeviceID);
				if (remoteDevice != null) {
					remoteMacAddress = String.valueOf(remoteDevice.get64BitAddress());
				}
			 debugMsg("Remote Mac Address: " + remoteMacAddress);
			}
		} catch (XBeeException e) {
			e.printStackTrace();
			throw new IOException(e);
		}
	}
	public void send(byte[] writeBytes, int length) throws IOException {
		send(this.remoteDevice, writeBytes, length);
	}
	public void send(RemoteXBeeDevice remoteDevice, byte[] writeBytes, int length) throws IOException {
		send(remoteDevice, writeBytes, length, 100);
	}
	public void send(RemoteXBeeDevice remoteDevice, byte[] writeBytes, int length, int pause) throws IOException {
		if (remoteDevice == null) {
			throw new IOException("Remote device is null, remoteDeviceID is " + this.remoteDeviceID + ", has this class been properly initialized?");
		}
		try {
			// The XBee device can ony handle packetsize bytes in one send() call
			// so therefore larger Packets have to splitted in chunks of the length of the packet size
			if (length > packetSize) {
				debugMsg("Sending large Packet");
				int usePacketSize = packetSize;
				// Loop over the data array in steps of the packetsize until
				// the end of the byte array has been reached
				for (int i = 0; i < length; i += packetSize) {
					// the last packet may be smaller than packet size
					// so set the length of the last byte array to the remaining bytes
					// e.g. imagine packet size is 100 and length of the byte array is 283
					// the last iteration would just be 83
					if (length - i < packetSize) {
						usePacketSize = length - i;
					}
					// construct a new byte array of suitable size
					byte[] packetWriteBytes = new byte[usePacketSize];
					// packetWriteBytes will always start at 0, while 
					// the pointer to the position in the data byte array moves further (therefore j + i)
					// where i is the current offset to start from
					for (int j = 0; j < usePacketSize; j++) {
						packetWriteBytes[j] = writeBytes[j + i];
					}
					localDevice.sendData(remoteDevice, packetWriteBytes);
				}
			} else {
				byte[] packetWriteBytes = new byte[length];
				for (int i = 0; i < length; i++) {
					packetWriteBytes[i] = writeBytes[i];
				}
				localDevice.sendData(remoteDevice, packetWriteBytes);
			}
			// It has been found that sometimes it happens that consecutive packets
			// overtake former ones on the air and reach the receiver earlier
			// to prevent this we issue a small break between sending each packet
			// 100 ms have worked out well in my environment but may be adjusted in 
			// larger networks
			Thread.sleep(pause);
			debugMsg("XBee Done Sending to: " + remoteMacAddress);
		} catch (XBeeException e) {
			throw new IOException(e);
		} catch (InterruptedException e) {} 
	} 
	public byte[] receive() throws IOException {
		try {
			XBeeMessage xbeeMessage = localDevice.readData(localDevice.getReceiveTimeout());
			if (xbeeMessage != null) {
				debugMsg("XBee Done Receiving Data from: " + remoteMacAddress);
				return xbeeMessage.getData();
			}
		} catch (Exception e) {
			throw new IOException(e);
		}
	  return new byte[]{};
	}
	public XBeeMessage receiveMessage() throws IOException {
		try {
			XBeeMessage xbeeMessage = localDevice.readData(localDevice.getReceiveTimeout());
			return xbeeMessage;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
	public void addListener(XBeeReceiveListener receiveListener) {
		this.receiveListener = receiveListener;
		localDevice.addDataListener(receiveListener);
	}
	public boolean dataAvailable() {
		 synchronized(this.receiveListener.sync) {
			 return this.receiveListener.available;
		 }
	}
	public String getLocalDeviceID() {
		return localDeviceID;
	}
	public InputStream getRemoteReceive() {
		return this.receiveRemote;
	}
	public OutputStream getRemoteSend() {
		return this.sendRemote;
	}
    private void debugMsg(String message) {
		  if (!debug) {
			  return;
		  }
		  System.out.println(message);
	  }
}
