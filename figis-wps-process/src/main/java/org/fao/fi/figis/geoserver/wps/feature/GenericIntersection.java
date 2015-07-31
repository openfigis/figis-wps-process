package org.fao.fi.figis.geoserver.wps.feature;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.log4j.Logger;
import org.fao.fi.figis.geoserver.wps.FigisProcess;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.collection.DecoratingSimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.process.ProcessException;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.gs.WrappingIterator;
import org.geotools.process.vector.ReprojectProcess;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.precision.EnhancedPrecisionOp;

/** A generic intersection process 
 * 
 * @author Emmanuel Blondel (FAO)
 * <emmanuel.blondel@fao.org><emmanuel.blondel1gmail.com>
 *
 */
@DescribeProcess(title="Generic Intersection",
				 description="Performs an generic intersection process associated with a area computation based on "
				 )
public class GenericIntersection implements FigisProcess{
	
	private static Logger logger = Logger.getLogger(GenericIntersection.class);
	
	static final String ECKERT_IV_WKT = "PROJCS[\"World_Eckert_IV\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Eckert_IV\"],PARAMETER[\"Central_Meridian\",0.0],UNIT[\"Meter\",1.0]]";

	private static String INT_OP = "_x_";
	private static String INT_PREFIX = "L";
	private static String AREA_ATT_NAME = "INT_AREA";

	
	@DescribeResult(name="result", description="output result")
	 public SimpleFeatureCollection execute(
			 @DescribeParameter(name="data 1",description="A first geometry feature collection") SimpleFeatureCollection features1,
			 @DescribeParameter(name="data 2",description="A second geometry feature collection") SimpleFeatureCollection features2
			){
		   
        //check input CRS
		CoordinateReferenceSystem inputCRS1 = features1.getSchema().getCoordinateReferenceSystem();
		CoordinateReferenceSystem inputCRS2 = features2.getSchema().getCoordinateReferenceSystem();
        if(!CRS.equalsIgnoreMetadata(inputCRS1, inputCRS2)){
        	try{
        		ReprojectProcess reproject = new ReprojectProcess();
        		features2 = reproject.execute(features2, inputCRS2, inputCRS1);
        		
        	}catch (Exception e){
            	throw new ProcessException("Input CRS of second feature collection is different. Reprojection intent failed: ", e);
            }
        }
        
        return new GenericIntersectionFeatureCollection(features1, features2);
	};
	
	
	
	
	
	/** GenericIntersectionFeatureCollection
	 * 
	 * @author eblondel
	 * <emmanuel.blondel@fao.org><emmanuel.blondel1gmail.com>
	 *
	 */
	static class GenericIntersectionFeatureCollection extends DecoratingSimpleFeatureCollection{

		
		SimpleFeatureCollection features;

		Class<?> geomBinding;
		SimpleFeatureType schema;
		String dataGeomName;

		
		/** GenericIntersection decorating feature collection
		 * 
		 * @param delegate
		 * @param features
		 */
		protected GenericIntersectionFeatureCollection(SimpleFeatureCollection delegate, SimpleFeatureCollection features) {
			super(delegate);
			this.features = features;
			this.geomBinding = this.getExpectedGeometryBinding();
			this.dataGeomName = features.getSchema().getGeometryDescriptor().getLocalName();
			
			
			// SimpleFeatureType & SimpleFeatureBuilder
    		SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();   	 		
    		
    		//add geometry attribute
    		tb.setCRS(delegate.getSchema().getCoordinateReferenceSystem());
    		tb.add(dataGeomName, geomBinding);	
            
    		//add attribute descriptors from layer 1
    		int dim = 1;
    		boolean intersection1 = isIntersection(delegate);
    		if(!intersection1){
    			tb.add(INT_PREFIX + dim + "_REF", String.class);
    		}	
    		
    		for (AttributeDescriptor att : delegate.getSchema().getAttributeDescriptors()) {        		
    			
    			if (!(att instanceof GeometryDescriptor)
    				&&
                    !att.getLocalName().equals("description") &&
                    !att.getLocalName().equals("name") &&
                    !att.getLocalName().equals("boundedBy")){
    				tb.minOccurs(att.getMinOccurs());
    				tb.maxOccurs(att.getMaxOccurs());
    				tb.restrictions(att.getType().getRestrictions());
    				
					String attName = att.getLocalName();
					if(!attName.equalsIgnoreCase(AREA_ATT_NAME)){ // handle intersection area attribute 
						if(intersection1){ //the input 1 is an intersection
							if(attName.startsWith(INT_PREFIX)){
								tb.add(attName, att.getType().getBinding());			
								int index = Integer.parseInt(attName.substring(INT_PREFIX.length(), INT_PREFIX.length()+1));
								if(dim < index){
									dim = dim+1;
								}
							}
						}else{
							tb.add(INT_PREFIX + dim + "_"+ att.getLocalName(), att.getType().getBinding());
						}
					}
                }
    		}
    		
    		//add attribute descriptors from layer 2
    		dim = dim + 1;
    		boolean intersection2 = isIntersection(features);
    		if(!intersection2){
    			tb.add(INT_PREFIX + dim + "_REF", String.class);
    		}	
    		
    		for (AttributeDescriptor att : features.getSchema().getAttributeDescriptors()) {
                if (!(att instanceof GeometryDescriptor)
                	&&
                	!att.getLocalName().equals("description") &&
                    !att.getLocalName().equals("name") &&
                    !att.getLocalName().equals("boundedBy")){
                	tb.minOccurs(att.getMinOccurs());
					tb.maxOccurs(att.getMaxOccurs());
					tb.restrictions(att.getType().getRestrictions());
					
					String attName = att.getLocalName();
					if(!attName.equalsIgnoreCase(AREA_ATT_NAME)){ // handle intersection area attribute 
						if(intersection2){ //the input 1 is an intersection
							if(attName.startsWith(INT_PREFIX)){
								tb.add(attName, att.getType().getBinding());			
								int index = Integer.parseInt(attName.substring(INT_PREFIX.length(), INT_PREFIX.length()+1));
								if(dim < index){
									dim = dim+1;
								}
							}
						}else{
							tb.add(INT_PREFIX + dim + "_"+ att.getLocalName(), att.getType().getBinding());
						}
					}
                }
    			
    		}
    		
    		tb.add(AREA_ATT_NAME, Double.class); // add attribute to handle the intersection area
    		
    		//name
    		String ftName = delegate.getSchema().getTypeName()+ INT_OP + features.getSchema().getTypeName();
    		tb.setName(ftName);
    		this.schema = tb.buildFeatureType();
		}
		
		@Override
        public SimpleFeatureType getSchema() {
            return schema;
        }

        @Override
        public SimpleFeatureIterator features() {
            return new GenericIntersectionFeatureIterator(delegate.features(), delegate, features, schema, dataGeomName);
        }

        public Iterator<SimpleFeature> iterator() {
            return new WrappingIterator(features());
        }

        public void close(Iterator<SimpleFeature> close) {
            if (close instanceof WrappingIterator) {
                ((WrappingIterator) close).close();
            }
        }
        
        
        /** A method to get the expected Geometry Binding
         * 
         * @return
         */
        public Class<?> getExpectedGeometryBinding(){
        
        	Class<?> expectedBinding = null;
        	Class<?> binding1 = delegate.getSchema().getGeometryDescriptor().getType().getBinding();
        	Class<?> binding2 = features.getSchema().getGeometryDescriptor().getType().getBinding();
        	
        	if(binding1.equals(Polygon.class) || binding1.equals(MultiPolygon.class)){
        		
        		if(binding2.equals(Polygon.class) || binding2.equals(MultiPolygon.class)){
        			expectedBinding = MultiPolygon.class;
        		}else if(binding2.equals(LineString.class) || binding2.equals(MultiLineString.class)){
        			expectedBinding = MultiLineString.class;
        		}else if(binding2.equals(Point.class)){ //here we exclude MultiPoint which is not commonly used
        			expectedBinding = Point.class;
        		}
        		
        	}else if(binding2.equals(LineString.class) || binding2.equals(MultiLineString.class)){
        		
        		if(binding2.equals(Polygon.class) || binding2.equals(MultiPolygon.class)){
        			expectedBinding = MultiLineString.class;
        		}else if(binding2.equals(LineString.class) || binding2.equals(MultiLineString.class)){
        			expectedBinding = Point.class; //what about two lines that overlap but not with the same linestring length
  
        		}else if(binding2.equals(Point.class)){
        			expectedBinding = Point.class;
        		}
        		
        	}else if(binding2.equals(Point.class)){
        		expectedBinding = Point.class;
        	}
        	
        	return expectedBinding;
        }     
        
        
        /** Identifies if the input collection is a product of the Intersection process or not
         * 
         * @return
         */
        public boolean isIntersection(SimpleFeatureCollection collection){
        	
        	boolean result = false;
        	for (AttributeDescriptor att : collection.getSchema().getAttributeDescriptors()) {
        		if(!(att instanceof GeometryDescriptor)){
        			if(att.getLocalName().startsWith(INT_PREFIX)){
            			result = true;
            		} 
        		}
        	}
    		       
    		return result;
        }
        
        
      
	}
	
	
	/** Intersection FeatureIterator - to compute the intersections in streaming fashion
	 * 
	 * @author eblondel
	 * <emmanuel.blondel@fao.org><emmanuel.blondel1gmail.com>
	 *
	 */
	static class GenericIntersectionFeatureIterator implements SimpleFeatureIterator{

		
		SimpleFeatureIterator delegate;

		SimpleFeatureCollection firstFeatures;
		
	    SimpleFeatureCollection secondFeatures;
	    
	    SimpleFeatureCollection intersectedFeatures;

	    SimpleFeatureType targetSchema;
	    
	    SimpleFeatureBuilder fb;
	     
	    Class<?> geomBinding;
	    
	    String dataGeomName;
	    
	    SimpleFeatureIterator iterator;
	    
	    boolean complete = true;
	    
	    boolean added = false;
	    
	    SimpleFeature next;
	    
	    SimpleFeature first;
	    
	    List<SimpleFeature> features = new ArrayList<SimpleFeature>();
	    
	    Integer iterationIndex = 0;
	    
	    int dim = 0;
	     
	    /** Constructor
	     * 
	     * @param delegate
	     * @param firstFeatures
	     * @param secondFeatures
	     * @param schema
	     * @param dataGeomName
	     */
		public GenericIntersectionFeatureIterator(
					SimpleFeatureIterator delegate,
					SimpleFeatureCollection firstFeatures,
					SimpleFeatureCollection secondFeatures,
					SimpleFeatureType schema,
					String dataGeomName
					){
			
			this.delegate = delegate;
			this.secondFeatures = secondFeatures;
			this.targetSchema = schema;
			this.geomBinding = targetSchema.getGeometryDescriptor().getType().getBinding();
			this.fb = new SimpleFeatureBuilder(targetSchema);
			this.dataGeomName = dataGeomName;		

		}
		
		
		public void close() {
			delegate.close();
			
		}

		 public SimpleFeature next() throws NoSuchElementException {
	            if (!hasNext()) {
	                throw new NoSuchElementException("hasNext() returned false!");
	            }

	            SimpleFeature result = next;
	            next = null;
	            return result;
	        }

		
		
		public boolean hasNext(){
			 while ((next == null && delegate.hasNext()) || (next == null && added)) {
				//business logic
				if (complete) {
					first = delegate.next();
					intersectedFeatures = null;
				} 
				
				try{

                	Geometry geom1 = (Geometry) first.getDefaultGeometryProperty().getValue();
                	if (intersectedFeatures == null && !added) {
                		intersectedFeatures = this.filteredCollection(geom1);
                        if(intersectedFeatures != null){
                			iterator = intersectedFeatures.features();
                        }else{
                        	iterator = null;
                        }
                		
                    }

                	if(iterator != null){
                		try{
                			while (iterator.hasNext()) {
                				added = false;
                				SimpleFeature second = iterator.next();
                				Geometry geom2 = (Geometry) second.getDefaultGeometryProperty().getValue();
                            
                				if (geom1.getEnvelope().intersects(geom2)) {
                            	
                					// compute geometry
                					//-----------------
                					Geometry geometry = EnhancedPrecisionOp.intersection(geom1, geom2);
                                
                					if(geometry instanceof GeometryCollection){
                						for(int i=0; i < geometry.getNumGeometries();i++){
                							Geometry extractedGeom = geometry.getGeometryN(i);
                        				
                							//if contains the expected Base Geom type - the geometry is the right one
                							if (this.geomBinding.isAssignableFrom(extractedGeom.getClass())){
                								geometry = validateGeometry(geometry);
                							}
                						}
                					}else{
                						geometry = validateGeometry(geometry);
                					}    
                                
                					//add Attributes
                					//--------------
                					
                					if (geometry != null) {
                						//add intersection geometry
                						fb.add(geometry);
                						
                						// add the non geometric attributes                             	
                						addAttributeValues(first);
                                    	addAttributeValues(second);
                                    
                                    	// calculate and add intersection area
                                    	Geometry targetGeometry = JTS.transform(geometry,
                                    							CRS.findMathTransform(this.targetSchema.getCoordinateReferenceSystem(),
                                    							CRS.parseWKT(ECKERT_IV_WKT)));
                                    	double area = targetGeometry.getArea();
                                    	fb.add(area);
                                    	
                                    	// build the feature
                                    	next = fb.buildFeature(iterationIndex.toString());
                                    	fb.reset();
                                    	
                                    	// update iterator status
                                    	if (iterator.hasNext()) {
                                    		complete = false;
                                    		added = true;
                                    		iterationIndex++;
                                    		return next != null;
                                    	}
                                    	iterationIndex++;
                					}

                				}
                				complete = false;
                			}
                			complete= true;
                		}finally{
                			if (!added) {
                				iterator.close();
                			}	
                		}
                	}else{
                		
                	}
                		
                }catch (Exception e){
                	throw new ProcessException("Failed to get intersections for" + first, e);
                }
                
				
			}
			return next != null;  
            
			
		}
		
		/** Get the sub feature collection (second input) that intersects with a geometry
		 * 
		 * @param currentGeom
		 * @return
		 */
		private SimpleFeatureCollection filteredCollection(Geometry currentGeom) {
			FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(null);
	        Filter intersectFilter = ff.intersects(ff.property(dataGeomName), ff.literal(currentGeom));
	        SimpleFeatureCollection subFeatureCollectionIntersection = this.secondFeatures.subCollection(intersectFilter);
	        return subFeatureCollectionIntersection;
		}
	
	
		/** validate a geometry
		 * 
		 * @param geometry
		 * @return
		 */
    	private Geometry validateGeometry(Geometry geometry){
    		Geometry validGeometry = null;
    		if(geometry.isValid()){
    			validGeometry = geometry;
    		}else{
    			validGeometry = EnhancedPrecisionOp.buffer(geometry, 0);
    		}	
    		return validGeometry;
    	}
    	
    	
    	/** add Attribute values to a feature
    	 * 
    	 * @param feature
    	 */
    	private void addAttributeValues(SimpleFeature feature) {
    		
    		boolean intersection = isIntersectionFeature(feature);
    		if(!intersection){
    			fb.add(feature.getFeatureType().getName().getLocalPart());
    		}
    		
    		for (AttributeDescriptor ad : feature.getFeatureType().getAttributeDescriptors()) {
                if(!(ad instanceof GeometryDescriptor)
                   &&
                   !ad.getLocalName().equals("description") &&
                   !ad.getLocalName().equals("name") &&
                   !ad.getLocalName().equals("boundedBy")){
                	
                	if(intersection){
            			if(!ad.getLocalName().equals(AREA_ATT_NAME)){
            				fb.add(feature.getAttribute(ad.getLocalName()));
            			}
            		}else{
            			fb.add(feature.getAttribute(ad.getLocalName()));
            		}	
                }
            }
        }
    
    	
    	/** 
         * 
         * @return
         */
        public boolean isIntersectionFeature(SimpleFeature feature){
        	
        	boolean result = false;
        	for (AttributeDescriptor att : feature.getFeatureType().getAttributeDescriptors()) {
        		if(!(att instanceof GeometryDescriptor)){
        			if(att.getLocalName().startsWith(INT_PREFIX)){
            			result = true;
            		} 
        		}
        	}
    		       
    		return result;
        }
    	
   
    	
	}
}

