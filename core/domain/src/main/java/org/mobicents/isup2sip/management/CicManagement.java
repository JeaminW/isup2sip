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

package org.mobicents.isup2sip.management;
/**
 * @author dmitri soloviev
 * 
 */

import jain.protocol.ip.mgcp.JainMgcpEvent;
import jain.protocol.ip.mgcp.message.RestartInProgress;
import jain.protocol.ip.mgcp.message.parms.CallIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConflictingParameterException;
import jain.protocol.ip.mgcp.message.parms.ConnectionDescriptor;
import jain.protocol.ip.mgcp.message.parms.ConnectionIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConnectionMode;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.RestartMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.Iterator;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.slee.ActivityContextInterface;
import javax.slee.SbbLocalObject;

import net.java.slee.resource.mgcp.JainMgcpProvider;
import net.java.slee.resource.mgcp.MgcpActivityContextInterfaceFactory;
import net.java.slee.resource.mgcp.MgcpConnectionActivity;
import net.java.slee.resource.mgcp.event.TransactionTimeout;

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
			if(part!=0)	ch = new Channel(gateway,Integer.toHexString(cic + 32), cic);
			else 		ch = new Channel(gateway,Integer.toHexString(cic), cic);
			channelByCic.put(cic, ch);

			// TODO: in fact, need to reset circuits
			//ch.setState(State.IDLE);
			
			logger.warning("created channel " + ch + 
					" cic=" + cic + ": " + ch.getCic() + "|" + 
					ch.getEndpointId() + "|" + ch.getGatewayAddress());
		}
	}

	public Channel allocateIdleChannel() {
		synchronized(synchCic) { 
			Channel ch = getNeededState(State.IDLE);
			if(ch == null) return null;
			
			logger.info("allocating cic=" + ch.getCic() + " ep=" + ch.getEndpointId());
		 	ch.setState(State.OUTGO);
		 	return ch;
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
	
	public void setUnknown(int cic) {
		synchronized (synchCic) {
			try {
				channelByCic.get(cic).setState(State.UNKNOWN);
			} catch (Exception e) {
			}
		}		
	}
	
	public boolean isActive(int cic){
		Channel.State state = channelByCic.get(cic).getState();
		if(state.equals(Channel.State.ANSWERED)) return true;
		if(state.equals(Channel.State.INCO)) return true;
		if(state.equals(Channel.State.OUTGO)) return true;
		return false;
	}
	
	public void resetChannels(Object obj, Context ctx, SbbLocalObject sbbLocalObject){
		logger.info("resetting channels");
/*
		// MGCP
		JainMgcpProvider mgcpProvider = null;
		MgcpActivityContextInterfaceFactory mgcpActivityContestInterfaceFactory = null;
		try{
			mgcpProvider = (JainMgcpProvider) ctx.lookup(Isup2SipManagement.MGCP_PROVIDER);
			mgcpActivityContestInterfaceFactory = (MgcpActivityContextInterfaceFactory) ctx.lookup(Isup2SipManagement.MGCP_ACI_FACTORY);
		} catch (Exception e) {
			logger.severe("exception " + e.getMessage());
			return;
		}
		
		// ISUP

//        isupProvider = (RAISUPProvider) ctx.lookup("slee/resources/isup/1.0/provider");
//        isupMessageFactory = isupProvider.getMessageFactory();
//        isupParameterFactory = isupProvider.getParameterFactory();
//        isupActivityContextInterfaceFactory = (org.mobicents.slee.resources.ss7.isup.ratype.ActivityContextInterfaceFactory) ctx.lookup("slee/resources/isup/1.0/acifactory");
*/		
		synchronized(synchCic) { 
			Iterator <Channel> i = null; i = channelByCic.values().iterator(); 
			while(i.hasNext()){
					Channel ch = i.next();
					if(ch.getState() == State.UNKNOWN){
						try {														
							// fire MGCP RSIP
/*							EndpointIdentifier endpointID = new EndpointIdentifier(ch.getEndpointId(),ch.getGatewayAddress());
							RestartInProgress rsip = new RestartInProgress(obj, endpointID, RestartMethod.Graceful);

							final int txID = mgcpProvider.getUniqueTransactionHandler();
							rsip.setTransactionHandle(txID);
							
							MgcpConnectionActivity connectionActivity = mgcpProvider.getConnectionActivity(txID, endpointID);
							ActivityContextInterface epnAci = mgcpActivityContestInterfaceFactory.getActivityContextInterface(connectionActivity);
							epnAci.attach(sbbLocalObject);
													
							mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { rsip });
							logger.info("RSIP sent; ep ID=" + endpointID + " sbb=" + sbbLocalObject);
*/
							// TODO: 
							// fire ISUP RSC
				            
				            // hack:
							ch.setState(State.IDLE);

						} catch (Exception e) {
							logger.severe("exception " + e.getMessage());
						}
					}
			}
		}
	}
	
	private Channel getNeededState(Channel.State state){
		Iterator <Channel> i = null; i = channelByCic.values().iterator(); 
		while(i.hasNext()){
				Channel ch = i.next();
				if(ch.getState() == state) return ch; 
		}
		return null;
	}
}
