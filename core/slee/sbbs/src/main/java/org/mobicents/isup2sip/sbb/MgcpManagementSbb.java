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
import jain.protocol.ip.mgcp.message.parms.CallIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConflictingParameterException;
import jain.protocol.ip.mgcp.message.parms.ConnectionDescriptor;
import jain.protocol.ip.mgcp.message.parms.ConnectionIdentifier;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.RestartMethod;
import jain.protocol.ip.mgcp.message.parms.ReturnCode;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.slee.ActivityContextInterface;
import javax.slee.FactoryException;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;
import javax.slee.SbbLocalObject;
import javax.slee.UnrecognizedActivityException;
import javax.slee.facilities.Tracer;
import javax.slee.serviceactivity.ServiceStartedEvent;

import net.java.slee.resource.mgcp.JainMgcpProvider;
import net.java.slee.resource.mgcp.MgcpActivityContextInterfaceFactory;
import net.java.slee.resource.mgcp.MgcpConnectionActivity;
import net.java.slee.resource.mgcp.event.TransactionTimeout;

import org.mobicents.isup2sip.commonlibs.Channel;
import org.mobicents.isup2sip.commonlibs.RequestRsipEvent;
import org.mobicents.isup2sip.management.CicManagement;
import org.mobicents.isup2sip.management.Isup2SipManagement;
import org.mobicents.isup2sip.management.Isup2SipPropertiesManagement;

/**
 * @author dmitri soloviev
 * 
 */

public abstract class MgcpManagementSbb implements javax.slee.Sbb {
	private SbbContext sbbContext;
	private static Tracer tracer;
	
	// MGCP
	private JainMgcpProvider mgcpProvider;
	private MgcpActivityContextInterfaceFactory mgcpActivityContestInterfaceFactory;
	
    private static final Isup2SipPropertiesManagement isup2SipPropertiesManagement = 
    		Isup2SipPropertiesManagement.getInstance();
    
    private final CicManagement cicManagement = isup2SipPropertiesManagement.getCicManagement();

    private final int remoteSPC = isup2SipPropertiesManagement.getRemoteSPC();
   
    public void onRequestRsipEvent(RequestRsipEvent event,  ActivityContextInterface aci){
    	tracer.info("MGCP Management Sbb started " + event);
    	
    	final SbbLocalObject sbbLocalObject = sbbContext.getSbbLocalObject();
    	final Channel channel = event.getChannel();
    	
		EndpointIdentifier endpointID = new EndpointIdentifier(channel.getEndpointId(),channel.getGatewayAddress());
		this.setEndpointIdentifier(endpointID);
		RestartInProgress rsip = new RestartInProgress(this, endpointID, RestartMethod.Graceful);

		final int txID = mgcpProvider.getUniqueTransactionHandler();
		rsip.setTransactionHandle(txID);
		
		MgcpConnectionActivity connectionActivity = mgcpProvider.getConnectionActivity(txID, endpointID);
		ActivityContextInterface epnAci = mgcpActivityContestInterfaceFactory.getActivityContextInterface(connectionActivity);
		epnAci.attach(sbbLocalObject);
								
		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { rsip });
		tracer.info("RSIP sent; ep ID=" + endpointID + " sbb=" + sbbLocalObject);
    }
    
	public void onRestartInProgressResponse(RestartInProgressResponse event, ActivityContextInterface aci) {
		tracer.info("RSIP RESP " + event);
		
		final EndpointIdentifier endpointID = this.getEndpointIdentifier();
		final int cic = this.getCic(endpointID);
		
		if(cic == -1) {
			tracer.severe("invalid endpoint id " + endpointID);
			aci.detach(sbbContext.getSbbLocalObject());
			return;
		}
		
		ReturnCode status = event.getReturnCode();

		if(status.getValue() != ReturnCode.TRANSACTION_EXECUTED_NORMALLY){
			tracer.warning("removing unequipped endpoint id " + endpointID);
			cicManagement.remove(cic);
		}
		
		//cicManagement.setIdle(cic);
		aci.detach(sbbContext.getSbbLocalObject());
	}	
	
	public void onMgcpTIMEOUT(TransactionTimeout event, ActivityContextInterface aci){
		tracer.severe("Mgcp Timeout" + event);
		
		final JainMgcpCommandEvent mgcpEvent = event.getJainMgcpCommandEvent();
		final EndpointIdentifier endpointID = mgcpEvent.getEndpointIdentifier();
		final int cic = this.getCic(endpointID);
		
		if(cic == -1) {
			tracer.severe("invalid endpoint id " + endpointID);
			aci.detach(sbbContext.getSbbLocalObject());
			return;
		}
		
		if(mgcpEvent instanceof RestartInProgress){
			tracer.severe("no resp for RSIP " + endpointID);
			cicManagement.remove(cic);
		}
		aci.detach(sbbContext.getSbbLocalObject());
	}

	public void onServiceStartedEvent(ServiceStartedEvent event, ActivityContextInterface aci){
		tracer.severe("service started");
		isup2SipPropertiesManagement.registerMgcpManagement();
		aci.detach(sbbContext.getSbbLocalObject());
	}
		
	
	
	public abstract void setEndpointIdentifier(EndpointIdentifier endpointID);

	public abstract EndpointIdentifier getEndpointIdentifier();
	
	
	
	public void setSbbContext(SbbContext context) {
		this.sbbContext = context;
		if (tracer == null) {
			tracer = sbbContext.getTracer(MgcpManagementSbb.class
					.getSimpleName());
		}
		try {	
			final Context ctx = (Context) new InitialContext().lookup(Isup2SipManagement.CONTEXT);
			// MGCP
			mgcpProvider = (JainMgcpProvider) ctx.lookup(Isup2SipManagement.MGCP_PROVIDER);
			mgcpActivityContestInterfaceFactory = (MgcpActivityContextInterfaceFactory) ctx.lookup(Isup2SipManagement.MGCP_ACI_FACTORY);
			
		} catch (NamingException e) {
			tracer.severe(e.getMessage(), e);
		}
	}

	public void unsetSbbContext() {
		this.sbbContext = null;

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
	
	private int getCic(EndpointIdentifier epId){
		String epStr = epId.toString();
		int pos = epStr.indexOf("@");
		if(pos == -1) return pos;
		return Integer.parseInt(epStr.substring(0, pos), 16);
	}
}
