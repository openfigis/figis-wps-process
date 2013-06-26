package org.fao.fi.figis.geoserver.wps.factory;

/** SpringBeanProcessFactory
 * 
 * @author eblondel, FAO
 *
 */
public class SpringBeanProcessFactory extends org.geoserver.wps.jts.SpringBeanProcessFactory{

	public SpringBeanProcessFactory(String title, String namespace,
			Class markerInterface) {
		super(title, namespace, markerInterface);

	}

}
