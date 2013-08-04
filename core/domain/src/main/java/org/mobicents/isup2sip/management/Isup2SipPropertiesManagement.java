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
package org.mobicents.isup2sip.management;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import javolution.text.TextBuilder;
import javolution.xml.XMLBinding;
import javolution.xml.XMLObjectReader;
import javolution.xml.XMLObjectWriter;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;

public class Isup2SipPropertiesManagement implements
		Isup2SipPropertiesManagementMBean {

	private static final org.apache.log4j.Logger logger = Logger
			.getLogger(Isup2SipPropertiesManagement.class);

	protected static final String GATEWAY = "gateway";
	protected static final String GATEWAY_PART = "part";
	protected static final String REMOTE_PC = "remote";


	private static final String TAB_INDENT = "\t";
	private static final String CLASS_ATTRIBUTE = "type";
	private static final XMLBinding binding = new XMLBinding();
	private static final String PERSIST_FILE_NAME = "isup2sip_properties.xml";

	private static Isup2SipPropertiesManagement instance;

	private final String name;

	private String persistDir = null;

	private final TextBuilder persistFile = TextBuilder.newInstance();

	private String gateway;

	private int gatewayPartForDebug;
	
	// private DataSource dataSource;

	private boolean countersEnabled = true;

	private CicManagement cicManagement;

	private int remoteSPC;
	
	private Isup2SipPropertiesManagement(String name) {
		this.name = name;
		binding.setClassAttribute(CLASS_ATTRIBUTE);
	}

	protected static Isup2SipPropertiesManagement getInstance(String name) {
		if (instance == null) {
			instance = new Isup2SipPropertiesManagement(name);
		}
		return instance;
	}

	public static Isup2SipPropertiesManagement getInstance() {
		return instance;
	}

	public String getName() {
		return name;
	}

	public CicManagement getCicManagement() {
		return cicManagement;
	}
	
	@Override
	public int getRemoteSPC(){
		return this.remoteSPC;
	}

	@Override
	public void setRemoteSPC(int pc){
		this.remoteSPC = pc;
		logger.warn("remote SPC is set to " + pc);
	}
	
	@Override
	public int getGatewayPart(){
		return this.gatewayPartForDebug;
	}

	@Override
	public void setGatewayPart(int part){
		this.gatewayPartForDebug = part;
		logger.warn("gateway Part is set to " + part);
	}
		
	@Override
	public String getGateway(){
		return this.gateway;
	}

	@Override
	public void setGateway(String gw){
		this.gateway = gw;
	}
		
	
	
	@Override
	public String getPersistDir() {
		return persistDir;
	}

	public void setPersistDir(String persistDir) {
		this.persistDir = persistDir;
	}

	public void start() throws Exception {

		this.persistFile.clear();

		if (persistDir != null) {
			this.persistFile.append(persistDir).append(File.separator)
					.append(this.name).append("_").append(PERSIST_FILE_NAME);
		} else {
			persistFile
					.append(System.getProperty(
							Isup2SipManagement.SERVICE_PERSIST_DIR_KEY,
							System.getProperty(Isup2SipManagement.USER_DIR_KEY)))
					.append(File.separator).append(this.name).append("_")
					.append(PERSIST_FILE_NAME);
		}

		logger.info(String.format("Loading SERVICE Properties from %s",
				persistFile.toString()));

		try {
			this.load();
		} catch (FileNotFoundException e) {
			logger.warn(String.format(
					"Failed to load the SERVICE configuration file. \n%s",
					e.getMessage()));
		}

		// this.setUpDataSource();

		this.cicManagement = new CicManagement(this.gateway, this.gatewayPartForDebug);

	}

	public void stop() throws Exception {
		// this.sessionCounters.reset();
		this.store();
	}

	/**
	 * Persist
	 */
	public void store() {

		// TODO : Should we keep reference to Objects rather than recreating
		// everytime?
		try {
			XMLObjectWriter writer = XMLObjectWriter
					.newInstance(new FileOutputStream(persistFile.toString()));
			writer.setBinding(binding);
			// Enables cross-references.
			// writer.setReferenceResolver(new XMLReferenceResolver());
			writer.setIndentation(TAB_INDENT);

			writer.write(this.gateway, GATEWAY,  String.class);
			writer.write(this.gatewayPartForDebug, GATEWAY_PART,  Integer.class);
			writer.write(this.remoteSPC, REMOTE_PC,  Integer.class);

			writer.close();
		} catch (Exception e) {
			logger.error("Error while persisting the Rule state in file", e);
		}
	}

	/**
	 * Load and create LinkSets and Link from persisted file
	 * 
	 * @throws Exception
	 */
	public void load() throws FileNotFoundException {

		XMLObjectReader reader = null;
		try {
			reader = XMLObjectReader.newInstance(new FileInputStream(
					persistFile.toString()));

			reader.setBinding(binding);
			this.gateway = reader.read(GATEWAY, String.class);
			this.gatewayPartForDebug = reader.read(GATEWAY_PART, Integer.class);
			this.remoteSPC = reader.read(REMOTE_PC, Integer.class);
			
			
			
			reader.close();
		} catch (XMLStreamException ex) {
			// this.logger.info(
			// "Error while re-creating Linksets from persisted file", ex);
		}
	}
	/*
	 * private void setUpDataSource() throws NamingException { Context ctx = new
	 * InitialContext(); this.dataSource = (DataSource)
	 * ctx.lookup("java:DefaultDS"); }
	 */
}
