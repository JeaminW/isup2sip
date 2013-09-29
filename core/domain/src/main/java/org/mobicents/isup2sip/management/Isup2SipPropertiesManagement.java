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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javolution.text.TextBuilder;
import javolution.util.FastSet;
import javolution.xml.XMLBinding;
import javolution.xml.XMLObjectReader;
import javolution.xml.XMLObjectWriter;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.mobicents.isup2sip.commonlibs.Channel;
/**
 * @author dmitri soloviev
 * 
 */
public class Isup2SipPropertiesManagement implements
		Isup2SipPropertiesManagementMBean {

	private static final org.apache.log4j.Logger logger = Logger
			.getLogger(Isup2SipPropertiesManagement.class);

	private FastSet <Multiplex> interfaces = new FastSet<Multiplex>();
	
//	protected static final String GATEWAY = "gateway";	// to be removed
//	protected static final String GATEWAY_PART = "part";	// to be removed
	
	protected static final String REMOTE_PC = "remote";
	protected static final String SIP_PEER = "sippeer";
	protected static final String SIP_IP = "sipip";
	protected static final String TDM = "tdm";
	protected static final String INTERFACES = "interfaces";
	
	
	public static final String ISUP_TO_SIP = "i2s";
	public static final String SIP_TO_ISUP = "s2i";

	private static final String TAB_INDENT = "\t";
	private static final String CLASS_ATTRIBUTE = "type";
	private XMLBinding binding = null;
	private static final String PERSIST_FILE_NAME = "isup2sip_properties.xml";
	private static final int TIMEOUT_BEFORE_ISUP_RESET = 15000;

	private static Isup2SipPropertiesManagement instance;

	private final String name;

	private String persistDir = null;

	private final TextBuilder persistFile = TextBuilder.newInstance();

	private boolean mgcpManagementSbbReady = false;
	
	private boolean isupManagementSbbReady = false;
	
	private Multiplex findMultiplex(int index){
		for (FastSet.Record r = interfaces.head(), end = interfaces.tail(); (r = r.getNext()) != end;) {
		     Multiplex mux = interfaces.valueOf(r);
		     if (mux.getIndex() == index) return mux;
		}
		
		return null;
	}
		
	public void addMultiplex(int index, String gw, int port){
		
		if( findMultiplex(index) != null ) 
			delMultiplex(index);
	
		Multiplex mux = new Multiplex(index, gw, port);
		interfaces.add(mux);
		
		cicManagement.addMultiplex(index, gw, port);
	}
	
	public void delMultiplex(int index){
		for (FastSet.Record r = interfaces.head(), end = interfaces.tail(); (r = r.getNext()) != end;) {
		     Multiplex mux = interfaces.valueOf(r);
		     if (mux.getIndex() == index) {
		    	 interfaces.delete(r);
		    	 return;
		     }
		}
	}
	
	public void showMultiplexes(StringBuilder sb){
		if( interfaces.isEmpty() ) return;
		
		for (FastSet.Record r = interfaces.head(), end = interfaces.tail(); (r = r.getNext()) != end;) {
		     Multiplex mux = interfaces.valueOf(r);
		     sb.append(mux.toString());
		     sb.append("\n");
		}
	}
	
	private void initMultiplexes(){
		if( interfaces.isEmpty() ) return;

		for (FastSet.Record r = interfaces.head(), end = interfaces.tail(); (r = r.getNext()) != end;) {
		     Multiplex mux = interfaces.valueOf(r);
		     int index = mux.getIndex();
		     cicManagement.addMultiplex(index, mux.getGateway(), mux.getPort());
			 logger.info("resetting pcm #" + index);
		     cicManagement.resetMultiplex(index);
		}
	}
	
	/** in ISUP->SIP case, this peer will get all calls
	 * 
	 */
	private String sipPeer;
	
	private String sipIp;

	/** during developing, a single Telscale Card is shared between 2 isup2sip instances,
	 * that are running one agains another
	 */
//	private int gatewayPartForDebug;
	
	private boolean countersEnabled = true;

	private CicManagement cicManagement = new CicManagement();

	private int remoteSPC;
	
	private Isup2SipPropertiesManagement(String name) {
		this.name = name;
		//binding.setClassAttribute(CLASS_ATTRIBUTE);
		//binding.setAlias(Multiplex.class, "pcm");
	}

	protected static Isup2SipPropertiesManagement getInstance(String name) {
		//logger.error("--- requesting by name; name is <" + name + "> instance is " + instance);
		if (instance == null) {
			instance = new Isup2SipPropertiesManagement(name);
		}
		return instance;
	}

	public static Isup2SipPropertiesManagement getInstance() {
		//logger.error("--- requesting w/o parameter; instance is " + instance);
		return instance;
	}

	public String getName() {
		return name;
	}

	public CicManagement getCicManagement() {
		return cicManagement;
	}
	
	public int getTimeoutBeforeIsupReset(){
		return this.TIMEOUT_BEFORE_ISUP_RESET;
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
/*	
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
*/		
	@Override
	public String getSipPeer(){
		return this.sipPeer;
	}

	@Override
	public void setSipPeer(String peer){
		this.sipPeer = peer;
	}
		
	@Override
	public String getSipIp(){
		return this.sipIp;
	}

	@Override
	public void setSipIp(String ip){
		this.sipIp = ip;
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
		this.initMultiplexes();

	}

	public void stop() throws Exception {
		// this.sessionCounters.reset();
		this.store();
	}

	/**
	 * Persist
	 */
	private void setXMLBinging(){
		binding = new XMLBinding();
		binding.setAlias(org.mobicents.isup2sip.management.Multiplex.class, "tdm");
		binding.setAlias(Integer.class,"port");
		binding.setAlias(Integer.class,"index");
		binding.setAlias(String.class,"gateway");
	}

	public void store() {

		// TODO : Should we keep reference to Objects rather than recreating
		// everytime?
		try {
			XMLObjectWriter writer = XMLObjectWriter
					.newInstance(new FileOutputStream(persistFile.toString()));
			
//			binding.setAlias(Multiplex.class, "pcm");
			this.setXMLBinging();
			writer.setBinding(binding);
			
			// Enables cross-references.
			// writer.setReferenceResolver(new XMLReferenceResolver());
			writer.setIndentation(TAB_INDENT);

			writer.write(this.remoteSPC, REMOTE_PC,  Integer.class);
			writer.write(this.sipIp, SIP_IP,  String.class);
			writer.write(this.sipPeer, SIP_PEER,  String.class);
			writer.write(this.interfaces, INTERFACES, FastSet.class);

			
			writer.close();
			
			logger.error("sipIp=" + this.sipIp + ", sipPeer=" + this.sipPeer); 
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

//			binding.setAlias(Multiplex.class, "pcm");
   //			reader.setBinding(binding);
			this.setXMLBinging();
			reader.setBinding(binding);
			
			this.remoteSPC = reader.read(REMOTE_PC, Integer.class);
			this.sipIp = reader.read(SIP_IP, String.class);
			this.sipPeer = reader.read(SIP_PEER, String.class);
			this.interfaces = reader.read(INTERFACES, FastSet.class);
			
			reader.close();
		} catch (Exception e) {
			logger.error("Error while loading file",e);
			// this.logger.info(
			// "Error while re-creating Linksets from persisted file", ex);
		}
	}
	/*
	 * private void setUpDataSource() throws NamingException { Context ctx = new
	 * InitialContext(); this.dataSource = (DataSource)
	 * ctx.lookup("java:DefaultDS"); }
	 */
	
	public void registerMgcpManagement(){
		this.mgcpManagementSbbReady = true;
		logger.info("MgcpManagementSbb registered");
		
		if(this.cicManagement != null) return;	// nothing to do if started already
		
		if(this.isupManagementSbbReady){
			for (FastSet.Record r = interfaces.head(), end = interfaces.tail(); (r = r.getNext()) != end;) {
				Multiplex mux = interfaces.valueOf(r);
				cicManagement.resetMultiplex(mux.getIndex());
			}
		}
	}

	public void registerIsupManagement(){
		this.isupManagementSbbReady = true;
		logger.info("IsupManagementSbb registered");
		
		if(this.cicManagement != null) return;	// nothing to do if started already

		if(this.mgcpManagementSbbReady){
			for (FastSet.Record r = interfaces.head(), end = interfaces.tail(); (r = r.getNext()) != end;) {
				Multiplex mux = interfaces.valueOf(r);
				cicManagement.resetMultiplex(mux.getIndex());
			}
		}
	}

}
