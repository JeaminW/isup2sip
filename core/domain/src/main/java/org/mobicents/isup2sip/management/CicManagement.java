package org.mobicents.isup2sip.management;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.mobicents.isup2sip.management.Channel.State;

public class CicManagement {
	private static final Logger logger = Logger.getLogger(CicManagement.class
			.getName());

	private static final Object synchCic = new Object();
	protected static Map<Integer, Channel> channelByCic = new HashMap<Integer, Channel>();

	protected int debug = 1;

	public CicManagement() throws Exception {
		logger.warning("Isup2Sip cic mahagement started");

		channelByCic.clear();

		for (int cic = 1; cic < 16; cic++) {
			// Channel ch = new
			// Channel("192.168.1.18:2427",Integer.toString(cic),cic);
			Channel ch = new Channel("192.168.1.18:2427",
					Integer.toString(cic + 32), cic);
			channelByCic.put(cic, ch);

			// TODO: in fact, need to reset circuits
			setState(cic, State.IDLE);
		}
	}

	public Channel getIdleChannel() {
		/*
		 * synchronized(synchCic) { Iterator<Channel> i = null; i =
		 * channelByCic.values().iterator(); while(i.hasNext()){
		 * if(i.next().getState() == State.IDLE) return i.next(); } return null;
		 * }
		 */
		debug++;
		logger.warning("debugging: value is " + debug);
		return channelByCic.get(debug);

	}

	public void setState(int cic, Channel.State state) {
		synchronized (synchCic) {
			try {
				channelByCic.get(cic).setState(state);
			} catch (Exception e) {

			}
		}
	}

}
