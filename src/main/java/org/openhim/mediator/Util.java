/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator;

import org.openhim.mediator.engine.MediatorConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

public class Util {
	
	//creating a new JAXB context is expensive, so keep static instances
	private static Map<String, JAXBContext> JAXBContextInstances = new HashMap<String, JAXBContext>();
	
	/**
	 * Split an id string into the id type and the id number
	 * @param id_str the id string to split
	 * @return an array with the first value being the id type and the second being the id number
	 */
	public static String[] splitIdentifer(String id_str) {
		int index = id_str.indexOf('-');
		String idType = id_str.substring(0, index);
		String id = id_str.substring(index + 1);
		String[] ret = new String[2];
		ret[0] = idType;
		ret[1] = id;
		return ret;
	}

	/**
	 * Marshall a JAXB object and return the XML as a string. The XML declaration will be added.
	 */
	public static String marshallJAXBObject(String namespace, Object o) throws JAXBException {
		return marshallJAXBObject(namespace, o, true);
	}
	
	/**
	 * Marshall a JAXB object and return the XML as a string
	 */
	public static String marshallJAXBObject(String namespace, Object o, boolean addXMLDeclaration) throws JAXBException {
		JAXBContext jc = getJAXBContext(namespace);
		Marshaller marshaller = jc.createMarshaller();
		if (addXMLDeclaration) {
			marshaller.setProperty("com.sun.xml.bind.xmlDeclaration", addXMLDeclaration);
		}
		StringWriter sw = new StringWriter();
		marshaller.marshal(o, sw);
		return sw.toString();
	}
	
	public static JAXBContext getJAXBContext(String namespace) throws JAXBException {
		if (!JAXBContextInstances.containsKey(namespace))
			JAXBContextInstances.put(namespace, JAXBContext.newInstance(namespace));
		return JAXBContextInstances.get(namespace);
	}

    public static String getResourceAsString(String resource) throws IOException {
        InputStream is = Util.class.getClassLoader().getResourceAsStream(resource);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String         line = null;
        StringBuilder  stringBuilder = new StringBuilder();
        String         ls = System.getProperty("line.separator");

        while((line = reader.readLine()) != null ) {
            stringBuilder.append(line);
            stringBuilder.append(ls);
        }

        return stringBuilder.toString();
    }

    public static boolean isPropertyTrue(MediatorConfig config, String key) {
        return isPropertyTrue(config, key, false);
    }

    public static boolean isPropertyTrue(MediatorConfig config, String key, boolean valueIfNotExist) {
        if (config==null || config.getProperty(key)==null) {
            return valueIfNotExist;
        }
        return config.getProperty(key).equalsIgnoreCase("true");
    }
}
