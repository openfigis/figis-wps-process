package org.fao.fi.figis.geoserver.wps.feature.visitor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.fao.fi.figis.geoserver.wps.utils.csquare.CsquareCode;
import org.fao.fi.figis.geoserver.wps.utils.csquare.CsquarePoint;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.visitor.AbstractCalcResult;
import org.geotools.feature.visitor.CalcResult;
import org.geotools.feature.visitor.FeatureCalc;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

/**
 * CsquareVisitor
 * 
 * 
 * @author eblondel (FAO)
 *
 */
public class CsquareVisitor implements FeatureCalc{

	
	Double resolution;
	Map<String,Integer> squares = new HashMap<String,Integer>();;
	
	
	/** Constructor
	 * @param resolution
	 */
	public CsquareVisitor(Double resolution){
		this.resolution = resolution;
	}
	
	
	
	/** Method to truncate the point coordinates
	 *  Due to some instability of the Csquare utility class used in CsquarePoint
		- coordinates will a large amount of digits: e.g.
			double x = -49.99999999999994;
			double y = 35.75000000000006;
			lead to a wrong csquare string: in this case it gives 7304:350 instead of 7304:459
	 * 
	 * @param point
	 * @return a point with truncated coordinates
	 */
	public Point truncate(Point point){
		GeometryFactory gf = JTSFactoryFinder.getGeometryFactory(null);
		
		double x = point.getX();
		double y = point.getY();
		
		BigDecimal bdx = new BigDecimal(x);
	    bdx = bdx.setScale(4, BigDecimal.ROUND_DOWN);	    
	    BigDecimal bdy = new BigDecimal(y);
	    bdy = bdy.setScale(4, BigDecimal.ROUND_DOWN);
	    
	    return gf.createPoint(new Coordinate(bdx.doubleValue(),bdy.doubleValue())); 
	}
	
	
	
	public void visit(Feature feature) {
		Point point = (Point) feature.getDefaultGeometryProperty().getValue();
		point = this.truncate(point);
		
		CsquarePoint csqPoint = new CsquarePoint(point.getX(), point.getY());
		CsquareCode csq = csqPoint.getCsquareCode(this.resolution);
		Polygon p = csq.toPolygon();
		
		//get csquare and count point by csquare (with an intersects control)
		if(point.intersects(p)){
			if(!squares.keySet().contains(csq.getValue())){
				squares.put(csq.getValue(), 1);
			}else{
				Integer count = squares.get(csq.getValue());
				squares.put(csq.getValue(), count+1);			
			}
		}
	}
	
	public void visit(SimpleFeature feature) {
        visit(feature);
    }
	
	
	public void init(SimpleFeatureCollection collection) {
    	//do nothing
    }
    
    
	public Double getResolution(){
		return this.resolution;
	}
	
	
	public void setValue(Map<String, Integer> newMap) {
    	this.squares = newMap;
    	
    }
    
    public void reset() {
        this.squares = new HashMap<String, Integer>();
    }
    

    
    
	/** get results
	 * 
	 */
	public CalcResult getResult() {
		if(squares.size() == 0) {
    		return CalcResult.NULL_RESULT;
    	}
        return new CsquareCountResult(squares);
	}
	
	
	
	/**
	 * CsquareCountResult class
	 *
	 */
	public static class CsquareCountResult extends AbstractCalcResult {
		
		private Map<String, Integer> map;
		
		
		public CsquareCountResult(Map<String, Integer> squares){
			this.map = squares;
		}
		
		public Object getValue() {
        	return new HashMap<String,Integer>(map);
        }
        
        public boolean isCompatible(CalcResult targetResults) {
            //list each calculation result which can merge with this type of result
        	if (targetResults == CalcResult.NULL_RESULT) return true;
        	return false;
        }

        
        public CalcResult merge(CalcResult resultsToAdd) {
            if (!isCompatible(resultsToAdd)) {
                throw new IllegalArgumentException(
                    "Parameter is not a compatible type");
            }
            
            return this;

        }
		
		
	}

	
}
