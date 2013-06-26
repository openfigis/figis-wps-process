package org.fao.fi.figis.geoserver.wps.utils.csquare;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.geotools.geometry.jts.JTSFactoryFinder;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;


/** A C-square code class associated to a set of methods such as to check the c-square code validity, to get the
 * limits of the c-square, or also to convert to a Polygon geometry or to a layer. 
 *
 * @author eblondel
 * 
 */

public class CsquareCode {

	Logger logger = Logger.getLogger(CsquareCode.class);
	
    public String csq;
    private String csq_string;
    private String err_msg = null;
    //private String asterisk_start_reached = "N";
    private String leading_digit = null;
    private String trailing_digit = null;
    private Double start_lat;
    private Double start_long;
    private Double end_lat;
    private Double end_long;
    private Double temp_sqr_size = 100.0;
    public String N_limit;
    public String S_limit;
    public String W_limit;
    public String E_limit;
    public Map<String, Double> limits = new HashMap<String, Double>();

    
    /** CsquareCode constructor
     * 
     * @param startCsquareCode
     */
    public CsquareCode(String startCsquareCode) {
        this.csq = startCsquareCode;
    }

    
    /** Get the c-square string
     * 
     * @return
     */
    public String getValue(){
    	return this.csq;
    }
    
    
    /** Method to assess the c-square validity
     * 
     * @return
     */
    public Boolean isValid() {
        String[] globalQuadrant = {"1", "3", "5", "7"};
        String[] inter1Quadrant = {"1", "2", "3", "4"};
        String[] inter2Quadrant = {"0", "1", "2", "3", "4"};
        String[] inter3Quadrant = {"5", "6", "7", "8", "9"};
        Set<String> globalQuadrantNames = new HashSet<String>(Arrays.asList(globalQuadrant));
        Set<String> inter1QuadrantNames = new HashSet<String>(Arrays.asList(inter1Quadrant));
        Set<String> inter2QuadrantNames = new HashSet<String>(Arrays.asList(inter2Quadrant));
        Set<String> inter3QuadrantNames = new HashSet<String>(Arrays.asList(inter3Quadrant));

        csq_string = csq.replaceAll("[0-9]+", "").replaceAll("\\*", "").replaceAll(":", "");

        //something remains after replacement, not a good character
        if (csq_string.length() > 0) {
            err_msg = "bad character found in " + csq;

            // c-square code must be minimum 4 characters
        } else if (csq == null || csq.length() < 4) {
            err_msg = "c-square code missing or incomplete";

            // test length of first cycle (to initial colon), reject if other than 4 characters
            // (add a trailing colon in case none exists)
        } else if ((csq + ":").indexOf(":") < 4) {
            err_msg = "initial cycle contains less than 4 digits";

        } else if ((csq + ":").indexOf(":") > 4) {
            err_msg = "initial cycle contains more than 4 digits";

        } else {
            // first cycle is 4 characters
            //now test they are all digits (no asterisks allowed)
            csq_string = csq.substring(0, 3).replaceAll("[0-9]+", "");
            Integer minAbsLat = Integer.parseInt(csq.substring(1, 2));
            Integer minAbsLon = Integer.parseInt(csq.substring(2, 4));

            //a character has been entered not in the set 0-9, or ^ has been entered
            if (csq_string.length() > 0) {
                err_msg = "bad character found in " + csq;

                //check global quadrant (first character) for validity - must be 1, 3, 5 or 7
            } else if (!globalQuadrantNames.contains(csq.substring(0, 1))) {
                err_msg = "illegal global quadrant value at " + csq.substring(0, 1) + "xxx";


                // check tens of degrees of latitude and longitude for validity
                // minimum absolute latitude: character 2 (cannot be more than 89.9999999...)
            } else if (!(minAbsLat >= 0 && minAbsLat < 9)) {
                err_msg = "illegal latitude value at x" + csq.substring(1, 2) + "xx";

                // minimum absolute longitude: characters 3 and 4 (cannot be more than 179.999999...)	
            } else if (!(minAbsLon >= 0 && minAbsLon < 18)) {
                err_msg = "illegal longitude value at xx" + csq.substring(2, 4);

            } //end if

        }//end if

        if (err_msg == null && csq.length() > 4) {

            //more than one cycle is present
            csq_string = csq;


            // detect any valid remaining final "truncated" cycle (colon + single digit 1-4) and delete,
            // flag if invalid value present

            if (err_msg == null) {

                if (csq_string.endsWith(":1") || csq_string.endsWith(":2") || csq_string.endsWith(":3") || csq_string.endsWith(":4")) {

                    csq_string = csq_string.substring(0, csq_string.length() - 2);

                } else if (csq_string.endsWith(":5") || csq_string.endsWith(":6") || csq_string.endsWith(":7") || csq_string.endsWith(":8") || csq_string.endsWith(":9")) {

                    err_msg = "illegal final intermediate quadrant value";

                } else if (csq_string.endsWith(":")) {

                    err_msg = "code cannot terminate with a colon";

                } // end if

            } // end if

            //now examine each cycle in turn, following the initial 4 digits
            //(any valid final "truncated" cycle has already been stripped, 
            //also any valid segments containing asterisks, so any remaining
            //ones should be 3 digits, with no asterisks)

            //remove initial 4 digits
            if (csq_string.length() > 4) {

                csq_string = csq_string.substring(5);
                while (csq_string.length() > 0 && err_msg == null) {

                    //cycle must be a colon plus 3 digits (plus additional cycles if present)
                    if (csq_string.length() < 3) {

                        err_msg = "incomplete cycle after colon character";

                        // now check length of the cycle - to next colon character
                        // (trailing colon added in case not present)
                    } else if ((csq_string + ":").indexOf(":") > 3) {

                        err_msg = "cycle :" + csq_string.substring(0, 3) + "... contains more than 3 digits";

                        // check for valid intermediate quadrant digit
                    } else if (!inter1QuadrantNames.contains(csq_string.substring(0, 1))) {

                        err_msg = "illegal intermediate quadrant value at :" + csq_string.substring(0, 3);

                        // if three digits are correctly present in cycle, second and third must agree
                        // with the designated intermediate quadrant
                    } else if ((csq_string.substring(0, 1).matches("1")
                            && (!inter2QuadrantNames.contains(csq_string.substring(1, 2))
                            || !inter2QuadrantNames.contains(csq_string.substring(2, 3)))) || (csq_string.substring(0, 1).matches("2")
                            && (!inter2QuadrantNames.contains(csq_string.substring(1, 2))
                            || !inter3QuadrantNames.contains(csq_string.substring(2, 3)))) || (csq_string.substring(0, 1).matches("3")
                            && (!inter3QuadrantNames.contains(csq_string.substring(1, 2))
                            || !inter2QuadrantNames.contains(csq_string.substring(2, 3)))) || (csq_string.substring(0, 1).matches("4")
                            && (!inter3QuadrantNames.contains(csq_string.substring(1, 2))
                            || !inter3QuadrantNames.contains(csq_string.substring(2, 3))))) {

                        err_msg = "illegal triplet at :" + csq_string.substring(0, 3);

                    } // end if

                    //move to the next cycle (strip the one just tested)
                    if (csq_string.length() > 3) {
                        csq_string = csq_string.substring(4);
                    } else {
                        csq_string = "";
                    }


                }// end loop

            }

        } // end if

        if (err_msg != null) {

            return false;

        } else {

            return true;
        }

        /* End of code validation section*/
    }

    
    /** Method to retrieve the reason of invalidated c-square code
     * 
     * @return
     */
    public String isValidReason() {
        if (this.isValid() == true) {
            return "Valid c-square code";
        } else {
            return this.err_msg;
        }
    }

    
    
    /** Method to get the c-square limits
     * 
     * @return
     */
    public Map<String, Double> getLimits() {

        // code is valid, get the N, S, W, E limits
        if (this.isValid() == true) {

            //reset csq_string to original value
            csq_string = csq;

            // remove any chains of asterisks if present (calculate boundaries on the total "compressed" area)
			/*
            if (csq_string.contains("*")){
            
            while (csq.endsWith("*") || csq_string.endsWith(":")){
            
            csq_string = csq_string.substring(0,csq_string.length()-1);
            
            }// end loop
            } // end if
             */

            // get the leading digit
            leading_digit = csq_string.substring(0, 1);

            // test for any trailing digit, save if found and then strip last 2 characters
            // to ensure string ends in a complete triplet

            if (csq_string.endsWith(":1")) {

                trailing_digit = "1";
                csq_string = csq_string.substring(0, csq_string.length() - 2);

            } else if (csq_string.endsWith(":2")) {

                trailing_digit = "2";
                csq_string = csq_string.substring(0, csq_string.length() - 2);

            } else if (csq_string.endsWith(":3")) {

                trailing_digit = "3";
                csq_string = csq_string.substring(0, csq_string.length() - 2);

            } else if (csq_string.endsWith(":4")) {

                trailing_digit = "4";
                csq_string = csq_string.substring(0, csq_string.length() - 2);

            } // end if

            // Now, parse the string to extract minimum absolute values of lat and long
            // first add a final colon (else will loop forever!)
            csq_string = csq_string + ":";

            // Go through the cycles one by one, extracting relevant digits and assembling hundreds, tens, units, etc. as required
            // E.g., lat aa.aaaa and long bbb.bbbb are encoded as "[x]abb:[y]ab:[y]ab:[y]ab:[y]ab:[y]ab"
            // where initial cycle is tens of latitude, and hundreds plus tens of longitude;
            // second cycle is units of degrees of lat and long; third cycle is tenths; fourth cycle is hundredths; etc.

            while (csq_string.length() > 0) {

                temp_sqr_size = temp_sqr_size / 10;

                if (temp_sqr_size == 10) {
                    //-- first time through, 4-digit cycle, includes 2 characters for longitude

                    start_lat = Double.parseDouble(csq_string.substring(1, 2)) * temp_sqr_size; // 1 character (tens)
                    start_long = Double.parseDouble(csq_string.substring(2, 4)) * temp_sqr_size; // 2 characters (hundreds + tens)
                } else {
                    // all other complete cycles (3 digits), single char for both latitude and longitude

                    start_lat = start_lat + (Double.parseDouble(csq_string.substring(1, 2)) * temp_sqr_size);
                    start_long = start_long + (Double.parseDouble(csq_string.substring(2, 3)) * temp_sqr_size);

                } // end if

                // strip the cycle just processed, plus colon character separator
                csq_string = csq_string.substring(csq_string.indexOf(":") + 1);


            } // end loop

            

            // get the end lat, long
            if (trailing_digit != null) {
            	
            	// add relevant extra fraction for trailing intermediate quadrant if present
                if (trailing_digit.matches("3") || trailing_digit.matches("4")) {
                    start_lat = start_lat + (temp_sqr_size * 0.5);
                } // end if

                if (trailing_digit.matches("2") || trailing_digit.matches("4")) {
                    start_long = start_long + (temp_sqr_size * 0.5);
                } // end if
            	
                //we have a square from the "intermediate" sequence (5, 0.5, 0.05, etc.)
                end_lat = start_lat + (temp_sqr_size * 0.5);
                end_long = start_long + (temp_sqr_size * 0.5);

            } else {
                //square is from main sequence (10, 1, 0.1, etc.)
                end_lat = start_lat + temp_sqr_size;
                end_long = start_long + temp_sqr_size;

            } // end if		

            // translate absolute values to correctly signed values
            // (transpose starts, ends as appropriate)

            if (leading_digit.matches("1")) {

                //NE global quadrant
                N_limit = end_lat.toString();
                S_limit = start_lat.toString();
                W_limit = start_long.toString();
                E_limit = end_long.toString();

            } else if (leading_digit.matches("3")) {
                //SE global quadrant, lats are negative
                N_limit = "-" + start_lat.toString();
                S_limit = "-" + end_lat.toString();
                W_limit = start_long.toString();
                E_limit = end_long.toString();

            } else if (leading_digit.matches("5")) {
                //SW global quadrant, lats and longs are both negative
                N_limit = "-" + start_lat.toString();
                S_limit = "-" + end_lat.toString();
                W_limit = "-" + end_long.toString();
                E_limit = "-" + start_long.toString();

            } else if (leading_digit.matches("7")) {
                //NW global quadrant, longs are negative
                N_limit = end_lat.toString();
                S_limit = start_lat.toString();
                W_limit = "-" + end_long.toString();
                E_limit = "-" + start_long.toString();

            } // end if



            //add trailing ".0" if appropriate
            if (!N_limit.contains(".")) {
                N_limit = N_limit + ".0";
            }//endif
            if (!S_limit.contains(".")) {
                S_limit = S_limit + ".0";
            }//endif
            if (!W_limit.contains(".")) {
                W_limit = W_limit + ".0";
            }//endif
            if (!E_limit.contains(".")) {
                E_limit = E_limit + ".0";
            }//endif

            //add leading "0" if appropriate
            if (N_limit.startsWith(".")) {
                N_limit = "0" + N_limit;
            } else if (N_limit.startsWith("-.")) {
                N_limit = "-0" + N_limit.substring(1);
            }//endif

            if (S_limit.startsWith(".")) {
                S_limit = "0" + S_limit;
            } else if (S_limit.startsWith("-.")) {
                S_limit = "-0" + S_limit.substring(1);
            }//endif

            if (W_limit.startsWith(".")) {
                W_limit = "0" + W_limit;
            } else if (W_limit.startsWith("-.")) {
                W_limit = "-0" + W_limit.substring(1);
            }//endif

            if (E_limit.startsWith(".")) {
                E_limit = "0" + E_limit;
            } else if (E_limit.startsWith("-.")) {
                E_limit = "-0" + E_limit.substring(1);
            }//endif

        }//end if

        
        //To double
        Double lat_max;
        if (N_limit.startsWith("-")) {
            lat_max = -Double.parseDouble(N_limit.substring(1));
        } else {
            lat_max = Double.parseDouble(N_limit);
        }

        Double lat_min;
        if (S_limit.startsWith("-")) {
            lat_min = -Double.parseDouble(S_limit.substring(1));
        } else {
            lat_min = Double.parseDouble(S_limit);
        }

        Double long_min;
        if (W_limit.startsWith("-")) {
            long_min = -Double.parseDouble(W_limit.substring(1));
        } else {
            long_min = Double.parseDouble(W_limit);
        }

        Double long_max;
        if (E_limit.startsWith("-")) {
            long_max = -Double.parseDouble(E_limit.substring(1));
        } else {
            long_max = Double.parseDouble(E_limit);
        }

        limits.put("S_limit", lat_min);
        limits.put("N_limit", lat_max);
        limits.put("W_limit", long_min);
        limits.put("E_limit", long_max);
        return limits;
       

    }
    
    

    /** Create a c-square polygon. Internally, the method gets the c-square limits and computes them in a Polygon object
     * 
     * @return a Polygon
     */
    public Polygon toPolygon() {
        Map<String, Double> maptest = this.getLimits();
        Double lat_min = maptest.get("S_limit");
        Double lat_max = maptest.get("N_limit");
        Double long_min = maptest.get("W_limit");
        Double long_max = maptest.get("E_limit");

        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);
        Coordinate[] coords =
                new Coordinate[]{new Coordinate(long_min, lat_max), new Coordinate(long_min, lat_min),
            new Coordinate(long_max, lat_min), new Coordinate(long_max, lat_max), new Coordinate(long_min, lat_max)};

        LinearRing ring = geometryFactory.createLinearRing(coords);
        LinearRing holes[] = null; // use LinearRing[] to represent holes
        Polygon polygon = geometryFactory.createPolygon(ring, holes);
        polygon.setSRID(4326);
        return polygon;

    }
    
    /** Get c-square center as JTS Point geometry
     * 
     * @return
     */
    public Point getCsquareCenter(){	
    	return this.toPolygon().getCentroid();
    }
    
    
    
    /** A method to get back a Point object (center of the c-square)
     * 
     * @return
     */
    public CsquarePoint toCsquarePoint(){
    	Double x = this.toPolygon().getCentroid().getX();
    	Double y = this.toPolygon().getCentroid().getY();
    	return new CsquarePoint(x,y);
    }

    
   

}