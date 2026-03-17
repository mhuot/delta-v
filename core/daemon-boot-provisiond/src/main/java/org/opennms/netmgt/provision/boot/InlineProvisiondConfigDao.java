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
package org.opennms.netmgt.provision.boot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.opennms.netmgt.config.provisiond.ProvisiondConfiguration;
import org.opennms.netmgt.config.provisiond.RequisitionDef;
import org.opennms.netmgt.dao.api.ProvisiondConfigurationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Standalone provisiond configuration DAO that reads provisiond-configuration.xml
 * from the filesystem. Supports requisition-def entries for scheduled imports.
 * Avoids needing DefaultProvisiondConfigurationDao from opennms-dao (which
 * can't be classloaded in the daemon-loader's OSGi context).
 */
public class InlineProvisiondConfigDao implements ProvisiondConfigurationDao {

    private static final Logger LOG = LoggerFactory.getLogger(InlineProvisiondConfigDao.class);

    private static final int DEFAULT_THREADS = 4;

    private static String getDefaultHome() {
        return System.getProperty("opennms.home", "/opt/deltav");
    }

    private static String getDefaultForeignSourceDir() {
        return getDefaultHome() + "/etc/foreign-sources";
    }

    private static String getDefaultRequisitionDir() {
        return getDefaultHome() + "/etc/imports";
    }

    private ProvisiondConfiguration config;

    public InlineProvisiondConfigDao() {
        config = loadConfig();
    }

    private ProvisiondConfiguration loadConfig() {
        File configFile = new File(getDefaultHome() + "/etc/provisiond-configuration.xml");
        if (configFile.exists()) {
            try {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(configFile);
                Element root = doc.getDocumentElement();

                ProvisiondConfiguration loaded = new ProvisiondConfiguration();
                String fsDir = root.getAttribute("foreign-source-dir");
                loaded.setForeignSourceDir(fsDir != null && !fsDir.isEmpty() ? fsDir : getDefaultForeignSourceDir());
                String reqDir = root.getAttribute("requistion-dir");
                loaded.setRequistionDir(reqDir != null && !reqDir.isEmpty() ? reqDir : getDefaultRequisitionDir());
                String importT = root.getAttribute("importThreads");
                loaded.setImportThreads(importT != null && !importT.isEmpty() ? Long.parseLong(importT) : DEFAULT_THREADS);
                String scanT = root.getAttribute("scanThreads");
                loaded.setScanThreads(scanT != null && !scanT.isEmpty() ? Long.parseLong(scanT) : DEFAULT_THREADS);
                String rescanT = root.getAttribute("rescanThreads");
                loaded.setRescanThreads(rescanT != null && !rescanT.isEmpty() ? Long.parseLong(rescanT) : DEFAULT_THREADS);
                String writeT = root.getAttribute("writeThreads");
                loaded.setWriteThreads(writeT != null && !writeT.isEmpty() ? Long.parseLong(writeT) : DEFAULT_THREADS);

                // Parse requisition-def elements
                NodeList defNodes = root.getElementsByTagName("requisition-def");
                List<RequisitionDef> defs = new ArrayList<>();
                for (int i = 0; i < defNodes.getLength(); i++) {
                    Element defEl = (Element) defNodes.item(i);
                    RequisitionDef def = new RequisitionDef();
                    def.setImportName(defEl.getAttribute("import-name"));
                    def.setImportUrlResource(defEl.getAttribute("import-url-resource"));
                    NodeList cronNodes = defEl.getElementsByTagName("cron-schedule");
                    if (cronNodes.getLength() > 0) {
                        def.setCronSchedule(cronNodes.item(0).getTextContent().trim());
                    }
                    defs.add(def);
                }
                loaded.setRequisitionDefs(defs);

                LOG.info("Loaded provisiond-configuration.xml with {} requisition-def(s)", defs.size());
                return loaded;
            } catch (Exception e) {
                LOG.warn("Failed to load provisiond-configuration.xml, using defaults", e);
            }
        }
        ProvisiondConfiguration defaults = new ProvisiondConfiguration();
        defaults.setForeignSourceDir(getDefaultForeignSourceDir());
        defaults.setRequistionDir(getDefaultRequisitionDir());
        defaults.setImportThreads((long) DEFAULT_THREADS);
        defaults.setScanThreads((long) DEFAULT_THREADS);
        defaults.setRescanThreads((long) DEFAULT_THREADS);
        defaults.setWriteThreads((long) DEFAULT_THREADS);
        return defaults;
    }

    @Override
    public ProvisiondConfiguration getConfig() {
        return config;
    }

    @Override
    public RequisitionDef getDef(String defName) {
        List<RequisitionDef> defs = getDefs();
        for (RequisitionDef def : defs) {
            if (def.getImportName().orElse("").equals(defName)) {
                return def;
            }
        }
        return null;
    }

    @Override
    public List<RequisitionDef> getDefs() {
        List<RequisitionDef> defs = config.getRequisitionDefs();
        return defs != null ? defs : Collections.emptyList();
    }

    @Override
    public String getForeignSourceDir() {
        return config.getForeignSourceDir();
    }

    @Override
    public String getRequisitionDir() {
        return config.getRequistionDir();
    }

    @Override
    public Integer getImportThreads() {
        return config.getImportThreads().intValue();
    }

    @Override
    public Integer getScanThreads() {
        return config.getScanThreads().intValue();
    }

    @Override
    public Integer getRescanThreads() {
        return config.getRescanThreads().intValue();
    }

    @Override
    public Integer getWriteThreads() {
        return config.getWriteThreads().intValue();
    }

    @Override
    public void reloadConfiguration() {
        config = loadConfig();
    }

    @Override
    public Map<String, Long> getRequisitionSchemeCount() {
        return Collections.emptyMap();
    }
}
