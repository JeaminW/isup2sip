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
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.HeaderFactory;
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
import org.mobicents.protocols.ss7.isup.ISUPMessageFactory;
import org.mobicents.protocols.ss7.isup.ISUPParameterFactory;
import org.mobicents.protocols.ss7.isup.message.InitialAddressMessage;
import org.mobicents.protocols.ss7.isup.message.parameter.CalledPartyNumber;
import org.mobicents.protocols.ss7.isup.message.parameter.CallingPartyCategory;
import org.mobicents.protocols.ss7.isup.message.parameter.CircuitIdentificationCode;
import org.mobicents.protocols.ss7.isup.message.parameter.ForwardCallIndicators;
import org.mobicents.protocols.ss7.isup.message.parameter.NatureOfConnectionIndicators;
import org.mobicents.protocols.ss7.isup.message.parameter.TransmissionMediumRequirement;
import org.mobicents.slee.resources.ss7.isup.ratype.RAISUPProvider;


public abstract class Isup2SipSbb implements javax.slee.Sbb {
	private SbbContext sbbContext;
	private static Tracer tracer;

	// SIP
	private SleeSipProvider sipProvider;
	private SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory;
	private HeaderFactory headerFactory;
	//private AddressFactory addressFactory;
	private MessageFactory messageFactory;
	
	private RequestEvent sipEvent;

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

	// Initial request
	public void onInviteEvent(RequestEvent event, ActivityContextInterface aci) {
		sipEvent = event;
		
		// ACI is the server transaction activity
		try {
			// try to allocate CIC
			final Channel channel  = getIdleChannel();
			if(channel == null) {
				tracer.warning("Failed to allocate CIC");
				sipReplyToRequestEvent(event, Response.SERVICE_UNAVAILABLE);
				return;
			}
			tracer.info("CIC allocated");
			tracer.info(channel.getEndpointId() +" | " + channel.getCic() + " | " + channel.getGatewayAddress());
			// Create SIP dialog
			final DialogActivity sipDialog = (DialogActivity) sipProvider.getNewDialog(event.getServerTransaction());
			// Obtain the dialog activity context and attach to it
			final ActivityContextInterface sipDialogACI = sipActivityContextInterfaceFactory.getActivityContextInterface(sipDialog);
			final SbbLocalObject sbbLocalObject = sbbContext.getSbbLocalObject();
			sipDialogACI.attach(sbbLocalObject);
			
			// send "trying" response
			sipReplyToRequestEvent(event, Response.TRYING);
			String sdp = new String(event.getRequest().getRawContent());

			CallIdentifier mgcpCallID = this.mgcpProvider.getUniqueCallIdentifier();
//			setCallIdentifier(mgcpCallID);

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
			tracer.info("SIP->ISUP, CRCX sent");
			
			// with a proper CRCX_RESP, sent IAM..
/*			
			// issue IAM
			IsupProvider isupProvider = 
			InitialAddressMessage msg = super.provider.getMessageFactory().createIAM(1);
            NatureOfConnectionIndicators nai = super.provider.getParameterFactory().createNatureOfConnectionIndicators();
            ForwardCallIndicators fci = super.provider.getParameterFactory().createForwardCallIndicators();
            CallingPartyCategory cpg = super.provider.getParameterFactory().createCallingPartyCategory();
            TransmissionMediumRequirement tmr = super.provider.getParameterFactory().createTransmissionMediumRequirement();
            CalledPartyNumber cpn = super.provider.getParameterFactory().createCalledPartyNumber();
            cpn.setAddress("14614577");
            
            
            msg.setNatureOfConnectionIndicators(nai);
            msg.setForwardCallIndicators(fci);
            msg.setCallingPartCategory(cpg);
            msg.setCalledPartyNumber(cpn);
            msg.setTransmissionMediumRequirement(tmr);
			// send CRCX
*/
			
		} catch (Throwable e) {
			tracer.severe("Failed to process incoming INVITE.", e);
			sipReplyToRequestEvent(event, Response.SERVICE_UNAVAILABLE);
		}
	}
	private Channel getIdleChannel() {
		// TODO Auto-generated method stub
		return null;
	}
	public void onCreateConnectionResponse(CreateConnectionResponse event,
			ActivityContextInterface aci) {
		tracer.info("Receive CRCX response: " + event);

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
				response = messageFactory.createResponse(Response.SESSION_PROGRESS, sipEvent.getRequest(), contentType, sdp.getBytes());
			} catch (ParseException ex) {
				tracer.warning("ParseException while trying to create SESSION_PROGRESS Response", ex);
			}
			
			InitialAddressMessage msg = isupMessageFactory.createIAM(1);
			CircuitIdentificationCode cic = isupParameterFactory.createCircuitIdentificationCode();
			cic.setCIC(2);
            NatureOfConnectionIndicators nai = isupParameterFactory.createNatureOfConnectionIndicators();
            ForwardCallIndicators fci = isupParameterFactory.createForwardCallIndicators();
            CallingPartyCategory cpg = isupParameterFactory.createCallingPartyCategory();
            TransmissionMediumRequirement tmr = isupParameterFactory.createTransmissionMediumRequirement();
            CalledPartyNumber cpn = isupParameterFactory.createCalledPartyNumber();
            cpn.setAddress("14614577");
            
            msg.setCircuitIdentificationCode(cic);
            msg.setNatureOfConnectionIndicators(nai);
            msg.setForwardCallIndicators(fci);
            msg.setCallingPartCategory(cpg);
            msg.setCalledPartyNumber(cpn);
            msg.setTransmissionMediumRequirement(tmr);

            try {
            	//Context ctx = isupProvider.createClientTransaction(msg);
                //ActivityContextInterface circuitACI = isupActivityContextInterfaceFactory.getActivityContextInterface();
                //circuitACI.attach(this.sbbContext.getSbbLocalObject());
                //ctx.sendRequest();
                isupProvider.sendMessage(msg,222);

                } catch (Exception e) {
                	// TODO Auto-generated catch block
            		e.printStackTrace();
            }
       
            /*
             * 
             *         private void resetCircuits() {
                ActivityContextInterface circuitACI = null;
                ISUPClientTransaction ctx = null;

                ISUPMessageFactory msgFactory = this.provider.getMessageFactory();
                ISUPParameterFactory prmFactory = this.provider.getParameterFactory();

                for (int i = 1; i <= 10; i++) {
                        ResetCircuitMessage rst = null;

                        rst = msgFactory.createRSC();
                        CircuitIdentificationCode cic = prmFactory.createCircuitIdentificationCode();
                        cic.setCIC(i);
                        rst.setCircuitIdentificationCode(cic);
                        try {
                                ctx = this.provider.createClientTransaction(rst);
                                circuitACI = this.acif.getActivityContextInterface(ctx);
                                circuitACI.attach(this.sbbContext.getSbbLocalObject());
                                ctx.sendRequest();
                        } catch (ActivityAlreadyExistsException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        } catch (IllegalArgumentException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        } catch (NullPointerException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        } catch (IllegalStateException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        } catch (SLEEException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        } catch (TransactionAlredyExistsException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        } catch (StartActivityException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        } catch (ParameterRangeInvalidException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        } catch (IOException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                        }
                }
             */
			try {
				sipEvent.getServerTransaction().sendResponse(response);
			} catch (InvalidArgumentException ex) {
				tracer.warning("InvalidArgumentException while trying to send SESSION_PROGRESS Response (with sdp)", ex);
			} catch (SipException ex) {
				tracer.warning("SipException while trying to send SESSION_PROGRESS Response (with sdp)", ex);
			}
		}
		else {
			/* unable to create voice path (that's strange), so
			 * SIP: send SERVICE_UNAVAILABLE
			 * detach MGCP
			 */
			try {
				Response response = messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, sipEvent.getRequest());
				sipEvent.getServerTransaction().sendResponse(response);
			} catch (Exception ex) {
				tracer.warning("Exception while trying to send SERVER_INTERNAL_ERROR Response", ex);
			}
		}
	}
	
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
		// send back 200 ok for this dialog right away, to avoid retransmissions
		sipReplyToRequestEvent(event, Response.OK);
		// forward to the other dialog
		processMidDialogRequest(event, aci);
	}

	public void onCancel(CancelRequestEvent event, ActivityContextInterface aci) {
		if (tracer.isInfoEnabled()) {
			tracer.info("Got a CANCEL request.");
		}
		
		try {
			this.sipProvider.acceptCancel(event, false);
			final ActivityContextInterface peerDialogACI = getPeerDialog(aci);
			final DialogActivity peerDialog = (DialogActivity) peerDialogACI
					.getActivity();
			final DialogState peerDialogState = peerDialog.getState();
			if (peerDialogState == null || peerDialogState == DialogState.EARLY) {
				peerDialog.sendCancel();
			} else {
				peerDialog.sendRequest(peerDialog.createRequest(Request.BYE));
			}
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

	private void processMidDialogRequest(RequestEvent event,
			ActivityContextInterface dialogACI) {
		try {
			// Find the dialog to forward the request on
			ActivityContextInterface peerACI = getPeerDialog(dialogACI);
			forwardRequest(event,
					(DialogActivity) peerACI.getActivity());
		} catch (SipException e) {
			tracer.severe(e.getMessage(), e);
			sipReplyToRequestEvent(event, Response.SERVICE_UNAVAILABLE);
		}
	}

	private void processResponse(ResponseEvent event,
			ActivityContextInterface aci) {
		try {
			// Find the dialog to forward the response on
			ActivityContextInterface peerACI = getPeerDialog(aci);
			forwardResponse((DialogActivity) aci.getActivity(),
					(DialogActivity) peerACI.getActivity(), event
							.getClientTransaction(), event.getResponse());
		} catch (SipException e) {
			tracer.severe(e.getMessage(), e);
		}
	}

	private ActivityContextInterface getPeerDialog(ActivityContextInterface aci)
			throws SipException {
		final ActivityContextInterface incomingDialogAci = getIncomingDialog();
		if (aci.equals(incomingDialogAci)) {
			return getOutgoingDialog();
		}
		if (aci.equals(getOutgoingDialog())) {
			return incomingDialogAci;
		}
		throw new SipException("could not find peer dialog");

	}

	private void forwardRequest(RequestEvent event, DialogActivity out)
			throws SipException {
		final Request incomingRequest = event.getRequest();
		if (tracer.isInfoEnabled()) {
			tracer.info("Forwarding request " + incomingRequest.getMethod()
					+ " to dialog " + out);
		}
		// Copies the request, setting the appropriate headers for the dialog.
		Request outgoingRequest = out.createRequest(incomingRequest);
		// Send the request on the dialog activity
		final ClientTransaction ct = out.sendRequest(outgoingRequest);
		// Record an association with the original server transaction,
		// so we can retrieve it when forwarding the response.
		out.associateServerTransaction(ct, event.getServerTransaction());
	}

	private void forwardResponse(DialogActivity in, DialogActivity out,
			ClientTransaction ct, Response receivedResponse)
			throws SipException {
		// Find the original server transaction that this response
		// should be forwarded on.
		final ServerTransaction st = in.getAssociatedServerTransaction(ct);
		// could be null
		if (st == null)
			throw new SipException(
					"could not find associated server transaction");
		if (tracer.isInfoEnabled()) {
			tracer.info("Forwarding response "
					+ receivedResponse.getStatusCode() + " to dialog " + out);
		}
		// Copy the response across, setting the appropriate headers for the
		// dialog
		final Response outgoingResponse = out.createResponse(st,
				receivedResponse);
		// Forward response upstream.
		try {
			st.sendResponse(outgoingResponse);
		} catch (InvalidArgumentException e) {
			tracer.severe("Failed to send response:\n" + outgoingResponse, e);
			throw new SipException("invalid response", e);
		}
	}

	// other request handling methods
	// lifecycle methods
	// CMP field accessors for each Dialogs ACI
	public abstract void setIncomingDialog(ActivityContextInterface aci);

	public abstract ActivityContextInterface getIncomingDialog();

	public abstract void setOutgoingDialog(ActivityContextInterface aci);

	public abstract ActivityContextInterface getOutgoingDialog();

	
	
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