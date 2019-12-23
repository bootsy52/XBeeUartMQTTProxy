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

import com.digi.xbee.api.listeners.IDataReceiveListener;
import com.digi.xbee.api.models.XBeeMessage;

/**
 * Class to manage the XBee received data that was sent by other modules in the 
 * same network.
 * 
 * <p>Acts as a data listener by implementing the 
 * {@link IDataReceiveListener} interface, and is notified when new 
 * data for the module is received.</p>
 * 
 * @see IDataReceiveListener
 *
 */

public class XBeeReceiveListener implements IDataReceiveListener {
	public byte[] data;
	public boolean ready = true;
	public boolean available = false;
	public boolean error = false;
	public Exception exception = null;
	public Object sync = new Object();
	@Override
	public void dataReceived(XBeeMessage xbeeMessage){
		try {
			if (this.ready) {
				this.data = xbeeMessage.getData();
				synchronized(sync) {
					available = true;
				}
			} else {
				// create a destination array that is the size of the two arrays
				byte[] newBytes = xbeeMessage.getData();
				byte[] addBytes = new byte[data.length + newBytes.length];

				// copy data into start of addBytes (from pos 0, copy data.length bytes)
				System.arraycopy(data, 0, addBytes, 0, data.length);

				// copy newBytes into end of destination (from pos newBytes.length, copy data.length bytes)
				System.arraycopy(newBytes, 0, addBytes, data.length, newBytes.length);
				synchronized(sync) {
					data = addBytes;
					available = true;
				}
			}
		} catch (Exception e) {
			exception = e;
		}
	}

}
