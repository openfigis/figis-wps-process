package org.fao.fi.figis.geoserver.wps.feature;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.fao.fi.figis.geoserver.wps.FigisProcess;
import org.fao.fi.figis.geoserver.wps.feature.visitor.CsquareVisitor;
import org.fao.fi.figis.geoserver.wps.utils.csquare.CsquareCode;
import org.fao.fi.figis.geoserver.wps.utils.csquare.CsquareUtils;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.visitor.FeatureCalc;
import org.geotools.process.ProcessException;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.feature.gs.ReprojectProcess;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.util.NullProgressListener;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;


/** A process to perform an square aggregation of point data.
 *  The process inputs a Point Feature Collection, and returns a Polygon square feature collection, which resolution
 *  have been specified as input. Internally, the process uses the c-square coding system.
 * 
 * @author Emmanuel Blondel (FAO)
 * <emmanuel.blondel@fao.org><emmanuel.blondel1gmail.com>
 *
 */
@DescribeProcess(title="AggregateByCsquare",
				 description="Performs an aggregation of point data by squares defined by a given resolution. The process "+
						 	 "uses the c-square coding system to aggregate the points."
				 )
public class AggregateByCsquare implements FigisProcess{

	
	FeatureCalc calc;
	SimpleFeatureType targetSchema;
	SimpleFeatureBuilder fb;
	
	@DescribeResult(name="result", description="output result")
	 public SimpleFeatureCollection execute(
			 @DescribeParameter(name="data",description="A geometry Point feature collection to aggregate") SimpleFeatureCollection features,
			 @DescribeParameter(name="resolution",description="Square resolution (in decimal degrees). The lowest resolution is 10 degrees, and there is no higher resolution limit. "
					 										  +"Resolutions values can be: 10, 5, 1, 0.5, 0.1, 0.05 etc") Double resolution									  
					 										  
			) throws IOException{
		
		
		//check input geometry type
		if(!features.getSchema().getGeometryDescriptor().getType().getBinding().equals(Point.class)){
			throw new IllegalArgumentException("Input data must be a Point feature collection");
		}
		   
        //check input CRS
		CoordinateReferenceSystem inputCRS = features.getSchema().getCoordinateReferenceSystem();
        if(!CRS.equalsIgnoreMetadata(inputCRS, DefaultGeographicCRS.WGS84 )){
        	try{
        		ReprojectProcess reproject = new ReprojectProcess();
        		features = reproject.execute(features, inputCRS, DefaultGeographicCRS.WGS84);
        		
        	}catch (Exception e){
            	throw new ProcessException("Input CRS different from WGS84. Reprojection intent failed: ", e);
            }
        }
		
        //check input resolution
        if(!CsquareUtils.isValidResolution(resolution)){
        	throw new IllegalArgumentException("The input resolution is not valid");
        }
        
        
		this.createSimpleFeatureBuilder(features, resolution);
		return this.getResults(features, resolution);
		
	};
	
	
	/** create SimpleFeatureBuilder
	 * 	
	 * @param features
	 * @param resolution
	 */
	public void createSimpleFeatureBuilder(SimpleFeatureCollection features, Double resolution){

		// SimpleFeatureType & SimpleFeatureBuilder
		SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
		tb.setName(features.getSchema().getName());
		tb.setCRS(features.getSchema().getCoordinateReferenceSystem());
				        
		tb.add("THE_GEOM", Polygon.class);
		tb.add("CSQUARECODE", String.class);
		tb.add("RESOLUTION", Double.class);
		tb.add("COUNT", Integer.class);
				        
		tb.setName(features.getSchema().getName());
		this.targetSchema = tb.buildFeatureType();
		this.fb = new SimpleFeatureBuilder(this.targetSchema);
	}
	
	
	/** Method to compute the result
	 * 
	 * @param resolution
	 * @return
	 * @throws IOException 
	 */
	public SimpleFeatureCollection getResults(SimpleFeatureCollection features, Double resolution) throws IOException{
		
		List<SimpleFeature> featuresList = new ArrayList<SimpleFeature>();
		
		this.calc = new CsquareVisitor(resolution);
		features.accepts(calc, new NullProgressListener());
		Map<String,Integer> map = (Map<String, Integer>) calc.getResult().getValue();
		
		Iterator<String> iterator = map.keySet().iterator();
		while(iterator.hasNext()){
			String csq = iterator.next();
			CsquareCode code = new CsquareCode(csq);
				
			fb.set("THE_GEOM", code.toPolygon());
			fb.set("CSQUARECODE", code.getValue());
			fb.set("RESOLUTION", resolution);
			fb.set("COUNT", map.get(csq));
			featuresList.add(fb.buildFeature(code.getValue())); 
			fb.reset();
			
		}
		
		SimpleFeatureCollection result = new ListFeatureCollection(this.targetSchema, featuresList);
		return result;		
	}
	
}