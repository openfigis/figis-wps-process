package org.fao.fi.figis.geoserver.wps.feature;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.precision.EnhancedPrecisionOp;


/** Firms Geographic Coverage Processor
 *  Queries the reference layers, and computes the FIRMS geographic coverage.
 * 
 * @author eblondel
 *
 */
public class FirmsCoverageProcessor {

	String geoserverUrl;
	String layerPrefix;
	SimpleFeatureType sourceSchema;
	String refLayer;
	SimpleFeatureSource featureSource;
	SimpleFeatureCollection collection;
	
	FilterFactory ff = CommonFactoryFinder.getFilterFactory();
	
	
	public FirmsCoverageProcessor(String gsURL, String layerPrefix, String layerRef, SimpleFeatureType sourceSchema){
		this.geoserverUrl = gsURL;
		this.layerPrefix = layerPrefix;
		this.sourceSchema = sourceSchema;
		this.refLayer = layerRef;
		initFeatureSource();
	}
	
	
	/** Init the FeatureSource to query reference layers
	 * 
	 */
	public void initFeatureSource(){
		
		String layerReference = this.getLayerReference();
		try{
			String wfsGetCap = this.geoserverUrl +"/ows?service=WFS&version=1.0.0&request=GetCapabilities";
		
			// use the WFS Datastore
			Map<String, Serializable> params = new HashMap<String, Serializable>();
			params.put(WFSDataStoreFactory.URL.key, wfsGetCap);
			params.put(WFSDataStoreFactory.TIMEOUT.key, new Integer(60000));
			params.put(WFSDataStoreFactory.FILTER_COMPLIANCE.key, new Integer(0));
		
			DataStore datastore = DataStoreFinder.getDataStore(params);
			this.featureSource = datastore.getFeatureSource(layerReference);
		
		}catch(Exception e){
			new RuntimeException("Unable to reach the feature source "+layerReference, e);
		}
	}
	
	
	/** Get the layer reference
	 *  Here the prefix is appended in the method, while it could be part of the layer name
	 *  as specified in the coverage input data.
	 * 
	 * @return
	 */
	public String getLayerReference(){
		String layerReference = layerPrefix+":"+this.refLayer;
		return layerReference;
	}
	
	
	
	/** Method to get the list of target geo-columns
	 *  This is dependent on the reference layer used to compute the FIRMS coverage.
	 *  e.g. T_FAO_EEZ = intersection between FAO areas & EEZ - 2 geocolumns:
	 *  	- farea_code
	 *  	- eez_code
	 *  
	 *  For now, the methods compares the reference layer featuretype to the initial feature type
	 *  Each attributedescriptor contained in the source feature type is added to the result
	 * 
	 * @return
	 */
	public Set<String> getRefGeoAttributes(){
		Set<String> set = new HashSet<String>();
		
		List <AttributeDescriptor> refAttributes = this.sourceSchema.getAttributeDescriptors();
		
		SimpleFeatureType target = this.getTargetFeatureType();
		for(AttributeDescriptor att : target.getAttributeDescriptors()){
			if(refAttributes.contains(att)){
				set.add(att.getLocalName());
			}
		}
		
		return set;
	}
	
	
	/** filter to apply to the target feature collection
	 * 
	 *  - The underlying created filter assumes that data geo-columns are the same as in the reference layer.
	 *  
	 * 	- For layers produced byt the Intersection Engine, each IE intersection layer of interest can be specified as
	 *    virtual SQL View, where SRCODE and TRGCODE attributes can be set with different alias, e.g. for the 
	 *    intersection FAO AREAS / EEZ, set SRCODE as farea_code, and TRGCODE as eez_code
	 * 
	 * @return a filter
	 */
	private Filter createFilter(SimpleFeature f){
			
		//Generate a CQL filter
		List<Filter> unitFilterList = new ArrayList<Filter>();
		
		for (String refGeoAtt : this.getRefGeoAttributes()){
			
			Filter unitFilter = null;
			
			//compute unit filter
			String value = (String) f.getAttribute(refGeoAtt);
			
			if(value!=null){//check if a (list of) values is found for the refGeoAtt
				
				value = value.replaceAll(" ", ""); //remove spaces
				String[] values = value.split(","); //split by ","
				
				if(values.length == 1){
					//build filter a unique value
					unitFilter = ff.equal(ff.property(refGeoAtt), ff.literal(value), true);
				
				}else{
					//build filter for a list of values
					List<Filter> list = new ArrayList<Filter>();
					for(String val : values){
						unitFilter = ff.equal(ff.property(refGeoAtt), ff.literal(val), true);
						list.add(unitFilter);
						
					}
					unitFilter = ff.or(list);
				}
			}
			
			
			//add to the list of filters
			if(unitFilter != null){
				unitFilterList.add(unitFilter);
			}
			
		
		}
		
		Filter filter = ff.and(unitFilterList);
		return filter;
	}
	
	/** Get the reference layer feature type
	 * 
	 * @return
	 */
	private SimpleFeatureType getTargetFeatureType(){
		
		SimpleFeatureType result = null;
		try{		
			result = featureSource.getSchema();
			
		}catch(Exception e){
			new RuntimeException("Unable to get the reference layer feature type", e);
		}
	
		return result;
	}
	
	
	
	/** Get the target collection.
	 *  The target collection is a here a sub collection of the reference layer specified in the FIRMS coverage data.
	 *  This set of features identifies each FIRMS data coverage datum as specified in the coverage data:
	 *  	- a reference layer name
	 *  	- a set of geo-columns
	 *  	- for each geo-column: 1 or more comma-separated values
	 * 
	 * @return a feature collection
	 * 
	 */
	private SimpleFeatureCollection getTargetFeatureCollection(SimpleFeature f){
		
		SimpleFeatureCollection result = null;
		try{
			Filter filter = this.createFilter(f);			
			result = featureSource.getFeatures(filter);			
			
			
		}catch(Exception e){
			new RuntimeException("Unable to get the target collection", e);
		}
	
		return result;
	}
	
	
	
	
	/** Compute the FIRMS coverage geometry
	 * 
	 * @return a geometry
	 */
	public Geometry computeFirmsCoverageGeometry(SimpleFeature feature){
		
		Geometry unionGeom = null;
		
		collection = this.getTargetFeatureCollection(feature);
		
		if(collection.size() > 0){
			
			SimpleFeatureIterator it = collection.features();
			try{
				
				//instantiate the unionGeom geometry
				if(it.hasNext()){
					unionGeom = (Geometry) it.next().getDefaultGeometryProperty().getValue();
				}
				
				//union process
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
		}
		
		return unionGeom;
	}
	
}
