package org.mobicents.isup2sip.commonlibs;

import java.io.Serializable;

public class RequestIsupResetsEvent implements Serializable {

	private int multiplexId;

	public RequestIsupResetsEvent(int multiplexId) {
		this.multiplexId = multiplexId;
	}
	
	public int getMultiplexId(){
		return this.multiplexId;
	}

	@Override
	public String toString() {
		return "RequestIsupResetsEvent for multiplex " + this.multiplexId;
	}
}
