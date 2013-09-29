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

import javax.resource.ResourceException;

import javax.slee.ActivityContextInterface;
import javax.slee.SbbLocalObject;

import net.java.slee.resource.mgcp.JainMgcpProvider;
import net.java.slee.resource.mgcp.MgcpActivityContextInterfaceFactory;
import net.java.slee.resource.mgcp.MgcpConnectionActivity;
import net.java.slee.resource.mgcp.event.TransactionTimeout;


import javax.naming.InitialContext;
import javax.slee.EventTypeID;
import javax.slee.connection.ExternalActivityHandle;
import javax.slee.connection.SleeConnection;
import javax.slee.connection.SleeConnectionFactory;

import org.mobicents.isup2sip.commonlibs.Channel;
import org.mobicents.isup2sip.commonlibs.Channel.State;
import org.mobicents.isup2sip.commonlibs.RequestIsupResetsEvent;
import org.mobicents.isup2sip.commonlibs.RequestRsipEvent;

public class CicManagement {
	private static final Logger logger = Logger.getLogger(CicManagement.class.getName());

	private static final Object synchCic = new Object();
	protected static Map<Integer, Channel> channelByCic = new HashMap<Integer, Channel>();

	public CicManagement(){ //throws Exception {
		logger.warning("Isup2Sip cic management started");

		channelByCic.clear();
// DEBUG!!
//addMultiplex(0, "192.168.1.13:2427", 0);
	}

	public void addMultiplex(int multiplexId, String gateway, int multiplexPort){
		// create CICs in "unknown" state
		// fire NewMultiplexEvent to start corresponding Sbb
		logger.info("adding multiplex " + multiplexId + " gatetway " + gateway + " port " + multiplexPort);

		for(int ts = 1; ts <= 31; ts++){
			int cic = 32*multiplexId + ts;
			int ep = 32*multiplexPort + ts;
			Channel ch = new Channel(gateway, Integer.toHexString(ep), cic);
			
			// TODO this is Wrong! 
			// signaling timeslots should be detected by means of MGCP, not during a failure of a real SIP->ISUP call..
//ch.setState(State.IDLE);
			
			channelByCic.put(cic, ch);
			logger.info("adding channel" + ch.toString());
		}
	}
	
	public void resetMultiplex(int multiplexId){
		logger.warning("resetting multiplex #" + multiplexId);
		for(int ts = 1; ts <= 31; ts++){
//		for(int ts = 1; ts <= 2; ts++){		// just for debuf, to reduce logging
			int cic = 32*multiplexId + ts;
			Channel ch = channelByCic.get(cic);
			ch.setState(State.UNKNOWN);
			
			this.fireMgcpRsipEvent(ch);
		}
		this.fireIsupResetEvent(multiplexId);		
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
	
	public boolean setIdle(int cic) {
		synchronized (synchCic) {
			try {
				channelByCic.get(cic).setState(State.IDLE);
				return true;
			} catch (Exception e) {
				return false;	// if a channel is unequipped - does not exist 
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

	public boolean setBlocked(int cic) {
		synchronized (synchCic) {
			try {
				if(channelByCic.get(cic).getState()==State.IDLE){
					channelByCic.get(cic).setState(State.BLOCKED);
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
/*	
	public void resetChannels(Object obj, Context ctx, SbbLocalObject sbbLocalObject){
		logger.info("resetting channels");

		synchronized(synchCic) { 
			Iterator <Channel> i = null; i = channelByCic.values().iterator(); 
			while(i.hasNext()){
					Channel ch = i.next();
					if(ch.getState() == State.UNKNOWN){
						try {														
				            // hack:
							ch.setState(State.IDLE);

						} catch (Exception e) {
							logger.severe("exception " + e.getMessage());
						}
					}
			}
		}
	}
*/	
	private Channel getNeededState(Channel.State state){
		Iterator <Channel> i = null; i = channelByCic.values().iterator(); 
		while(i.hasNext()){
				Channel ch = i.next();
				if(ch.getState() == state) return ch; 
		}
		return null;
	}
	
	public void remove(int cic){
		channelByCic.remove(cic);
	}
	
    public void fireMgcpRsipEvent(final Channel ch) {
        Thread t = new Thread(new Runnable() {
        	public void run() {
        		try {
        			InitialContext ic = new InitialContext();

                    SleeConnectionFactory factory = (SleeConnectionFactory) ic.lookup("java:/MobicentsConnectionFactory");

                    SleeConnection conn = null;
                    conn = factory.getConnection();

                    ExternalActivityHandle handle = conn.createActivityHandle();
                    
                    EventTypeID requestType = conn.getEventTypeID("org.mobicents.isup2sip.commonlibs.REQ_RSIP", "org.mobicents", "1.0");
                    
                    RequestRsipEvent epEvent = new RequestRsipEvent(ch);
                    logger.info("firing event " + epEvent + " for " + ch + " ---- " + ch.getEndpointId() + " " + ch.getGatewayAddress());
                    
 //                   CustomEvent customEvent = new CustomEvent(orderId, amount, customerfullname, cutomerphone, userName);

                    conn.fireEvent(epEvent, requestType, handle, null);
                    conn.close();
        		} catch (Exception e) {
        			e.printStackTrace();
        		}
        	}
        });
        t.start();
        try {
        	t.join();
        } catch (Exception e) {
        	e.printStackTrace();
        }
    }

    public void fireIsupResetEvent(final int multiplexId) {
    	
        Thread t = new Thread(new Runnable() {
        	public void run() {
        		try {
        			InitialContext ic = new InitialContext();

                    SleeConnectionFactory factory = (SleeConnectionFactory) ic.lookup("java:/MobicentsConnectionFactory");

                    SleeConnection conn = null;
                    conn = factory.getConnection();

                    ExternalActivityHandle handle = conn.createActivityHandle();
                    
                    EventTypeID requestType = conn.getEventTypeID("org.mobicents.isup2sip.commonlibs.REQ_RSC_GRS", "org.mobicents", "1.0");
                    
                    RequestIsupResetsEvent isupEvent = new RequestIsupResetsEvent(multiplexId);
                    
 //                   CustomEvent customEvent = new CustomEvent(orderId, amount, customerfullname, cutomerphone, userName);

                    conn.fireEvent(isupEvent, requestType, handle, null);
                    conn.close();
        		} catch (Exception e) {
        			e.printStackTrace();
                }
       
        	}
        });
        t.start();
        try {
        	t.join();
        } catch (Exception e) {
        	e.printStackTrace();
        }
    }    
}
