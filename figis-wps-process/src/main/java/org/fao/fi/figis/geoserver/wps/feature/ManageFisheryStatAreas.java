package org.fao.fi.figis.geoserver.wps.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


import org.fao.fi.figis.geoserver.wps.FigisProcess;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.precision.EnhancedPrecisionOp;



/** A process to manage the master FAO areas GIS layer
 *  The result is a Feature Collection for which each feature is a single FAO area layer. Such result should
 *  be published in Geoserver and used to publish other derivated layers such as:
 *  - the 5 FAO area layers used in the FIGIS applications/viewers (after DB import of the results, these ones can be
 *    easily published as SQL View layers)
 *  - input as other WPS processes (e.g. Erase)
 *  
 *  The process also manages the STATUS of the areas and thus allows, after DB import, to publish 2 different FAO area layers
 *  - 1 with only officially recognized FAO areas (for dissemination) through an SQL View layer
 *  - 1 with all areas whatever the status (for use in FIGIS applications/viewers)
 * 
 * @author Emmanuel Blondel (FAO)
 * <emmanuel.blondel@fao.org><emmanuel.blondel1gmail.com>
 *
 */
@DescribeProcess(title="Manage Fishery Statistical Areas",
				 description="Manage the master FAO areas GIS layer"	 	 
				 )
public class ManageFisheryStatAreas implements FigisProcess{

	List<SimpleFeature> result;

	Set<FisheryStatArea> areas;	
	SimpleFeatureCollection masterCollection;
	SimpleFeatureType schema;
	SimpleFeatureBuilder fb;
	
	SimpleFeatureCollection filteredCollection;
	SimpleFeatureCollection dissolvedCollection;
	
	
	/** FisheryStatArea
	 *  Defines the fishery stat area levels & property name that handles the fishery area code
	 *
	 */
	public enum FisheryStatArea{
		
		MAJOR ("F_AREA","MAJOR"),
		SUBAREA ("F_SUBAREA","SUBAREA"),
		DIVISION ("F_DIVISION","DIVISION"),
		SUBDIVISION ("F_SUBDIVIS","SUBDIVISION"),
		SUBUNIT("F_SUBUNIT","SUBUNIT");
		
		private final String propertyName;
		private final String levelName;
		
		FisheryStatArea(String propertyName, String levelName){
			this.propertyName = propertyName;
			this.levelName = levelName;
		}
		
		public String propertyName(){
			return this.propertyName;
		}
		
		public String levelName(){
			return this.levelName;
		}
		
	}
	
	/** Execute the WPS process
	 * 
	 * @param features
	 * @return
	 */
	@DescribeResult(name="result", description="output result")
	 public SimpleFeatureCollection execute(
			 @DescribeParameter(name="data",description="FAO raw fishery statistical area layer (master layer)") SimpleFeatureCollection features
			 ){
	
		this.masterCollection = features;
		this.areas = this.getFisheryStatAreas();
		createSimpleFeatureBuilder();
		
		result = new ArrayList<SimpleFeature>();
		
		Iterator<FisheryStatArea> areaIt = areas.iterator();
		while(areaIt.hasNext()){
			result.addAll(this.dissolveByFisheryArea(areaIt.next()));
		}
		
		return new ListFeatureCollection(schema, result);
		
	};
	 
	
	
	/** Get the set of FisheryStatArea (linked hashset)
	 * 
	 * @return
	 */
	public Set<FisheryStatArea> getFisheryStatAreas(){
	    Set<FisheryStatArea> set = new LinkedHashSet<FisheryStatArea>();    				
	    set.add(FisheryStatArea.MAJOR);
	    set.add(FisheryStatArea.SUBAREA);
	    set.add(FisheryStatArea.DIVISION);
	    set.add(FisheryStatArea.SUBDIVISION);
	    set.add(FisheryStatArea.SUBUNIT);
	    return set;
	}
	

 
		
    /** create SimpleFeatureBuilder
    * 	
    * @param features
    * @param resolution
    */
    public void createSimpleFeatureBuilder(){

    	SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
    	tb.setName("FAO_AREAS");
    	tb.setCRS(DefaultGeographicCRS.WGS84);		        
    	tb.add("THE_GEOM", MultiPolygon.class);
    	tb.add("F_LEVEL", String.class); //add attribute to handle the level
    	tb.add("F_CODE", String.class);
    	tb.add("OCEAN",String.class);
    	tb.add("SUBOCEAN",String.class);
    	
    	Iterator<FisheryStatArea> it = areas.iterator();
    	while(it.hasNext()){
    		tb.add(it.next().propertyName(),String.class);
    	}
    	tb.add("F_STATUS",Integer.class);
    				        
    	this.schema = tb.buildFeatureType(); //SimpleFeatureType
    	this.fb = new SimpleFeatureBuilder(this.schema); //SimpleFeatureBuilder
    }
    	


	
	/** Build a dissolved feature collection for a given Fishery stat. area level
     * 
     * @param area
     * @return
     */
    public List<SimpleFeature> dissolveByFisheryArea(FisheryStatArea area){
    	
    	List<SimpleFeature> dissolvedFeatures = new ArrayList<SimpleFeature>();
    	
    	//list of unique codes
    	Set<String> codeValues = new HashSet<String>();
    	SimpleFeatureIterator it = masterCollection.features();
    	try{
    		while(it.hasNext()){
    			SimpleFeature f = it.next();
    			String codeValue = (String) f.getAttribute(area.propertyName());
    			if(codeValue != null){
    				if(!codeValues.contains(codeValue) && codeValue.length() != 0){
    					boolean test = codeValues.add(codeValue);
    				}
    			}
    		}
    	}finally{
    		if(it!=null){
    			it.close();
    		}
    	}

    	
    	//build the collection
    	Iterator<String> valuesIt = codeValues.iterator();
    	while(valuesIt.hasNext()){
    		
    		String value = valuesIt.next();
    		
    		//get filtered collection
    		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(null);
	        Filter areaFilter = ff.equals(ff.property(area.propertyName()), ff.literal(value));
    		filteredCollection = masterCollection.subCollection(areaFilter);
    		
    		//dissolve the filtered collection
    		SimpleFeature feature = createFeature(filteredCollection, area);
    		dissolvedFeatures.add(feature);
    	}
    	
    	return dissolvedFeatures;
    }
    
    
    /** A method to create a dissolved feature:
     *  - dissolve the geometries of a feature collection
     *  - add relevant attribute values
     * 
     * @param collection
     * @return
     */
    public SimpleFeature createFeature(SimpleFeatureCollection collection, FisheryStatArea area){
		
    	
    	String areaCode = null;
    	String subarea = null;
    	String div = null;
    	String subdiv = null;

    	
    	Integer areaStatus = 1;
    	Geometry unionGeom = null;
    	
		SimpleFeatureIterator it = collection.features();
		try{
	
			if(it.hasNext()){
				SimpleFeature f = it.next();
				unionGeom = (Geometry) f.getDefaultGeometryProperty().getValue(); //instantiate unionGeom
				
				//handle area status (endorsed or not)
				areaCode = (String) f.getAttribute(area.propertyName());
				if(areaCode != null){
					if(areaCode.startsWith("_")){
						areaCode = areaCode.substring(1);
						areaStatus = 0;
					}
				}
				
				//from the first feature get the other relevant attributes and feed the feature builder
				if(area.equals(FisheryStatArea.SUBAREA)){
					fb.set(FisheryStatArea.MAJOR.propertyName(), (String) f.getAttribute(FisheryStatArea.MAJOR.propertyName()));
					
				}else if(area.equals(FisheryStatArea.DIVISION)){
					subarea = (String) f.getAttribute(FisheryStatArea.SUBAREA.propertyName());
					if(areaStatus == 0){
						subarea = subarea.substring(1);
					}
					
					fb.set(FisheryStatArea.MAJOR.propertyName(), (String) f.getAttribute(FisheryStatArea.MAJOR.propertyName()));
					fb.set(FisheryStatArea.SUBAREA.propertyName(), subarea);
					
				}else if(area.equals(FisheryStatArea.SUBDIVISION)){
					subarea = (String) f.getAttribute(FisheryStatArea.SUBAREA.propertyName());
					div = (String) f.getAttribute(FisheryStatArea.DIVISION.propertyName());
					if(areaStatus == 0){
						subarea = subarea.substring(1);
						div = div.substring(1);
					}
					
					fb.set(FisheryStatArea.MAJOR.propertyName(), (String) f.getAttribute(FisheryStatArea.MAJOR.propertyName()));
					fb.set(FisheryStatArea.SUBAREA.propertyName(), subarea);
					fb.set(FisheryStatArea.DIVISION.propertyName(), div);
					
				}else if(area.equals(FisheryStatArea.SUBUNIT)){
					subarea = (String) f.getAttribute(FisheryStatArea.SUBAREA.propertyName());
					div = (String) f.getAttribute(FisheryStatArea.DIVISION.propertyName());
					subdiv = (String) f.getAttribute(FisheryStatArea.SUBDIVISION.propertyName());
					if(areaStatus == 0){
						subarea = subarea.substring(1);
						div = div.substring(1);
						subdiv = subdiv.substring(1);
					}
					
					fb.set(FisheryStatArea.MAJOR.propertyName(), (String) f.getAttribute(FisheryStatArea.MAJOR.propertyName()));
					fb.set(FisheryStatArea.SUBAREA.propertyName(), subarea);
					fb.set(FisheryStatArea.DIVISION.propertyName(), div);
					fb.set(FisheryStatArea.SUBDIVISION.propertyName(), subdiv);
				}
				
				
				fb.set(area.propertyName(), areaCode); //add current area code
				
				//other attributes
				fb.set("F_LEVEL", area.levelName()); //add the FAO area level
				fb.set("F_CODE", areaCode); //add the code in a single attribute for dissemination
				fb.set("OCEAN", (String) f.getAttribute("OCEAN")); //ocean
				fb.set("SUBOCEAN", (String) f.getAttribute("SUBOCEAN")); //subocean
				fb.set("F_STATUS", areaStatus); // add status
				
			}
			
			//dissolve the Geometry
			while(it.hasNext()){
					
				Geometry geom = (Geometry) it.next().getDefaultGeometryProperty().getValue();
					
				if(unionGeom !=null & geom !=null){//control in case of null unexpected geometries
					unionGeom = EnhancedPrecisionOp.union(unionGeom, geom);
				}
			}
				
		}finally{
			if(it != null){
				it.close();
			}
		}
		
		fb.set("GEOM", unionGeom); //add the geometry
		SimpleFeature result = fb.buildFeature(areaCode);
		fb.reset();
		return result;
	}
	
}