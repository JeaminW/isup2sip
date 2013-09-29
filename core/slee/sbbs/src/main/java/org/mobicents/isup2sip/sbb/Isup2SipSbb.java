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
import jain.protocol.ip.mgcp.message.DeleteConnection;
import jain.protocol.ip.mgcp.message.DeleteConnectionResponse;
import jain.protocol.ip.mgcp.message.ModifyConnection;
import jain.protocol.ip.mgcp.message.ModifyConnectionResponse;
import jain.protocol.ip.mgcp.message.parms.CallIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConflictingParameterException;
import jain.protocol.ip.mgcp.message.parms.ConnectionDescriptor;
import jain.protocol.ip.mgcp.message.parms.ConnectionIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConnectionMode;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.ReturnCode;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

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

import net.java.slee.resource.mgcp.JainMgcpProvider;
import net.java.slee.resource.mgcp.MgcpActivityContextInterfaceFactory;
import net.java.slee.resource.mgcp.MgcpConnectionActivity;
import net.java.slee.resource.mgcp.event.TransactionTimeout;
import net.java.slee.resource.sip.CancelRequestEvent;
import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

import org.mobicents.isup2sip.commonlibs.Channel;
import org.mobicents.isup2sip.management.CicManagement;
import org.mobicents.isup2sip.management.Isup2SipManagement;
import org.mobicents.isup2sip.management.Isup2SipPropertiesManagement;
import org.mobicents.isup2sip.sbb.CodingShemes;
import org.mobicents.protocols.ss7.isup.ISUPMessageFactory;
import org.mobicents.protocols.ss7.isup.ISUPParameterFactory;
import org.mobicents.protocols.ss7.isup.ISUPTimeoutEvent;
import org.mobicents.protocols.ss7.isup.message.AddressCompleteMessage;
import org.mobicents.protocols.ss7.isup.message.AnswerMessage;
import org.mobicents.protocols.ss7.isup.message.InitialAddressMessage;
import org.mobicents.protocols.ss7.isup.message.ReleaseCompleteMessage;
import org.mobicents.protocols.ss7.isup.message.ReleaseMessage;
import org.mobicents.protocols.ss7.isup.message.parameter.BackwardCallIndicators;
import org.mobicents.protocols.ss7.isup.message.parameter.CalledPartyNumber;
import org.mobicents.protocols.ss7.isup.message.parameter.CallingPartyCategory;
import org.mobicents.protocols.ss7.isup.message.parameter.CallingPartyNumber;
import org.mobicents.protocols.ss7.isup.message.parameter.CauseIndicators;
import org.mobicents.protocols.ss7.isup.message.parameter.CircuitIdentificationCode;
import org.mobicents.protocols.ss7.isup.message.parameter.ForwardCallIndicators;
import org.mobicents.protocols.ss7.isup.message.parameter.NatureOfConnectionIndicators;
import org.mobicents.protocols.ss7.isup.message.parameter.TransmissionMediumRequirement;
import org.mobicents.slee.resources.ss7.isup.events.TimeoutEvent;
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
    
    private Isup2SipPropertiesManagement isup2SipPropertiesManagement = 
    		Isup2SipPropertiesManagement.getInstance();
    
    private CicManagement cicManagement = isup2SipPropertiesManagement.getCicManagement();
    
    private int remoteSPC = isup2SipPropertiesManagement.getRemoteSPC();

    public Isup2SipSbb(){ }

    
	// Initial request
	public void onIAM(InitialAddressMessage iam, ActivityContextInterface aci){
		
		tracer.warning("isup IAM " + iam.getCircuitIdentificationCode().getCIC());
		final int cic = iam.getCircuitIdentificationCode().getCIC();
		final SbbLocalObject sbbLocalObject = sbbContext.getSbbLocalObject();
		final Channel channel = cicManagement.getChannelByCic(cic);
		
		this.setCicValue(cic);
		this.setConversionType(Isup2SipPropertiesManagement.ISUP_TO_SIP);
		this.setANumber(iam.getCallingPartyNumber().getAddress());
		this.setBNumber(iam.getCalledPartyNumber().getAddress());

		if(channel == null) {
			tracer.severe("not equipped CIC=" + cic);
			// TO DO: terminate a call properly
		}
		showMe();
		
		if(! cicManagement.setBusy(this.getCicValue())){
			// stop a call. 
			tracer.warning("CIC is not idle");	
		}
		
		sendCRCX(null);
	}
	
	public void onANM(AnswerMessage anm, ActivityContextInterface aci){
		tracer.info("isup ANM");
		showMe();
		
		final Request sipRequest = this.getSipRequest();
		Response response = null;
		try {
			ContentTypeHeader contentType = null;
			try {
				contentType = headerFactory.createContentTypeHeader("application", "sdp");
			} catch (ParseException ex) {}
			
			/* SDP is needed to make linphone satisfied, that is the only reason to keep SDP 
			 * I have no idea why it is not enough to get SDP within */
			response = messageFactory.createResponse(Response.OK, sipRequest, contentType, this.getSdp().getBytes());
				
			AddressFactory addressFactory = sipProvider.getAddressFactory();
			Address contactAddress = addressFactory
				.createAddress("sip:" + isup2SipPropertiesManagement.getSipIp());
			ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
			response.addHeader(contactHeader);

			this.getServerTransaction().sendResponse(response);
			
		} catch (ParseException ex) {
			tracer.warning("ParseException while trying to create 200_OK Response", ex);
		} catch (SipException ex) {
			tracer.warning("SipException while trying to create 200_OK Response", ex);
		} catch (InvalidArgumentException ex) {
			tracer.warning("InvalidArgumentException while trying to create 200_OK Response", ex);
		}
		
		
		cicManagement.setAnswered(this.getCicValue());
	}
	
	public void onACM(AddressCompleteMessage acm, ActivityContextInterface aci){
		tracer.info("isup ACM " + acm.getCircuitIdentificationCode().getCIC());
		showMe();
		
		final Request request = this.getSipRequest();
		
		try {
			getServerTransaction().sendResponse(
					sipProvider.getMessageFactory().createResponse(Response.RINGING,request));
		} catch (Throwable e) {
			tracer.severe("Failed to reply to request:\n" + request, e);
		}
		
		cicManagement.setAnswered(this.getCicValue());
	}	
	
	public void onREL(ReleaseMessage rel, ActivityContextInterface aci){
		tracer.info("isup REL " + rel.getCircuitIdentificationCode().getCIC() + 
				", SIP request " + this.getSipRequest());
		showMe();
		
		try{ 
			final Dialog dialog = this.getDialog();
			Request byeRequest = dialog.createRequest(Request.BYE);
			tracer.info("sending SIP bye " + byeRequest);
			ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
			dialog.sendRequest(ct);

//			releaseState();

		} catch (Exception e) {
        	// TODO Auto-generated catch block
		   	e.printStackTrace();
		}

		sendRLC();
        sendDLCX();
		detachAll();
	}

	public void onRLC(ReleaseCompleteMessage rlc, ActivityContextInterface aci){
		tracer.warning("isup RLC " + rlc.getCircuitIdentificationCode().getCIC());
		showMe();
		detachAll();
	}
	
	public void onTIMEOUT(TimeoutEvent event, ActivityContextInterface aci){
		tracer.info("timeout" + event + " for msg=" + event.getMessage());
		if(event.getMessage() instanceof InitialAddressMessage){
			cicManagement.setUnknown(this.getCicValue());
			sendSipRRequestTimeout();
			sendDLCX();
			detachAll();
		}
	}
		
	
// generic MGCP responces
	public void onCreateConnectionResponse(CreateConnectionResponse event,
			ActivityContextInterface aci) {

		this.setMgcpConnectionIdentifier(event.getConnectionIdentifier().toString());
		
		if(this.getConversionType().equals(Isup2SipPropertiesManagement.SIP_TO_ISUP))
			onCreateConnectionResponseSipToIsup(event,aci);
		else if(this.getConversionType().equals(Isup2SipPropertiesManagement.ISUP_TO_SIP))
			onCreateConnectionResponseIsupToSip(event,aci);
		else {
			tracer.severe("unexpected CRCX RESP");
		}
	}
	
	public void onCreateConnectionResponseSipToIsup(CreateConnectionResponse event,
				ActivityContextInterface aci) {
			tracer.info("CRCX RESP (Sip->Isup) sbb=" + sbbContext.getSbbLocalObject());
		
			ReturnCode status = event.getReturnCode();

			boolean connectionCreated = (status.getValue() == ReturnCode.TRANSACTION_EXECUTED_NORMALLY);
			String sdp = null;
			
			if(connectionCreated) {
				tracer.info("sdp detected");
				sdp = event.getLocalConnectionDescriptor().toString();
				connectionCreated = (sdp!=null);
			}
			else tracer.severe("MGCP transaction failed");
			
			final Request request = this.getSipRequest();
			
			if(connectionCreated){
				/* the following should be done: 
				 * (1) SIP: send progress with sdp
				 * (2) ISUP: send IAM
				 */
				
				this.setSdp(sdp);

				ContentTypeHeader contentType = null;
				try {
					contentType = headerFactory.createContentTypeHeader("application", "sdp");
				} catch (ParseException ex) {}
				
				Response response = null;
				try {
					response = messageFactory.createResponse(Response.SESSION_PROGRESS, request, contentType, sdp);
				} catch (ParseException ex) {
					tracer.warning("ParseException while trying to create SESSION_PROGRESS Response", ex);
				}
				// fetch A- and B- numbers
				FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
				ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);
				final String aNumber = CodingShemes.numberFromURI(fromHeader.getAddress().getURI().toString());
				final String bNumber = CodingShemes.numberFromURI(toHeader.getAddress().getURI().toString());
				tracer.warning("To   header=" + toHeader + " ->" + bNumber);
				tracer.warning("From header=" + fromHeader + " ->" + aNumber);
				
				InitialAddressMessage msg = isupMessageFactory.createIAM(this.getCicValue());
//				CircuitIdentificationCode cic = isupParameterFactory.createCircuitIdentificationCode();
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
	                
//	            	isupProvider.sendMessage(msg,remoteSPC);
	                } catch (Exception e) {
	                	// TODO Auto-generated catch block
	            		e.printStackTrace();
	            }
	       
				try {
					this.getServerTransaction().sendResponse(response);
				} catch (InvalidArgumentException ex) {
					tracer.warning("InvalidArgumentException while trying to send SESSION_PROGRESS Response (with sdp)", ex);
				} catch (SipException ex) {
					tracer.warning("SipException while trying to send SESSION_PROGRESS Response (with sdp)", ex);
				}
			}
			else {
				tracer.severe("sending SIP SERVER_INTERNAL_ERROR");
				/* unable to create voice path (that's strange), so
				 * mark CIC as IDLE
				 * SIP: send SERVICE_UNAVAILABLE
	//!!!!!!!	 * detach MGCP
				 */
				cicManagement.setIdle(this.getCicValue());
				
				try {
					Response response = messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, getSipRequest());
					this.getServerTransaction().sendResponse(response);
				} catch (Exception ex) {
					tracer.warning("Exception while trying to send SERVER_INTERNAL_ERROR Response", ex);
				}
			}
		}
	
	public void onCreateConnectionResponseIsupToSip(CreateConnectionResponse event,
				ActivityContextInterface aci) {
			tracer.info("CRCX RESP (Isup->Sip) sbb=" + sbbContext.getSbbLocalObject());
		
			ReturnCode status = event.getReturnCode();

			boolean connectionCreated = (status.getValue() == ReturnCode.TRANSACTION_EXECUTED_NORMALLY);
			String sdp = null;
			
			if(connectionCreated) {
				sdp = event.getLocalConnectionDescriptor().toString();
				connectionCreated = (sdp!=null);
			}
			if(connectionCreated){
				/* the following should be done: 
				 * (1?) ISUP: send ACM 
				 * (2) SIP: send INVITE
				 */
				this.setSdp(sdp);
				
				sendACM();
				
				ContentTypeHeader contentType = null;
				try {
					contentType = headerFactory.createContentTypeHeader("application", "sdp");
				} catch (ParseException ex) {}

				// just send SIP INVITE
				
				try {
					// create headers needed 
					AddressFactory addressFactory = sipProvider.getAddressFactory();
					
					Address fromNameAddress = addressFactory
							.createAddress("sip:"+ this.getANumber() + "@" + isup2SipPropertiesManagement.getSipIp());
					Address toNameAddress = addressFactory
							.createAddress("sip:"+ this.getBNumber() + "@" + isup2SipPropertiesManagement.getSipPeer());
					
					HeaderFactory headerFactory = sipProvider.getHeaderFactory();
					FromHeader fromHeader = headerFactory.createFromHeader(
							fromNameAddress, null);
					ToHeader toHeader = headerFactory.createToHeader(
							toNameAddress, null);
					
					CallIdHeader callIdHeader = sipProvider.getNewCallId();
					
					List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>(1);
					ListeningPoint listeningPoint = sipProvider.getListeningPoints()[0];
					ViaHeader viaHeader = sipProvider.getHeaderFactory().createViaHeader(listeningPoint.getIPAddress(),
							listeningPoint.getPort(),listeningPoint.getTransport(), null);
					viaHeaders.add(viaHeader);
					ContentTypeHeader contentTypeHeader = headerFactory
							.createContentTypeHeader("application", "sdp");
					CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(2L,
							Request.INVITE);
					MaxForwardsHeader maxForwardsHeader = headerFactory
							.createMaxForwardsHeader(70);
					
					MessageFactory messageFactory = sipProvider.getMessageFactory();

					
					tracer.warning("own SIP ip is " + isup2SipPropertiesManagement.getSipIp() + 
							", SIP peer is " + isup2SipPropertiesManagement.getSipPeer());
					// create request uri
					URI requestURI = addressFactory.createSipURI(this.getBNumber(),isup2SipPropertiesManagement.getSipPeer());
							
					Address contactAddress = addressFactory.createAddress("sip:" + isup2SipPropertiesManagement.getSipIp());
					ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
							
					// create request
					Request invite = messageFactory.createRequest(requestURI, Request.INVITE, callIdHeader, cSeqHeader, 
									fromHeader, toHeader, viaHeaders, maxForwardsHeader, contentTypeHeader, sdp);
					invite.setHeader(contactHeader);
					
					// create client transaction and send request
					ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(invite);
							
					final DialogActivity sipDialog = (DialogActivity) sipProvider.getNewDialog(clientTransaction);
					final ActivityContextInterface sipDialogACI = sipActivityContextInterfaceFactory.getActivityContextInterface(sipDialog);
					final SbbLocalObject sbbLocalObject = sbbContext.getSbbLocalObject();
					sipDialogACI.attach(sbbLocalObject);
							
					clientTransaction.sendRequest();
							
					this.setSipRequest(invite);

				} catch (Throwable f) {
					tracer.severe("Failed to create and send message", f);
				}
		}
	}

	public void onModifyConnectionResponse(ModifyConnectionResponse event,
			ActivityContextInterface aci) {
		tracer.warning("MDCX Resp, Isup->Sip");
	}

	public void onDeleteConnectionResponse(DeleteConnectionResponse event,
				ActivityContextInterface aci) {
		// I will never get it, I hope: ACI will be detached already
	}

	public void onMgcpTIMEOUT(TransactionTimeout event, ActivityContextInterface aci){
		tracer.severe("Mgcp Timeout");
		
		if(event.getJainMgcpCommandEvent() instanceof CreateConnection){
			cicManagement.setUnknown(this.getCicValue());
			sendSipRRequestTimeout();
			detachAll();
		}
	}

	// SIP Events
	// Initial request
	public void onInviteEvent(RequestEvent sipEvent, ActivityContextInterface aci) {
		
		if(this.getConversionType() != null){
			tracer.warning("Re-Invite event");
			onReInviteEvent(sipEvent, aci);
			return;
		}
		
		final Request request = sipEvent.getRequest();
		tracer.warning("(primary) Invite event, uri is " + request.getRequestURI() + "---");
		this.setSipRequest(request);
		this.setConversionType(Isup2SipPropertiesManagement.SIP_TO_ISUP);
		
		// ACI is the server transaction activity
		try {
			// try to allocate CIC
			final Channel channel = cicManagement.allocateIdleChannel();
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
			
			String sdp = new String(request.getRawContent());			
			sendCRCX(sdp);
			
			// with a proper CRCX_RESP, sent IAM..
			
		} catch (Throwable e) {
			tracer.severe("Failed to process incoming INVITE.", e);
			sipReplyToRequestEvent(sipEvent, Response.SERVICE_UNAVAILABLE);
		}
	}
	
	public void onReInviteEvent(RequestEvent sipEvent, ActivityContextInterface aci) {
		tracer.severe("on Re-Invite: " + sipEvent);
		final Request request = sipEvent.getRequest();
		this.setSipRequest(request);
		try{
			String sdp = new String(request.getRawContent());
			sendMDCX(sdp);
		}
		catch (Exception e){
			tracer.severe("exception while fetching sdp");
		}		
		sipReplyToRequestEvent(sipEvent, Response.OK);	
	}

	// Responses
	public void on1xxResponse(ResponseEvent sipEvent, ActivityContextInterface aci) {
		tracer.severe("on1xxResp: " + sipEvent);
		try{
			String sdp = new String(sipEvent.getResponse().getRawContent());
			sendMDCX(sdp);
		}
		catch (Exception e){
			tracer.severe("exception while fetching sdp");
		}
	}

	public void on2xxResponse(ResponseEvent sipEvent, ActivityContextInterface aci) {
		tracer.severe("on2xxResp:" + sipEvent);
		final CSeqHeader cseq = (CSeqHeader) sipEvent.getResponse().getHeader(
				CSeqHeader.NAME);
		tracer.severe("cseq hdr:" + sipEvent + " ; method=" + cseq.getMethod());
		if (cseq.getMethod().equals(Request.INVITE)) {
			try {
				String sdp = new String(sipEvent.getResponse().getRawContent());
				tracer.severe("on2xx: sdp is " + sdp);
				sendMDCX(sdp);
				
				Request ack = sipEvent.getDialog().createAck(cseq.getSeqNumber());		
				/* 3 lines to make Linphone happy, but it looks noncense to me */
//				sdp = this.getSdp();
//				ContentTypeHeader cth = sipProvider.getHeaderFactory().createContentTypeHeader("application", "sdp");
//				ack.setContent(sdp, cth);
				
				sipEvent.getDialog().sendAck(ack);
			} catch (Exception e) {
				tracer.severe("Unable to ack INVITE's 200 ok", e);
			}
			
			sendANM();
			
		} else if (cseq.getMethod().equals(Request.BYE)
				|| cseq.getMethod().equals(Request.CANCEL)) {
			return;
		}
	}

	public void onBye(RequestEvent event, ActivityContextInterface aci) {
		
		try{
			Response response = messageFactory.createResponse(Response.OK, event.getRequest());
        	event.getServerTransaction().sendResponse(response);
		} catch (Exception e) {
        	// TODO Auto-generated catch block
		   	e.printStackTrace();
		}
 
        sendREL(CauseIndicators._CV_ALL_CLEAR);
		sendDLCX();
	}

	public void onCancel(CancelRequestEvent event, ActivityContextInterface aci) {
		if (tracer.isInfoEnabled()) {
			tracer.info("Got a CANCEL request.");
		}
		
		try {
			Response response = messageFactory.createResponse(Response.OK, event.getRequest());
        	event.getServerTransaction().sendResponse(response);
		} catch (Exception e) {
			tracer.severe("Failed to process cancel request", e);
		}
		
        sendREL(CauseIndicators._CV_ALL_CLEAR);
		sendDLCX();
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

	
	

	
	
	public abstract void setCicValue(int cicValue);

	public abstract int getCicValue();

	public abstract void setSipRequest(Request sipRequest);

	public abstract Request getSipRequest();
	
	public abstract void setMgcpCallIdentifier(String mgcpCallId);
	
	public abstract String getMgcpCallIdentifier();
	
	public abstract void setMgcpConnectionIdentifier(String mgcpConnId);
	
	public abstract String getMgcpConnectionIdentifier();
	
	public abstract void setConversionType(String type);
	
	public abstract String getConversionType();
	
	public abstract void setANumber(String type);
	
	public abstract String getANumber();
	
	public abstract void setBNumber(String type);
	
	public abstract String getBNumber();
	
	/* Ekiga phone needs sdp with 200Ok*/
	public abstract void setSdp(String sdp);
	
	public abstract String getSdp();
	
	public void setSbbContext(SbbContext context) {
		this.sbbContext = context;
		if (tracer == null) {
			tracer = sbbContext.getTracer(Isup2SipSbb.class
					.getSimpleName());
		}
		try {
			tracer.severe("trying to start!!!!");
			final Context ctx = (Context) new InitialContext().lookup(Isup2SipManagement.CONTEXT);
	
			// SIP
			sipActivityContextInterfaceFactory = (SipActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainsip/1.2/acifactory");
			sipProvider = (SleeSipProvider) ctx.lookup("slee/resources/jainsip/1.2/provider");
			//addressFactory = provider.getAddressFactory();
			headerFactory = sipProvider.getHeaderFactory();
			messageFactory = sipProvider.getMessageFactory();
			
			// MGCP
			mgcpProvider = (JainMgcpProvider) ctx.lookup(Isup2SipManagement.MGCP_PROVIDER);
			mgcpActivityContestInterfaceFactory = (MgcpActivityContextInterfaceFactory) ctx.lookup(Isup2SipManagement.MGCP_ACI_FACTORY);
			
			// ISUP
            isupProvider = (RAISUPProvider) ctx.lookup("slee/resources/isup/1.0/provider");
            isupMessageFactory = isupProvider.getMessageFactory();
            isupParameterFactory = isupProvider.getParameterFactory();
            isupActivityContextInterfaceFactory = (org.mobicents.slee.resources.ss7.isup.ratype.ActivityContextInterfaceFactory) ctx.lookup("slee/resources/isup/1.0/acifactory");
								
		} catch (NamingException e) {
			tracer.severe(e.getMessage(), e);
		}
	}
	
	public void onServiceStartedEvent(ServiceStartedEvent event, ActivityContextInterface aci){
		tracer.severe("-- service started");
//		isup2SipPropertiesManagement.registerIsupManagement();
//		aci.detach(sbbContext.getSbbLocalObject());
	}
	
	public void showMe(){
		tracer.warning("object: " + sbbContext.getSbbLocalObject() 
				+ " cic=" + this.getCicValue()
				+ " sipRequest=" + this.getSipRequest());
	}
	
	
	
    protected Dialog getDialog() {
        ActivityContextInterface activities[] = sbbContext.getActivities();
        for (ActivityContextInterface aci : activities) {
            if (aci.getActivity() instanceof Dialog) {
                return (Dialog)aci.getActivity();
            }
        }
        return null;
    }

    protected ServerTransaction getServerTransaction() {
        ActivityContextInterface activities[] = sbbContext.getActivities();
        for (ActivityContextInterface aci : activities) {
            if (aci.getActivity() instanceof ServerTransaction) {
                return (ServerTransaction)aci.getActivity();
            }
        }
        return null;
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


	
// ISUP functions
	
	void sendACM(){

		tracer.info("sending ACM");
		
		AddressCompleteMessage msg = isupMessageFactory.createACM(this.getCicValue());
		BackwardCallIndicators bci = isupParameterFactory.createBackwardCallIndicators();
		msg.setBackwardCallIndicators(bci);
        msg.setSls(this.getCicValue());
        try {
        	// just to play with stack, send smth
        	isupProvider.sendMessage(msg,remoteSPC);
            } catch (Exception e) {
            	// TODO Auto-generated catch block
        		e.printStackTrace();
        }
	}
	
	void sendANM(){
		
		tracer.info("sending ANM");

		AnswerMessage msg = isupMessageFactory.createANM(this.getCicValue());
		msg.setSls(this.getCicValue());
        try {
        	// just to play with stack, send smth
        	isupProvider.sendMessage(msg,remoteSPC);
            } catch (Exception e) {
            	// TODO Auto-generated catch block
        		e.printStackTrace();
        }
		
	}
	
	void sendREL(int causeValue){
		final int cic = this.getCicValue();
		ReleaseMessage msg = isupMessageFactory.createREL(cic);
		msg.setSls(cic);
		CauseIndicators cause = isupParameterFactory.createCauseIndicators();
		cause.setCauseValue(causeValue);
		msg.setCauseIndicators(cause);
		try {
			// just to play with stack, send smth
		   	isupProvider.sendMessage(msg,remoteSPC);
		} catch (Exception e) {
        	// TODO Auto-generated catch block
		   	e.printStackTrace();
		}
	}
	
	void sendRLC(){
		final int cic = this.getCicValue();
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
	
	
// MGCP functions
	
	void sendCRCX(String sdp){
		
		final SbbLocalObject sbbLocalObject = sbbContext.getSbbLocalObject();
		final int cic = this.getCicValue();
		final Channel channel = cicManagement.getChannelByCic(cic);
		
		CallIdentifier mgcpCallID = mgcpProvider.getUniqueCallIdentifier();
		this.setMgcpCallIdentifier(mgcpCallID.toString());
		EndpointIdentifier endpointID = new EndpointIdentifier(channel.getEndpointId(),channel.getGatewayAddress());
		CreateConnection createConnection = new CreateConnection(this,
				mgcpCallID, endpointID, ConnectionMode.SendRecv);
		if(sdp !=null)
			try {
				createConnection.setRemoteConnectionDescriptor(new ConnectionDescriptor(sdp));
			} catch (ConflictingParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		final int txID = mgcpProvider.getUniqueTransactionHandler();
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
		
		tracer.info("CRCX: " + createConnection);
		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { createConnection });
		tracer.info("CRCX sent; ep ID=" + endpointID + " sbb=" + sbbLocalObject);
	}
	
	void sendMDCX(String sdp){
		tracer.info("preparing MDCX");		
		final SbbLocalObject sbbLocalObject = sbbContext.getSbbLocalObject();
		final int cic = this.getCicValue();
		final Channel channel = cicManagement.getChannelByCic(cic);
		CallIdentifier mgcpCallID = new CallIdentifier(this.getMgcpCallIdentifier());
		this.setMgcpCallIdentifier(mgcpCallID.toString());
		EndpointIdentifier endpointID = new EndpointIdentifier(channel.getEndpointId(),channel.getGatewayAddress());
		ConnectionIdentifier connectionIdentifier = new ConnectionIdentifier(this.getMgcpConnectionIdentifier());
		ModifyConnection modifyConnection = new ModifyConnection(this, mgcpCallID, endpointID, connectionIdentifier);
		try {
			modifyConnection.setRemoteConnectionDescriptor(new ConnectionDescriptor(sdp));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		final int txID = mgcpProvider.getUniqueTransactionHandler();
		modifyConnection.setTransactionHandle(txID);
		
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
		
		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { modifyConnection });
		tracer.info("DLCX sent; ep ID=" + endpointID + " sbb=" + sbbLocalObject);
		
	}
	
	void sendDLCX(){
        
		final SbbLocalObject sbbLocalObject = sbbContext.getSbbLocalObject();
		final Channel channel = cicManagement.getChannelByCic(this.getCicValue());
        CallIdentifier mgcpCallID = new CallIdentifier(this.getMgcpCallIdentifier());
        
        ConnectionIdentifier connectionIdentifier = new ConnectionIdentifier(this.getMgcpConnectionIdentifier());
		        
		EndpointIdentifier endpointID = new EndpointIdentifier(channel.getEndpointId(), channel.getGatewayAddress());
		DeleteConnection deleteConnection = new DeleteConnection(this, mgcpCallID, endpointID, connectionIdentifier);
		final int txID = mgcpProvider.getUniqueTransactionHandler();
		deleteConnection.setTransactionHandle(txID);
		
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
		
		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection });
		tracer.info("DLCX sent; ep ID=" + endpointID + " sbb=" + sbbLocalObject);

	}
	
	public void sendSipRRequestTimeout(){
		final Request request = this.getSipRequest();
		Response response = null;
		try {
			response = messageFactory.createResponse(Response.REQUEST_TIMEOUT, request);
		} catch (ParseException ex) {
			tracer.warning("ParseException while trying to create REQUEST_TIMEOUT Response", ex);
		}
		
		try {
			this.getServerTransaction().sendResponse(response);
		} catch (InvalidArgumentException ex) {
			tracer.warning("InvalidArgumentException while trying to send REQUEST_TIMEOUT Response", ex);
		} catch (SipException ex) {
			tracer.warning("SipException while trying to send REQUEST_TIMEOUT Response", ex);
		}
	}
	
 	private void detachAll() {
		ActivityContextInterface[] activities = sbbContext.getActivities();
		SbbLocalObject sbbLocalObject = sbbContext.getSbbLocalObject();
	
		for (ActivityContextInterface attachedAci : activities) {
//			tracer.severe("activity " + attachedAci.getActivity());
			attachedAci.detach(sbbLocalObject);
		}
	}	

}
