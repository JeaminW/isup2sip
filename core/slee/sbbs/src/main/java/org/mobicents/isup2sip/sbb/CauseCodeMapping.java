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

public class CauseCodeMapping {
		public static int statusToCause(int status){
			switch(status){
			case 400: return 41;//Bad Request                       41 Temporary Failure
			case 401: return 21;//Unauthorized                      21 Call rejected (*)
			case 402: return 21 ;//Payment required                  21 Call rejected
			case 403: return 21;//Forbidden                         21 Call rejected
			case 404: return 1;//Not found                          1 Unallocated number
			case 405: return 63;//Method not allowed                63 Service or option unavailable
			case 406: return 79;//Not acceptable                    79 Service/option not implemented (+)
			case 407: return 21;//Proxy authentication required     21 Call rejected (*)
			case 408: return 102;//Request timeout                  102 Recovery on timer expiry
			case 410: return 22;//Gone                              22 Number changed (w/o diagnostic)
			case 413: return 127;//Request Entity too long          127 Interworking (+)
			case 414: return 127;//Request-URI too long             127 Interworking (+)
			case 415: return 79;//Unsupported media type            79 Service/option not implemented (+)
			case 416: return 127;//Unsupported URI Scheme           127 Interworking (+)
			case 420: return 127;//Bad extension                    127 Interworking (+)
			case 421: return 127;//Extension Required               127 Interworking (+)
			case 423: return 127;//Interval Too Brief               127 Interworking (+)
			case 480: return 18;//Temporarily unavailable           18 No user responding
			case 481: return 41;//Call/Transaction Does not Exist   41 Temporary Failure
			case 482: return 25;//Loop Detected                     25 Exchange - routing error
			case 483: return 25;//Too many hops                     25 Exchange - routing error
			case 484: return 28;//Address incomplete                28 Invalid Number Format (+)
			case 485: return 1;//Ambiguous                          1 Unallocated number
			case 486: return 17;//Busy here                         17 User busy
//			case 487: return ;//Request Terminated               --- (no mapping)
//			case 488: return ;//Not Acceptable here              --- by Warning header
			case 500: return 41;//Server internal error             41 Temporary failure
			case 501: return 79;//Not implemented                   79 Not implemented, unspecified
			case 502: return 38;//Bad gateway                       38 Network out of order
			case 503: return 41;//Service unavailable               41 Temporary failure
//			case 504: return 102;//Server time-out                  102 Recovery on timer expiry
			case 504: return 127;//Version Not Supported            127 Interworking (+)
			case 513: return 127;//Message Too Large                127 Interworking (+)
			case 600: return 17;//Busy everywhere                   17 User busy
			case 603: return 21;//Decline                           21 Call rejected
			case 604: return 1;//Does not exist anywhere            1 Unallocated number
//			case 606: return ;//Not acceptable                   --- by Warning header			
			}
			return 16;		//"Normal clearing"
		}
}
