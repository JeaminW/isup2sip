package org.mobicents.isup2sip.management;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.Iterator;

import org.mobicents.isup2sip.management.Channel.State;

public class CicManagement {
	private static final Logger logger = Logger.getLogger(CicManagement.class.getName());

	private static final Object synchCic = new Object();
	protected static Map<Integer, Channel> channelByCic = new HashMap<Integer, Channel>();

	public CicManagement(String gateway, int part) throws Exception {
		logger.warning("Isup2Sip cic mahagement started");

		channelByCic.clear();
		// TODO: this is for DEBUG only, 
		// real configuration should be read from a file
		// for testing, half of a card is acting as one equipment, a second as is another
		
		for (int cic = 1; cic < 31; cic++) {
			if(cic == 16) continue;
			Channel ch;
			if(part!=0)	ch = new Channel(gateway,Integer.toString(cic + 32), cic);
			else 		ch = new Channel(gateway,Integer.toString(cic), cic);
			channelByCic.put(cic, ch);

			// TODO: in fact, need to reset circuits
			ch.setState(State.IDLE);
			
			logger.warning("created channel " + ch + 
					" cic=" + cic + ": " + ch.getCic() + "|" + 
					ch.getEndpointId() + "|" + ch.getGatewayAddress());
		}
	}

	public Channel getIdleChannel() {
		synchronized(synchCic) { 
			Iterator <Channel> i = null; i = channelByCic.values().iterator(); 
			while(i.hasNext()){
					Channel ch = i.next();
					logger.warning("trying "+ ch + ":" + ch.getCic() + " state=" + ch.getState());
					if(ch.getState() == State.IDLE) {
						 	
						 	logger.warning("allocating cic=" + ch.getCic() + " ep=" + ch.getEndpointId());
						 	ch.setState(State.OUTGO);
						 	return ch; }
					 else logger.warning("skipping cic=" + ch.getCic() + " ep=" + ch.getEndpointId());
			}
					 
			return null;
		}
	}

	public Channel getChannelByCic(int cic) {
		return channelByCic.get(cic);
	}
	public void setIdle(int cic) {
		synchronized (synchCic) {
			try {
				channelByCic.get(cic).setState(State.IDLE);
			} catch (Exception e) {

			}
		}
	}

	public boolean setBusy(int cic) {
		synchronized (synchCic) {
			try {
				if(channelByCic.get(cic).getState()==State.IDLE){
					channelByCic.get(cic).setState(State.INCO);
					return true;
				}
				return false;
			} catch (Exception e) {
				return false;
			}
		}
	}

	public void setAnswered(int cic) {
		synchronized (synchCic) {
			try {
				channelByCic.get(cic).setState(State.ANSWERED);
			} catch (Exception e) {

			}
		}
	}
}
