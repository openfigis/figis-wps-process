package org.fao.fi.figis.geoserver.wps.utils.csquare;

/**
 * Csquare utils
 * 
 * @author eblondel
 *
 */
public final class CsquareUtils {

	
	
	/** check the validity of the c-square resolution
	 * 
	 * @param value
	 * @return
	 */
	public static boolean isValidResolution(double value){
		
		//nb of decimals
		int dec = (int) Math.abs( Math.floor(Math.log(value) / Math.log(10)) );
		
		//validity rules
		boolean result = false;
		if(value == 10){
			return true;
			
		}else if (value < 10){
			double res = value * Math.pow(10, dec);
			if(res==1 || res==5){
				result = true;
			}
		}
		
		return result;
	}
	
	
}
