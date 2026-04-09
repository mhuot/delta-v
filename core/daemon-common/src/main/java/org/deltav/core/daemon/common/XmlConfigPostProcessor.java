/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.core.daemon.common;

import java.io.File;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Extracts nested XML content from config files that Jackson XmlMapper cannot
 * deserialize due to limited {@code @XmlAnyElement} support.
 *
 * <p>Jackson's {@code JaxbAnnotationModule} does not support
 * {@code @XmlAnyElement(lax=false)} with {@code @XmlJavaTypeAdapter}, so
 * nested XML elements (e.g., {@code <page-sequence>} inside
 * {@code <parameter>}) are silently dropped during deserialization.
 * This class re-reads the XML file with a DOM parser to recover them.</p>
 */
public final class XmlConfigPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(XmlConfigPostProcessor.class);

    private XmlConfigPostProcessor() {}

    /**
     * Extracts nested XML content from {@code <parameter>} elements in a
     * poller-configuration.xml file.
     *
     * <p>Finds {@code <parameter>} elements that are direct children of
     * {@code <service>} and have child elements but no {@code value} attribute.
     * Serializes each child element to an XML string.</p>
     *
     * @param configFile the XML config file to parse
     * @return map of {@code "packageName:serviceName:paramKey"} to serialized
     *         XML string, empty if no nested parameters found
     */
    public static Map<String, String> extractNestedXmlParameters(File configFile) {
        Map<String, String> result = new HashMap<>();
        try {
            var dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            var doc = dbf.newDocumentBuilder().parse(configFile);
            doc.getDocumentElement().normalize();

            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            NodeList paramElements = doc.getElementsByTagName("parameter");
            for (int i = 0; i < paramElements.getLength(); i++) {
                Element param = (Element) paramElements.item(i);
                String key = param.getAttribute("key");
                String value = param.getAttribute("value");

                if (key.isEmpty() || !value.isEmpty()) {
                    continue;
                }

                // Only process <parameter> elements that are direct children of <service>
                Node parent = param.getParentNode();
                if (parent == null || !"service".equals(parent.getNodeName())) {
                    continue;
                }

                Element childElement = firstChildElement(param);
                if (childElement == null) {
                    continue;
                }

                var writer = new StringWriter();
                transformer.transform(new DOMSource(childElement), new StreamResult(writer));
                String xmlString = writer.toString();

                Element serviceEl = (Element) parent;
                Element packageEl = (Element) serviceEl.getParentNode();
                String serviceName = serviceEl.getAttribute("name");
                String packageName = packageEl.getAttribute("name");
                String lookupKey = packageName + ":" + serviceName + ":" + key;

                result.put(lookupKey, xmlString);
                LOG.info("Extracted nested XML parameter: package={}, service={}, key={} ({} chars)",
                        packageName, serviceName, key, xmlString.length());
            }
        } catch (Exception e) {
            LOG.error("Failed to extract nested XML parameters from {}", configFile, e);
        }
        return result;
    }

    private static Element firstChildElement(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return (Element) children.item(i);
            }
        }
        return null;
    }
}
