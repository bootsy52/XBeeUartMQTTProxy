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

import java.net.Socket;

import com.digi.xbee.api.RemoteXBeeDevice;

public class XBeeQueue {
	public Socket server = null;
	public long lastReceived = 0;
	public RemoteXBeeDevice remoteDevice = null;
	public boolean isSocketWriting = false;
	public boolean isSocketReading = false;
	public byte[] dataOut = null;
	
}
