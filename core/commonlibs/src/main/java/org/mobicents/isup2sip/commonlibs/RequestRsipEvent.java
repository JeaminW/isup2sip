package org.mobicents.isup2sip.commonlibs;

import java.io.Serializable;
import org.mobicents.isup2sip.commonlibs.Channel;

public class RequestRsipEvent implements Serializable {

	private Channel channel;

	public RequestRsipEvent(Channel ch) {
		this.channel = ch;
	}

	public Channel getChannel(){
		return this.channel;
	}
	
	@Override
	public String toString() {
		return "RequestRsipEvent for cic=" + channel.getCic() + ", " + channel.getEndpointId();
	}
}
