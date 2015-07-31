package org.fao.fi.figis.geoserver.wps.feature;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.fao.fi.figis.geoserver.wps.FigisProcess;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.collection.DecoratingSimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.process.ProcessException;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.gs.WrappingIterator;
import org.geotools.process.vector.UniqueProcess;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.sort.SortOrder;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;


/** A process to create the FIRMS coverage from a coverage descriptive table
 * 	(see http://193.43.36.238:9090/browse/FGI-4)
 * 
 * 
 * @author Emmanuel Blondel (FAO)
 * <emmanuel.blondel@fao.org><emmanuel.blondel1gmail.com>
 *
 */
@DescribeProcess(title="create FIRMS coverage",
				 description="create the FIRMS coverage from a non-spatial table"
				 )
public class CreateFirmsCoverage implements FigisProcess{
	
	FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

	
	@DescribeResult(name="result", description="output result")
	 public SimpleFeatureCollection execute(
			 @DescribeParameter(name="data",description="A geometry less collection to transform into a geographic coverage") SimpleFeatureCollection features,
			 @DescribeParameter(name="geoserverURL",description="the Geoserver URL where to search source GIS layers") String geoserverURL,
			 @DescribeParameter(name="layerNamespace",description="the GeoServer namespace where source GIS layers are published") String layerPrefix,
			 @DescribeParameter(name="layerRefAttribute",description="Attribute containing the layer references") String refAttribute
			){
	
		//sorting by refAttribute
		SortBy sort = ff.sort( refAttribute, SortOrder.DESCENDING);
		features = features.sort(sort);
		
		return new FirmsCoverageFeatureCollection(features, geoserverURL, layerPrefix, refAttribute, features.getSchema());
		
	}
	
	
	
	/** FirmsCoverage decorated feature collection
	 * 
	 * @author eblondel
	 *
	 */
	static class FirmsCoverageFeatureCollection extends DecoratingSimpleFeatureCollection{

		SimpleFeatureType sourceSchema;
		SimpleFeatureType targetSchema;
		
		String geoserverURL;
		String layerPrefix;
		String refAttribute;
		
	
		
		/** Constructor
		 * 
		 * @param delegate
		 * @param refAttribute
		 */
		protected FirmsCoverageFeatureCollection(SimpleFeatureCollection delegate, String geoserverURL, String layerPrefix, String refAttribute, SimpleFeatureType schema) {
			super(delegate);
			this.createSimpleFeatureType(delegate);
			
			this.sourceSchema = schema;
			this.geoserverURL = geoserverURL;
			this.layerPrefix = layerPrefix;
			this.refAttribute = refAttribute;

		}
		
		
		@Override
        public SimpleFeatureType getSchema() {
            return targetSchema;
        }		
		
        
		
		@Override
		public SimpleFeatureIterator features() {
			return new FirmsCoverageFeatureIterator(delegate.features(),
					targetSchema, geoserverURL, layerPrefix, refAttribute,
					sourceSchema, this.getLayersList());
		}

        public Iterator<SimpleFeature> iterator() {
            return new WrappingIterator(features());
        }

        public void close(Iterator<SimpleFeature> close) {
            if (close instanceof WrappingIterator) {
                ((WrappingIterator) close).close();
            }
        }    
		
        /** create SimpleFeatureBuilder
    	 * 	
    	 * @param features
    	 */
    	public void createSimpleFeatureType(SimpleFeatureCollection features){

    		// SimpleFeatureType & SimpleFeatureBuilder
    		SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
    		tb.setName("FirmsCoverage");
    		
    		// add geometry property
    		tb.setCRS(DefaultGeographicCRS.WGS84);     
    		tb.add("THE_GEOM", MultiPolygon.class); 
    		
    		//add other properties
    		tb.addAll(features.getSchema().getAttributeDescriptors());
    		
    		//add new properties
    		tb.add("assess",String.class);
    		tb.add("manage",String.class);
    		tb.add("assess_pub",String.class);
    		tb.add("manage_pub",String.class);
		        
    		this.targetSchema = tb.buildFeatureType();
    	}
    	
    	
    	/** Get the list of reference layers
    	 * 
    	 * @return
    	 */
    	public LinkedList<String> getLayersList(){
    	
    		LinkedList<String> result = new LinkedList<String>();
    		
			UniqueProcess unique = new UniqueProcess();
			SimpleFeatureCollection list = null;
			try {
				list = unique.execute(delegate, refAttribute, null);

				//build map
				SimpleFeatureIterator it2 = list.features();
				try{
					while(it2.hasNext()){
						SimpleFeature sf2 = it2.next();
						String refLayer = sf2.getAttribute("value").toString();
							result.add(refLayer); //add the reference layer name to the list
					}					
				}finally{
					if(it2 != null){
					it2.close();
					}
				}
			
			}catch(Exception e){
				throw new ProcessException("Unable to get the layer references list", e);
			}
			return result;
    	}
    	

	}
	
	
	
	/** FirmsCoverage specific iterator
	 *  to compute the FIRMS coverage in streaming fashion
	 * 
	 * @author eblondel
	 *
	 */
	static class FirmsCoverageFeatureIterator implements SimpleFeatureIterator{

		SimpleFeatureType sourceSchema;
		
		SimpleFeatureIterator delegate;
		SimpleFeatureBuilder fb;
		
		String geoserverURL;
		String layerPrefix;
		String refAttribute;
		List<String> layerList;
		
		SimpleFeature next;
		FirmsCoverageProcessor processor;
		String layerRef;
	    int iterationIndex = 0;
	    
	    
	    
		/**
		 * Constructor
		 * 
		 * @param delegate
		 * @param schema
		 * @param refAttribute
		 * @param layerList
		 */
		public FirmsCoverageFeatureIterator(SimpleFeatureIterator delegate,
				SimpleFeatureType targetSchema, String geoserverURL,
				String layerPrefix, String refAttribute,
				SimpleFeatureType sourceSchema, LinkedList<String> layerList) {
			this.delegate = delegate;
			this.fb = new SimpleFeatureBuilder(targetSchema);

			this.sourceSchema = sourceSchema;
			this.geoserverURL = geoserverURL;
			this.layerPrefix = layerPrefix;
			this.refAttribute = refAttribute;
			this.layerList = layerList;

			this.layerRef = this.layerList.get(0);
			this.processor = new FirmsCoverageProcessor(geoserverURL, layerPrefix, layerRef, sourceSchema);

		}
		
		public boolean hasNext() {
			

            while (next == null && delegate.hasNext()) {

            	SimpleFeature sf = delegate.next();
                
            	//look if we should still rely on the same processor, if not change to get the appropriate FeatureSource
            	String layer = (String) sf.getAttribute(refAttribute);
            	if(!layer.equals(this.layerRef)){
            		layerRef = layer;
            		this.processor = new FirmsCoverageProcessor(geoserverURL, layerPrefix, layer, sourceSchema);
            	}
                
                next = this.createFirmsCoverageFeature(sf);
                fb.reset();
                iterationIndex++;
            }
            
            return next != null;
		}

		
		public SimpleFeature next() throws NoSuchElementException {
			  if (!hasNext()) {
	                throw new NoSuchElementException("hasNext() returned false!");
	            }

	            SimpleFeature result = next;
	            next = null;
	            return result;
		}

		
		public void close() {
			delegate.close();
			
		}
		
		
		/** Create the FIRMS coverage feature from the initial geometry-less data feature
		 * 
		 * @param feature
		 * @return
		 */
		public SimpleFeature createFirmsCoverageFeature(SimpleFeature feature){
			
			Geometry geom = processor.computeFirmsCoverageGeometry(feature);
			SimpleFeature result = null;
			if(geom !=null){
				fb.add(geom);
				fb.addAll(feature.getAttributes());
				computeThematicCoverage(feature);
				
				String ID = feature.getAttribute("cd_rowid").toString();
				result = fb.buildFeature(ID);
				fb.reset();
			}
			
			return result;
		}
		
		
		
		/** Method to compute the Thematic coverage, i.e.
		 *  the boolean fields ASSESS , MANAGE, ASSESS_PUB, MANAGE_PUB
		 * 
		 * @param feature
		 */
		public void computeThematicCoverage(SimpleFeature feature){
			
			//count values
			 Integer mrCount = Integer.parseInt((String) feature.getAttribute("mr_count"));
			 Integer fCount = Integer.parseInt((String) feature.getAttribute("f_count")); 

			 //validation & publication status (should be Boolean)
			 String mrValid = (String) feature.getAttribute("mr_valid");
			 String fValid = (String) feature.getAttribute("f_valid");
			 String mrPub = (String) feature.getAttribute("mr_pub");
			 String fPub = (String) feature.getAttribute("f_pub");
			 
			 //thematic count values
			 Integer fish_res = Integer.parseInt((String) feature.getAttribute("fish_res"));
			 Integer fish_act = Integer.parseInt((String) feature.getAttribute("fish_act"));
			 Integer prod_sys = Integer.parseInt((String) feature.getAttribute("prod_sys"));
			 Integer man_unit = Integer.parseInt((String) feature.getAttribute("man_unit"));
			 Integer juris = Integer.parseInt((String) feature.getAttribute("juris"));
			 
			 //Assessment
			 if((mrCount > 0 && mrValid.equals("Y")) || (fCount > 0 && fValid.equals("Y") && fish_res > 0)){
				 fb.set("assess", "Y");
			 }else{
				 fb.set("assess", "N");
			 }
			 
			 //Management
			 if(fCount > 0 && fValid.equals("Y") && (fish_act > 0 || prod_sys > 0 || man_unit > 0 || juris > 0)){
				 fb.set("manage", "Y");
			 }else{
				 fb.set("manage", "N");
			 }
			 
			 //Assessment - Published
			 if((mrCount > 0 && mrValid.equals("Y") && mrPub.equals("Y")) || (fCount > 0 && fValid.equals("Y") && fPub.equals("Y") && fish_res > 0)){
				 fb.set("assess_pub", "Y");
			 }else{
				 fb.set("assess_pub", "N");
			 }
			 
			//Management - Published
			 if(fCount > 0 && fValid.equals("Y") && fPub.equals("Y") && (fish_act > 0 || prod_sys > 0 || man_unit > 0 || juris > 0)){
				 fb.set("manage_pub", "Y");
			 }else{
				 fb.set("manage_pub", "N");
			 }

		}
		
		
	}
	
	
}
		