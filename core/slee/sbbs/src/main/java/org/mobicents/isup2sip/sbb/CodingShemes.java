package org.mobicents.isup2sip.sbb;

public class CodingShemes {
	public static String numberFromURI(String uri){
		if(uri.toLowerCase().startsWith("sip:")){
			return uri.substring(uri.indexOf(':') + 1, uri.indexOf('@'));
		}
		return uri.substring(0, uri.indexOf('@'));
	}
}