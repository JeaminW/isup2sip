/*
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

package org.mobicents.isup2sip.sbb;

import jain.protocol.ip.mgcp.JainMgcpEvent;
import jain.protocol.ip.mgcp.message.CreateConnection;
import jain.protocol.ip.mgcp.message.CreateConnectionResponse;
import jain.protocol.ip.mgcp.message.parms.CallIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConflictingParameterException;
import jain.protocol.ip.mgcp.message.parms.ConnectionDescriptor;
import jain.protocol.ip.mgcp.message.parms.ConnectionMode;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.ReturnCode;

import java.text.ParseException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sip.ClientTransaction;
import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
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

import net.java.slee.resource.mgcp.JainMgcpProvider;
import net.java.slee.resource.mgcp.MgcpActivityContextInterfaceFactory;
import net.java.slee.resource.mgcp.MgcpConnectionActivity;
import net.java.slee.resource.sip.CancelRequestEvent;
import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

import org.mobicents.isup2sip.management.Channel;
import org.mobicents.isup2sip.management.CicManagement;
import org.mobicents.isup2sip.management.Isup2SipPropertiesManagement;
//import org.mobicents.isup2sip.sbb.CauseCodeMapping;
import org.mobicents.isup2sip.sbb.CodingShemes;
import org.mobicents.protocols.ss7.isup.ISUPMessageFactory;
import org.mobicents.protocols.ss7.isup.ISUPParameterFactory;
import org.mobicents.protocols.ss7.isup.message.AddressCompleteMessage;
import org.mobicents.protocols.ss7.isup.message.AnswerMessage;
import org.mobicents.protocols.ss7.isup.message.InitialAddressMessage;
import org.mobicents.protocols.ss7.isup.message.ReleaseCompleteMessage;
import org.mobicents.protocols.ss7.isup.message.ReleaseMessage;
import org.mobicents.protocols.ss7.isup.message.parameter.CalledPartyNumber;
import org.mobicents.protocols.ss7.isup.message.parameter.CallingPartyCategory;
import org.mobicents.protocols.ss7.isup.message.parameter.CallingPartyNumber;
import org.mobicents.protocols.ss7.isup.message.parameter.CircuitIdentificationCode;
import org.mobicents.protocols.ss7.isup.message.parameter.ForwardCallIndicators;
import org.mobicents.protocols.ss7.isup.message.parameter.NatureOfConnectionIndicators;
import org.mobicents.protocols.ss7.isup.message.parameter.TransmissionMediumRequirement;
import org.mobicents.slee.resources.ss7.isup.ratype.CircuitActivity;
import org.mobicents.slee.resources.ss7.isup.ratype.RAISUPProvider;

/**
 * 
 * @author dmitri soloviev
 */
public abstract class Isup2SipSbb implements javax.slee.Sbb {
	private SbbContext sbbContext;
	private static Tracer tracer;

	// SIP
	private SleeSipProvider sipProvider;
	private SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory;
	private HeaderFactory headerFactory;
	//private AddressFactory addressFactory;
	private MessageFactory messageFactory;
	
	// MGCP
	private JainMgcpProvider mgcpProvider;
	private MgcpActivityContextInterfaceFactory mgcpActivityContestInterfaceFactory;
	
	// ISUP
    protected RAISUPProvider isupProvider;
    protected ISUPMessageFactory isupMessageFactory;
    protected ISUPParameterFactory isupParameterFactory;
    protected org.mobicents.slee.resources.ss7.isup.ratype.ActivityContextInterfaceFactory isupActivityContextInterfaceFactory;
    
    private static final Isup2SipPropertiesManagement isup2SipPropertiesManagement = 
    		Isup2SipPropertiesManagement.getInstance();
    
    private final CicManagement cicManagement = isup2SipPropertiesManagement.getCicManagement();
    
    private final int remoteSPC = isup2SipPropertiesManagement.getRemoteSPC();

    public Isup2SipSbb(){ }
    
	// Initial request
	public void onInviteEvent(RequestEvent sipEvent, ActivityContextInterface aci) {
		
		if(this.getSipEvent() != null){
			tracer.warning("Re-Invite event");
			onReInviteEvent(sipEvent, aci);
			return;
		}
		
		tracer.warning("(primary) Invite event");
		this.setSipEvent(sipEvent);
		
		// ACI is the server transaction activity
		try {
			// try to allocate CIC
			final Channel channel = cicManagement.getIdleChannel();
			if(channel == null) {
				tracer.warning("Failed to allocate CIC for Invite");
				sipReplyToRequestEvent(sipEvent, Response.SERVICE_UNAVAILABLE);
				return;
			}
			
			this.setCicValue(channel.getCic());
			
			// Create SIP dialog
			final DialogActivity sipDialog = (DialogActivity) sipProvider.getNewDialog(sipEvent.getServerTransaction());
			final ActivityContextInterface sipDialogACI = sipActivityContextInterfaceFactory.getActivityContextInterface(sipDialog);
			final SbbLocalObject sbbLocalObject = sbbContext.getSbbLocalObject();
			sipDialogACI.attach(sbbLocalObject);
			
			// send "trying" response
			sipReplyToRequestEvent(sipEvent, Response.TRYING);
			
			String sdp = new String(sipEvent.getRequest().getRawContent());
			
			CallIdentifier mgcpCallID = mgcpProvider.getUniqueCallIdentifier();
			this.setMgcpCallIdentifier(mgcpCallID.toString());
			EndpointIdentifier endpointID = new EndpointIdentifier(channel.getEndpointId(),channel.getGatewayAddress());
			CreateConnection createConnection = new CreateConnection(this,
					mgcpCallID, endpointID, ConnectionMode.SendRecv);
			try {
				createConnection.setRemoteConnectionDescriptor(new ConnectionDescriptor(sdp));
			} catch (ConflictingParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			int txID = mgcpProvider.getUniqueTransactionHandler();
			createConnection.setTransactionHandle(txID);
			
			MgcpConnectionActivity connectionActivity = null;
			try {
				connectionActivity = mgcpProvider.getConnectionActivity(txID, endpointID);
				ActivityContextInterface epnAci = mgcpActivityContestInterfaceFactory.getActivityContextInterface(connectionActivity);
				epnAci.attach(sbbLocalObject);
			} catch (FactoryException ex) {
				ex.printStackTrace();
			} catch (NullPointerException ex) {
				ex.printStackTrace();
			} catch (UnrecognizedActivityException ex) {
				ex.printStackTrace();
			}
			
			mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { createConnection });
			tracer.info("SIP->ISUP, CRCX sent; ep ID=" + endpointID + " sbb=" + sbbLocalObject);

			// with a proper CRCX_RESP, sent IAM..
			
		} catch (Throwable e) {
			tracer.severe("Failed to process incoming INVITE.", e);
			sipReplyToRequestEvent(sipEvent, Response.SERVICE_UNAVAILABLE);
		}
	}

	public void onIAM(InitialAddressMessage iam, ActivityContextInterface aci){
		
		tracer.warning("isup IAM " + iam.getCircuitIdentificationCode().getCIC());
		int cic = iam.getCircuitIdentificationCode().getCIC();
		this.setCicValue(cic);
		showMe();
		
		
		if(! cicManagement.setBusy(this.getCicValue())){
			// stop a call. 
			tracer.warning("CIC is not idle");
			
		}
		// just dummy code now
		AddressCompleteMessage msg = isupMessageFactory.createACM(this.getCicValue());
        msg.setSls(this.getCicValue());

        try {
        	CircuitActivity circuitActivity = isupProvider.createCircuitActivity(msg,remoteSPC);
        	ActivityContextInterface cicAci = isupActivityContextInterfaceFactory.getActivityContextInterface(circuitActivity);
        	cicAci.attach(sbbContext.getSbbLocalObject());
        	circuitActivity.sendMessage(msg);
            
//        	isupProvider.sendMessage(msg,remoteSPC);
            } catch (Exception e) {
            	// TODO Auto-generated catch block
        		e.printStackTrace();
        }
	}
	
	
	
	
	public void onANM(AnswerMessage anm, ActivityContextInterface aci){
		tracer.warning("isup ANM");
		showMe();
		
		sipReplyToRequestEvent(this.getSipEvent(), Response.OK);
		
		cicManagement.setAnswered(this.getCicValue());
	}
	
	public void onACM(AddressCompleteMessage isupEvent, ActivityContextInterface aci){
		tracer.warning("isup ACM");
		showMe();
		
		sipReplyToRequestEvent(this.getSipEvent(), Response.RINGING);
		
		cicManagement.setAnswered(this.getCicValue());
	}	
	
	public void onREL(ReleaseMessage rel, ActivityContextInterface aci){
		tracer.warning("isup REL");
		showMe();
	}
	
	public void onRLC(ReleaseCompleteMessage isupEvent, ActivityContextInterface aci){
		tracer.warning("isup REL");
		showMe();
	}
	


	public void onCreateConnectionResponse(CreateConnectionResponse event,
			ActivityContextInterface aci) {
		tracer.info("CRCX RESP sbb=" + sbbContext.getSbbLocalObject());
		
		ReturnCode status = event.getReturnCode();

		boolean connectionCreated = (status.getValue() == ReturnCode.TRANSACTION_EXECUTED_NORMALLY);
		String sdp = null;
		
		if(connectionCreated) {
			sdp = event.getLocalConnectionDescriptor().toString();
			connectionCreated = (sdp!=null);
		}
		if(connectionCreated){
			/* the following should be done: 
			 * (1) SIP: send progress with sdp
			 * (2) ISUP: send invite
			 */
			ContentTypeHeader contentType = null;
			try {
				contentType = headerFactory.createContentTypeHeader("application", "sdp");
			} catch (ParseException ex) {}
			
			Response response = null;
			try {
				response = messageFactory.createResponse(Response.SESSION_PROGRESS, getSipEvent().getRequest(), contentType, sdp.getBytes());
			} catch (ParseException ex) {
				tracer.warning("ParseException while trying to create SESSION_PROGRESS Response", ex);
			}
			
			// fetch A- and B- numbers
			Request sipRequest = this.getSipEvent().getRequest();
			FromHeader fromHeader = (FromHeader) sipRequest.getHeader(FromHeader.NAME);
			ToHeader toHeader = (ToHeader) sipRequest.getHeader(ToHeader.NAME);
			final String aNumber = CodingShemes.numberFromURI(fromHeader.getAddress().getURI().toString());
			final String bNumber = CodingShemes.numberFromURI(toHeader.getAddress().getURI().toString());
			tracer.warning("To   header=" + toHeader + " ->" + bNumber);
			tracer.warning("From header=" + fromHeader + " ->" + aNumber);
			
			InitialAddressMessage msg = isupMessageFactory.createIAM(this.getCicValue());
//			CircuitIdentificationCode cic = isupParameterFactory.createCircuitIdentificationCode();
            NatureOfConnectionIndicators nai = isupParameterFactory.createNatureOfConnectionIndicators();
            ForwardCallIndicators fci = isupParameterFactory.createForwardCallIndicators();
            CallingPartyCategory cpg = isupParameterFactory.createCallingPartyCategory();
            TransmissionMediumRequirement tmr = isupParameterFactory.createTransmissionMediumRequirement();
            CalledPartyNumber cpn = isupParameterFactory.createCalledPartyNumber();
            CallingPartyNumber cgp = isupParameterFactory.createCallingPartyNumber();
            cpn.setAddress(bNumber);
            cgp.setAddress(aNumber);
            msg.setNatureOfConnectionIndicators(nai);
            msg.setForwardCallIndicators(fci);
            msg.setCallingPartCategory(cpg);
            msg.setCalledPartyNumber(cpn);
            msg.setCallingPartyNumber(cgp);
            msg.setTransmissionMediumRequirement(tmr);
            msg.setSls(this.getCicValue());

            try {
            	CircuitActivity circuitActivity = isupProvider.createCircuitActivity(msg,remoteSPC);
            	ActivityContextInterface cicAci = isupActivityContextInterfaceFactory.getActivityContextInterface(circuitActivity);
            	cicAci.attach(sbbContext.getSbbLocalObject());
            	circuitActivity.sendMessage(msg);
                
//            	isupProvider.sendMessage(msg,remoteSPC);
                } catch (Exception e) {
                	// TODO Auto-generated catch block
            		e.printStackTrace();
            }
       
			try {
				getSipEvent().getServerTransaction().sendResponse(response);
			} catch (InvalidArgumentException ex) {
				tracer.warning("InvalidArgumentException while trying to send SESSION_PROGRESS Response (with sdp)", ex);
			} catch (SipException ex) {
				tracer.warning("SipException while trying to send SESSION_PROGRESS Response (with sdp)", ex);
			}
		}
		else {
			/* unable to create voice path (that's strange), so
			 * mark CIC as IDLE
			 * SIP: send SERVICE_UNAVAILABLE
//!!!!!!!	 * detach MGCP
			 */
			cicManagement.setIdle(this.getCicValue());
			
			try {
				Response response = messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, getSipEvent().getRequest());
				getSipEvent().getServerTransaction().sendResponse(response);
			} catch (Exception ex) {
				tracer.warning("Exception while trying to send SERVER_INTERNAL_ERROR Response", ex);
			}
		}
	}

	public void onModifyConnectionResponse(CreateConnectionResponse event,
			ActivityContextInterface aci) {}

	public void onDeleteConnectionResponse(CreateConnectionResponse event,
				ActivityContextInterface aci) {}

	
	
	
	

	public void onReInviteEvent(RequestEvent sipEvent, ActivityContextInterface aci) {

	// Responses
	public void on1xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		if (event.getResponse().getStatusCode() == Response.TRYING) {
			// those are not forwarded to the other dialog
			return;
		}
		processResponse(event, aci);
	}

	public void on2xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		final CSeqHeader cseq = (CSeqHeader) event.getResponse().getHeader(
				CSeqHeader.NAME);
		if (cseq.getMethod().equals(Request.INVITE)) {
			// lets ack it ourselves to avoid UAS retransmissions due to
			// forwarding of this response and further UAC Ack
			// note that the app does not handles UAC ACKs
			try {
				final Request ack = event.getDialog().createAck(
						cseq.getSeqNumber());
				event.getDialog().sendAck(ack);
			} catch (Exception e) {
				tracer.severe("Unable to ack INVITE's 200 ok from UAS", e);
			}
		} else if (cseq.getMethod().equals(Request.BYE)
				|| cseq.getMethod().equals(Request.CANCEL)) {
			// not forwarded to the other dialog
			return;
		}
		processResponse(event, aci);
	}

	public void onBye(RequestEvent event, ActivityContextInterface aci) {

	}

	public void onCancel(CancelRequestEvent event, ActivityContextInterface aci) {
		if (tracer.isInfoEnabled()) {
			tracer.info("Got a CANCEL request.");
		}
		
		try {
			
		} catch (Exception e) {
			tracer.severe("Failed to process cancel request", e);
		}
	}

	// Other mid-dialog requests handled the same way as above
	// Helpers

	private void sipReplyToRequestEvent(RequestEvent event, int status) {
		try {
			event.getServerTransaction().sendResponse(
					sipProvider.getMessageFactory().createResponse(status,
							event.getRequest()));
		} catch (Throwable e) {
			tracer.severe("Failed to reply to request event:\n" + event, e);
		}
	}

	private void processResponse(ResponseEvent event,
			ActivityContextInterface aci) {
		try {

		} catch (Exception e) {
			tracer.severe(e.getMessage(), e);
		}
	}
	
	

	
	
	public abstract void setCicValue(int cicValue);

	public abstract int getCicValue();

	public abstract void setSipEvent(RequestEvent sipEvent);

	public abstract RequestEvent getSipEvent();
	
	public abstract void setMgcpCallIdentifier(String mgcpCallId);
	
	public abstract String getMgcpCallIdentifier();
	
	public abstract void setMgcpConnectionIdentifier(String mgcpConnId);
	
	public abstract String getMgcpConnectionIdentifier();
	
	
	public void setSbbContext(SbbContext context) {
		this.sbbContext = context;
		if (tracer == null) {
			tracer = sbbContext.getTracer(Isup2SipSbb.class
					.getSimpleName());
		}
		try {
			tracer.severe("trying to start!!!!");
			final Context ctx = (Context) new InitialContext().lookup("java:comp/env");
	
			// SIP
			sipActivityContextInterfaceFactory = (SipActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainsip/1.2/acifactory");
			sipProvider = (SleeSipProvider) ctx.lookup("slee/resources/jainsip/1.2/provider");
			//addressFactory = provider.getAddressFactory();
			headerFactory = sipProvider.getHeaderFactory();
			messageFactory = sipProvider.getMessageFactory();
			
			// MGCP
			mgcpProvider = (JainMgcpProvider) ctx.lookup("slee/resources/jainmgcp/2.0/provider");
			mgcpActivityContestInterfaceFactory = (MgcpActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainmgcp/2.0/acifactory");
			
			// ISUP
            isupProvider = (RAISUPProvider) ctx.lookup("slee/resources/isup/1.0/provider");
            isupMessageFactory = isupProvider.getMessageFactory();
            isupParameterFactory = isupProvider.getParameterFactory();
            isupActivityContextInterfaceFactory = (org.mobicents.slee.resources.ss7.isup.ratype.ActivityContextInterfaceFactory) ctx.lookup("slee/resources/isup/1.0/acifactory");
								
		} catch (NamingException e) {
			tracer.severe(e.getMessage(), e);
		}
	}


	
	public void showMe(){
		tracer.warning("object: " + sbbContext.getSbbLocalObject() 
				+ " cic=" + this.getCicValue()
				+ " sipEvent=" + this.getSipEvent());
	}
	
	
	
	
	
	public void unsetSbbContext() {
		this.sbbContext = null;
		this.sipActivityContextInterfaceFactory = null;
		this.sipProvider = null;
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
