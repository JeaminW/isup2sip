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

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import javax.slee.ActivityContextInterface;
import javax.slee.FactoryException;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;
import javax.slee.SbbLocalObject;
import javax.slee.UnrecognizedActivityException;
import javax.slee.facilities.Tracer;
import javax.slee.serviceactivity.ServiceStartedEvent;

import org.mobicents.isup2sip.management.CicManagement;
import org.mobicents.isup2sip.management.Isup2SipPropertiesManagement;

import net.java.slee.resource.mgcp.event.TransactionTimeout;

import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

//import org.mobicents.isup2sip.management.Isup2SipManagement;
//import org.mobicents.isup2sip.management.Isup2SipPropertiesManagement;

/**
 * @author dmitri soloviev
 * 
 */
public abstract class SipManagementSbb implements javax.slee.Sbb {
	private SbbContext sbbContext;
	private static Tracer tracer;
	
	private SleeSipProvider sipProvider;
	private SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory;
	private HeaderFactory headerFactory;
	private MessageFactory messageFactory;
	   
    // SIP stuff
    // Initial event
    // TODO Now it just makes softphones happy. Later on it might make sense to keep pairs DID<->SIP peer
	public void onRegisterEvent(RequestEvent sipEvent, ActivityContextInterface aci) {
		tracer.info("registering");
		try {
			sipEvent.getServerTransaction().sendResponse(
					sipProvider.getMessageFactory().createResponse(Response.OK,
							sipEvent.getRequest()));
		} catch (Throwable e) {
			tracer.severe("Failed to reply to request event:\n" + sipEvent, e);
		}
		aci.detach(sbbContext.getSbbLocalObject());
	}    
	
	public void onServiceStartedEvent(ServiceStartedEvent event, ActivityContextInterface aci){	
		tracer.warning("service started");
		aci.detach(sbbContext.getSbbLocalObject());
	}	
	
	public void setSbbContext(SbbContext context) {
		this.sbbContext = context;
		if (tracer == null) {
			tracer = sbbContext.getTracer(SipManagementSbb.class.getSimpleName());
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
}
