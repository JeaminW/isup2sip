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

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.Logger;
import org.mobicents.ss7.management.console.ShellExecutor;

/**
 * @author amit bhayani, dmitri soloviev
 * 
 */
public class Isup2SipShellExecutor implements ShellExecutor {

	private static final org.apache.log4j.Logger logger = Logger.getLogger(Isup2SipShellExecutor.class);

	private Isup2SipManagement isup2SipManagement;
	private Isup2SipPropertiesManagement isup2sipPropertiesManagement = Isup2SipPropertiesManagement.getInstance();

	/**
	 * 
	 */
	public Isup2SipShellExecutor() {
		// TODO Auto-generated constructor stub
	}

	public Isup2SipManagement getIsup2SipManagement() {
		return isup2SipManagement;
	}

	public void setIsup2SipManagement(Isup2SipManagement isup2sipManagement) {
		this.isup2SipManagement = isup2sipManagement;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.ss7.management.console.ShellExecutor#execute(java.lang.
	 * String[])
	 */
	@Override
	public String execute(String[] commands) {

		try {
			if (commands.length < 2) {
				return Isup2SipOAMMessages.INVALID_COMMAND;
			}
			String command = commands[1];

			if (command.equals("set")) {
				return this.manageSet(commands);
			} else if (command.equals("get")) {
				return this.manageGet(commands);
			}
			return Isup2SipOAMMessages.INVALID_COMMAND;
		} catch (Exception e) {
			logger.error(String.format("Error while executing comand %s", Arrays.toString(commands)), e);
			return e.getMessage();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.ss7.management.console.ShellExecutor#handles(java.lang.
	 * String)
	 */
	@Override
	public boolean handles(String command) {
		return "isup2sip".equals(command);
	}

	/**
	 * isup2sip set spurl <url>
	 * 
	 * @param options
	 * @return
	 * @throws Exception
	 */
	private String manageSet(String[] options) throws Exception {
		if (options.length < 4) {
			return Isup2SipOAMMessages.INVALID_COMMAND;
		}

		String parName = options[2].toLowerCase();
/*		if (parName.equals(Isup2SipPropertiesManagement.GATEWAY)) {
			isup2sipPropertiesManagement.setGateway(options[3]);
		} else if (parName.equals(Isup2SipPropertiesManagement.GATEWAY_PART)) {
			isup2sipPropertiesManagement.setGatewayPart(Integer.parseInt(options[3]));
		} else */
		if (parName.equals(Isup2SipPropertiesManagement.REMOTE_PC)) {
			isup2sipPropertiesManagement.setRemoteSPC(Integer.parseInt(options[3]));
		} else if (parName.equals(Isup2SipPropertiesManagement.SIP_PEER)) {
			isup2sipPropertiesManagement.setSipPeer(options[3]);
		} else if (parName.equals(Isup2SipPropertiesManagement.SIP_IP)) {
			isup2sipPropertiesManagement.setSipIp(options[3]);
		} else if(parName.equals(Isup2SipPropertiesManagement.TDM)){
			int index = Integer.parseInt(options[3]);
			isup2sipPropertiesManagement.delMultiplex(index);
			
			if(options.length > 5){
				String gateway = options[4];
				int port = Integer.parseInt(options[5]);
				logger.info("creating multiplex " + index + " gatetway " + gateway + " port " + port);
				isup2sipPropertiesManagement.addMultiplex(index, gateway, port);
				isup2sipPropertiesManagement.getCicManagement().resetMultiplex(index);
			}
		} else {
			return Isup2SipOAMMessages.INVALID_COMMAND;
		}

		return Isup2SipOAMMessages.PARAMETER_SUCCESSFULLY_SET;
	}

	/**
	 * isup2sip get spurl
	 * 
	 * @param options
	 * @return
	 * @throws Exception
	 */
	private String manageGet(String[] options) throws Exception {
		if (options.length == 3) {
			String parName = options[2].toLowerCase();

			StringBuilder sb = new StringBuilder();
			sb.append(options[2]);
			sb.append(" = ");

			if (parName.equals(Isup2SipPropertiesManagement.REMOTE_PC)) {
				sb.append(Integer.toString(isup2sipPropertiesManagement.getRemoteSPC()));
			} else if (parName.equals(Isup2SipPropertiesManagement.SIP_PEER)) {
				sb.append(isup2sipPropertiesManagement.getSipPeer());
			} else if (parName.equals(Isup2SipPropertiesManagement.SIP_IP)) {
				sb.append(isup2sipPropertiesManagement.getSipIp());
			} else {
				return Isup2SipOAMMessages.INVALID_COMMAND;
			}

			return sb.toString();
		} /*else if (options.length == 4) {	// isup2sip get multiplex N
			String parName = options[2].toLowerCase();

			StringBuilder sb = new StringBuilder();
			
			if (parName.equals(Isup2SipPropertiesManagement.MUX)) {
				int index = Integer.valueOf(options[3]);
				Multiplex mux = isup2sipPropertiesManagement.getMultimplex(index);
				sb.append(mux);
			} else {
				return Isup2SipOAMMessages.INVALID_COMMAND;
			}
				
			return sb.toString();
		} */else {
			StringBuilder sb = new StringBuilder();

			sb.append(Isup2SipPropertiesManagement.REMOTE_PC + " = ");
			sb.append(Integer.toString(isup2sipPropertiesManagement.getRemoteSPC()));
			sb.append(", ");

			sb.append(Isup2SipPropertiesManagement.SIP_IP + " = ");
			sb.append(isup2sipPropertiesManagement.getSipIp());
			sb.append(", ");
			
			sb.append(Isup2SipPropertiesManagement.SIP_PEER + " = ");
			sb.append(isup2sipPropertiesManagement.getSipPeer());
			sb.append("\n");

			isup2sipPropertiesManagement.showMultiplexes(sb);
			
			return sb.toString();
		}
	}

}
