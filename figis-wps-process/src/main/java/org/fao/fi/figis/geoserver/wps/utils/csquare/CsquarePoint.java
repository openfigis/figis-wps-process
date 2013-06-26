package org.fao.fi.figis.geoserver.wps.utils.csquare;

import org.geotools.geometry.jts.JTSFactoryFinder;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import it.cnr.aquamaps.CSquare;



public class CsquarePoint{
	
	double lat;
	double lon;

	
	/** Constructor 1
	 * 
	 * @param lat
	 * @param lon
	 */
	public CsquarePoint(double inputLongitude, double inputLatitude){
		this.lon = inputLongitude;
		this.lat = inputLatitude;		
	}

	
	/** Constructor 2
	 * 
	 */
	public CsquarePoint(){
	}

	
	
	/** Method to set the point longitude
	 * 
	 * @param inputLongitude
	 */
	public void setLon(double inputLongitude){
		this.lon = inputLongitude;
	}

	
	
	/** Method to set the Point latitude
	 * 
	 * @param inputLatitude
	 */
	public void setLat(double inputLatitude){
		this.lat = inputLatitude;
    }

	
	
	/** Method to get a Csquarecode.
	 *  The conversion to c-square string was initially based on the following code http://www.marine.csiro.au/csquares/Point_java.txt (author: Phoebe Zhang).
	 *  This conversion is now done using the geo-utils-custom-geopeo (gCube Externals). Finally, the string is encapsulated in a CsquareCode object, so actions
	 *  such as testing the c-square validity, or retrieving the non-validity reason can be performed.
	 * 
	 * @param resolution
	 * @return a CsquareCode object
	 */
	public CsquareCode getCsquareCode(double inputResolution){
		String csquare = CSquare.centroidToCode(lat, lon, inputResolution);
		return new CsquareCode(csquare);
	}

	
	/** Get Index
	 * 
	 * @param inputResolution
	 * @return
	 */
	public int getIndex(double inputResolution){
		int index = ((Double) (Math.abs(this.lon * this.lat) / inputResolution)).intValue();
		return index;
	}
	
	
	/** A method to convert to a JTS Point object
	 * 
	 * @return a JTS point object
	 */
	public Point toPoint(){
		
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);
		Coordinate coord = new Coordinate(this.lon, this.lat);
		Point point = geometryFactory.createPoint(coord);
		point.setSRID(4326);
		return point;

	}
	
	
}


