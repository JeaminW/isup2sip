package org.mobicents.isup2sip.management;

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
}
