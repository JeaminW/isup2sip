package org.mobicents.isup2sip.management;

import javolution.xml.XMLFormat;
import javolution.xml.XMLSerializable;
import javolution.xml.stream.XMLStreamException;

public class Multiplex implements XMLSerializable{
	
	private static final String INDEX 	= "index";
	private static final String GATEWAY 	= "gateway";
	private static final String PORT 		= "port";
	
	protected int index;
	
	protected String gateway;
	
	protected int port;
	
	public Multiplex(int num, String gw, int gatewayPort){
		index = num;
		gateway = gw;
		port = gatewayPort;
	}
	public Multiplex(){
		index = -1;
		gateway = new String();
		port = -1;
	}
	
	public int getIndex(){
		return this.index;
	}
	
	public String getGateway(){
		return this.gateway;
	}
	
	public int getPort(){
		return this.port;
	}

	public void setIndex(int value){
		this.index = value;
	}
	
	public void setGateway(String value){
		this.gateway = value;
	}
	
	public void setPort(int value){
		this.port = value;
	}
	
	public String toString(){
		return "multiplex " + index + " gateway " + gateway + " port " + port;
	}
	
	protected static final XMLFormat<Multiplex> ASP_FACTORY_XML = new XMLFormat<Multiplex>(Multiplex.class) {

		@Override
		public void read(javolution.xml.XMLFormat.InputElement xml, Multiplex mux)
				throws XMLStreamException {
			mux.index 	= xml.getAttribute(INDEX, 0);
			mux.gateway = xml.getAttribute(GATEWAY, "-");
			mux.port 	= xml.getAttribute(PORT, 0);
//			mux.setGateway(xml.getAttribute(GATEWAY, " - "));
		}

		@Override
		public void write(Multiplex mux, javolution.xml.XMLFormat.OutputElement xml)
				throws XMLStreamException {
			xml.setAttribute(INDEX, mux.index);
			xml.setAttribute(GATEWAY, mux.gateway);
			xml.setAttribute(PORT, mux.port);
		}
	};

}
