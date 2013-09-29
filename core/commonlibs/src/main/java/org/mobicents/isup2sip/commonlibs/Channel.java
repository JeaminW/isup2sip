/**
 * TeleStax, Open Source Cloud Communications  
 * Copyright 2012, Telestax Inc and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.isup2sip.commonlibs;
/**
 * @author dmitri soloviev
 * 
 */
public class Channel{
	public enum State {
		UNKNOWN, 
		IDLE, 		// can be allocated/seized
		INCO, 		// incoming call setup
		OUTGO, 		// outgoing call setup
		ANSWERED, 
		BLOCKED, 
		BLOCKREQ
	};

	protected State state;

	/** ip address of Telscale SS7 card */
	protected String gatewayAddress;

	/** MGCP endpoint id */
	protected String endpointId;

	/** ISUP (ea isup-api) cic */
	protected int cic; // from isup-api

	public Channel(String gatewayAddr, String endPoint, int isupCic) {
		state = State.UNKNOWN;
		gatewayAddress = gatewayAddr;
		cic = isupCic;
		endpointId = endPoint;
	}

	public String getEndpointId() {
		return endpointId;
	}

	public int getCic() {
		return cic;
	}

	public State getState() {
		return state;
	}

	public String getGatewayAddress() {
		return gatewayAddress;
	}

	public void setState(State st) {
		state = st;
	}
	
	@Override 
	public String toString(){
		return "channel CIC=" + cic + " EP=" + this.endpointId + "@" + this.gatewayAddress;
	}
}
