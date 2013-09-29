/**
 * TeleStax, Open Source Cloud Communications  Copyright 2012. 
 * and individual contributors
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

package org.mobicents.isup2sip.sbb;

import jain.protocol.ip.mgcp.JainMgcpCommandEvent;
import jain.protocol.ip.mgcp.JainMgcpEvent;
import jain.protocol.ip.mgcp.JainMgcpResponseEvent;
import jain.protocol.ip.mgcp.message.RestartInProgress;
import jain.protocol.ip.mgcp.message.RestartInProgressResponse;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.slee.ActivityContextInterface;
import javax.slee.FactoryException;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;
import javax.slee.SbbLocalObject;
import javax.slee.UnrecognizedActivityException;
import javax.slee.facilities.TimerEvent;
import javax.slee.facilities.TimerFacility;
import javax.slee.facilities.TimerOptions;
import javax.slee.facilities.TimerPreserveMissed;
import javax.slee.facilities.Tracer;
import javax.slee.serviceactivity.ServiceStartedEvent;

import net.java.slee.resource.mgcp.event.TransactionTimeout;

import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

import org.mobicents.isup2sip.commonlibs.Channel;
import org.mobicents.isup2sip.commonlibs.RequestIsupResetsEvent;
import org.mobicents.isup2sip.management.CicManagement;
import org.mobicents.isup2sip.management.Isup2SipManagement;
import org.mobicents.isup2sip.management.Isup2SipPropertiesManagement;

import org.mobicents.protocols.ss7.isup.ISUPMessageFactory;
import org.mobicents.protocols.ss7.isup.ISUPParameterFactory;

import org.mobicents.protocols.ss7.isup.message.BlockingAckMessage;
import org.mobicents.protocols.ss7.isup.message.BlockingMessage;
import org.mobicents.protocols.ss7.isup.message.ReleaseCompleteMessage;
import org.mobicents.protocols.ss7.isup.message.ResetCircuitMessage;
import org.mobicents.protocols.ss7.isup.message.UnblockingAckMessage;
import org.mobicents.protocols.ss7.isup.message.UnblockingMessage;
import org.mobicents.protocols.ss7.isup.message.UnequippedCICMessage;

import org.mobicents.slee.resources.ss7.isup.ratype.RAISUPProvider;

/**
 * @author dmitri soloviev
 * 
 */
public abstract class IsupManagementSbb implements javax.slee.Sbb {
	private SbbContext sbbContext;
	private TimerFacility timerFacility = null;
	private static Tracer tracer;
		
	protected RAISUPProvider isupProvider;
    protected ISUPMessageFactory isupMessageFactory;
    protected ISUPParameterFactory isupParameterFactory;
    protected org.mobicents.slee.resources.ss7.isup.ratype.ActivityContextInterfaceFactory isupActivityContextInterfaceFactory;
    
    private Isup2SipPropertiesManagement isup2SipPropertiesManagement = 
    		Isup2SipPropertiesManagement.getInstance();
    
    private CicManagement cicManagement = isup2SipPropertiesManagement.getCicManagement();
    
    private int remoteSPC = isup2SipPropertiesManagement.getRemoteSPC();
    
    public IsupManagementSbb(){ }
    
    // Initial event
    public void onRequestIsupResetsEvent(RequestIsupResetsEvent event, ActivityContextInterface aci){
    	
    	tracer.warning("ISUP reset requested for some multiplex");
    	
    	final int mux = event.getMultiplexId();
    	this.setMultiplex(mux);
    	
    	tracer.warning("ISUP reset requested for multiplex " + mux);
    	
    	TimerOptions options = new TimerOptions();
        //options.setPreserveMissed(TimerPreserveMissed.ALL);
		this.timerFacility.setTimer(aci, null, System.currentTimeMillis() + isup2SipPropertiesManagement.getTimeoutBeforeIsupReset(), options);
    }
	
    public void onTimerEvent(RequestIsupResetsEvent event, ActivityContextInterface aci){
    	// fire various restart messages
    	final int mux = this.getMultiplex();
    	String res = new String();
    	for(int ts=1;ts<=31;ts++){
    		int cic = mux*32+ts;
    		if(cicManagement.getChannelByCic(cic).equals(Channel.State.UNKNOWN))
    			res.concat(" " + cic);
    		else {
    			tracer.warning("group: " + res);
    			res = new String();
    		}
    	}
    	if(! res.isEmpty()) tracer.warning("group: " + res);
    }
    
	// ISUP events
    // Initial event
	public void onRSC(ResetCircuitMessage rsc, ActivityContextInterface aci){
		final int cic = rsc.getCircuitIdentificationCode().getCIC();
		tracer.warning("isup RSC " + cic);
		
		if(cicManagement.setIdle(cic)) sendRLC(cic);
		else sendUCIC(cic);
		aci.detach(sbbContext.getSbbLocalObject());
	}
	
	// Initial event
	public void onRLC(ReleaseCompleteMessage rlc, ActivityContextInterface aci){
		final int cic = rlc.getCircuitIdentificationCode().getCIC();
		tracer.warning("isup RLC, as a resp to RSC " + cic);
		cicManagement.setIdle(cic);
		aci.detach(sbbContext.getSbbLocalObject());
	}
	
    // Initial event
	public void onBLO(BlockingMessage blo, ActivityContextInterface aci){
		final int cic = blo.getCircuitIdentificationCode().getCIC();
		tracer.warning("isup UBL " + cic);
		if(cicManagement.setBlocked(cic)) sendBLA(cic);
		aci.detach(sbbContext.getSbbLocalObject());
	}
	
	public void onBLA(BlockingAckMessage bla, ActivityContextInterface aci){

	}

    // Initial event
	public void onUBL(UnblockingMessage ubl, ActivityContextInterface aci){
		final int cic = ubl.getCircuitIdentificationCode().getCIC();
		tracer.warning("isup UBL " + cic);
		if(cicManagement.setIdle(cic)) sendUBA(cic); 
		aci.detach(sbbContext.getSbbLocalObject());
	}
	
	public void onUBA(UnblockingAckMessage uba, ActivityContextInterface aci){

	}
	
	public void onServiceStartedEvent(ServiceStartedEvent event, ActivityContextInterface aci){
		tracer.severe("-- service started");
//		isup2SipPropertiesManagement.registerIsupManagement();
		aci.detach(sbbContext.getSbbLocalObject());
	}

	
	public void setSbbContext(SbbContext context) {
		this.sbbContext = context;
		if (tracer == null) {
			tracer = sbbContext.getTracer(IsupManagementSbb.class.getSimpleName());
		}
		Context ctx = null;
		
		try{
			tracer.severe("trying to start!!!!");
			ctx = (Context) new InitialContext().lookup(Isup2SipManagement.CONTEXT);
		} catch (Exception e) {
			tracer.severe("error setting context" + e);
		}
		
		try {
			timerFacility = (TimerFacility) ctx.lookup(TimerFacility.JNDI_NAME);
		} catch (Exception e) {
			tracer.severe("error configuring timer" + e);
		}
		
		try{
			// ISUP
            isupProvider = (RAISUPProvider) ctx.lookup("slee/resources/isup/1.0/provider");
            isupMessageFactory = isupProvider.getMessageFactory();
            isupParameterFactory = isupProvider.getParameterFactory();
            isupActivityContextInterfaceFactory = (org.mobicents.slee.resources.ss7.isup.ratype.ActivityContextInterfaceFactory) ctx.lookup("slee/resources/isup/1.0/acifactory");
								
		} catch (Exception e) {
			tracer.severe("error configuring isup" + e);
		}
		tracer.warning("contex is set ---- ");
	}

	public void unsetSbbContext() {
		this.sbbContext = null;
		this.tracer = null;
		this.timerFacility = null;
	}
	
	public void sbbCreate() throws javax.slee.CreateException {
	}

	public void sbbPostCreate() throws javax.slee.CreateException {
	}

	public void sbbActivate() {
	}

	public void sbbPassivate() {
	}

	public void sbbRemove() {
	}

	public void sbbLoad() {
	}

	public void sbbStore() {
	}

	public void sbbExceptionThrown(Exception exception, Object event,
			ActivityContextInterface activity) {
	}

	public void sbbRolledBack(RolledBackContext context) {
	}
	
	
	
	public abstract void setMultiplex(int mux);
	
	public abstract int getMultiplex();
	
	
	
	void sendRLC(int cic){
		ReleaseCompleteMessage msg = isupMessageFactory.createRLC(cic);
        msg.setSls(cic);

        try {
        	// just to play with stack, send smth
        	isupProvider.sendMessage(msg,remoteSPC);
            } catch (Exception e) {
            	// TODO Auto-generated catch block
        		e.printStackTrace();
        }
	}
	
	void sendUCIC(int cic){
		UnequippedCICMessage msg = isupMessageFactory.createUCIC(cic);
        msg.setSls(cic);

        try {
        	// just to play with stack, send smth
        	isupProvider.sendMessage(msg,remoteSPC);
            } catch (Exception e) {
            	// TODO Auto-generated catch block
        		e.printStackTrace();
        }
	}
	
	void sendBLA(int cic){
		BlockingAckMessage msg = isupMessageFactory.createBLA(cic);
        msg.setSls(cic);

        try {
        	// just to play with stack, send smth
        	isupProvider.sendMessage(msg,remoteSPC);
            } catch (Exception e) {
            	// TODO Auto-generated catch block
        		e.printStackTrace();
        }
	}
	
	void sendUBA(int cic){
		UnblockingAckMessage msg = isupMessageFactory.createUBA(cic);
        msg.setSls(cic);

        try {
        	// just to play with stack, send smth
        	isupProvider.sendMessage(msg,remoteSPC);
            } catch (Exception e) {
            	// TODO Auto-generated catch block
        		e.printStackTrace();
        }
	}
}
