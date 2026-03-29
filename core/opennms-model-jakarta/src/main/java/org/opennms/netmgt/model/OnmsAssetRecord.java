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
package org.opennms.netmgt.model;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import com.google.common.base.MoreObjects;

/**
 * Represents the asset information for a node.
 *
 * @hibernate.class table="assets"
 */
@Entity
@Table(name = "assets")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OnmsAssetRecord implements Serializable {
    private static final long serialVersionUID = -8259333820682056097L;

    /**
     * Constant <code>AUTOENABLED="A"</code>
     */
    public static final String AUTOENABLED = "A";

    /**
     * Constant <code>SSH_CONNECTION="ssh"</code>
     */
    public static final String SSH_CONNECTION = "ssh";

    /**
     * Constant <code>TELNET_CONNECTION="telnet"</code>
     */
    public static final String TELNET_CONNECTION = "telnet";

    /**
     * Constant <code>RSH_CONNECTION="rsh"</code>
     */
    public static final String RSH_CONNECTION = "rsh";

    private Integer m_id;

    /** identifier field */
    private String m_category = "Unspecified";

    /** identifier field */
    private String m_manufacturer;

    /** identifier field */
    private String m_vendor;

    /** identifier field */
    private String m_modelNumber;

    /** identifier field */
    private String m_serialNumber;

    /** identifier field */
    private String m_description;

    /** identifier field */
    private String m_circuitId;

    /** identifier field */
    private String m_assetNumber;

    /** identifier field */
    private String m_operatingSystem;

    /** identifier field */
    private String m_rack;

    /** identifier field */
    private String m_slot;

    /** identifier field */
    private String m_port;

    /** identifier field */
    private String m_region;

    /** identifier field */
    private String m_division;

    /** identifier field */
    private String m_department;

    /** identifier field */
    private String m_building;

    /** identifier field */
    private String m_floor;

    /** identifier field */
    private String m_room;

    /** identifier field */
    private String m_vendorPhone;

    /** identifier field */
    private String m_vendorFax;

    /** identifier field */
    private String m_vendorAssetNumber;

    /** identifier field */
    private String m_username;

    /** identifier field */
    private String m_password;

    /** identifier field */
    private String m_enable;

    /** identifier field */
    private String m_connection;

    /** identifier field */
    private String m_autoenable;

    /** identifier field */
    private String m_lastModifiedBy = "";

    /** identifier field */
    private Date m_lastModifiedDate = new Date();

    /** identifier field */
    private String m_dateInstalled;

    /** identifier field */
    private String m_lease;

    /** identifier field */
    private String m_leaseExpires;

    /** identifier field */
    private String m_supportPhone;

    /** identifier field */
    private String m_maintcontract;

    /** identifier field */
    private String m_maintContractExpiration;

    /** identifier field */
    private String m_displayCategory;

    /** identifier field */
    private String m_notifyCategory;

    /** identifier field */
    private String m_pollerCategory;

    /** identifier field */
    private String m_thresholdCategory;

    /** identifier field */
    private String m_comment;

    /** identifier field */
    private String m_cpu;

    /** identifier field */
    private String m_ram;

    /** identifier field */
    private String m_storagectrl;

    /** identifier field */
    private String m_hdd1;

    /** identifier field */
    private String m_hdd2;

    /** identifier field */
    private String m_hdd3;

    /** identifier field */
    private String m_hdd4;

    /** identifier field */
    private String m_hdd5;

    /** identifier field */
    private String m_hdd6;

    /** identifier field */
    private String m_numpowersupplies;

    /** identifier field */
    private String m_inputpower;

    /** identifier field */
    private String m_additionalhardware;

    /** identifier field */
    private String m_admin;

    /** identifier field */
    private String m_snmpcommunity;

    /** identifier field */
    private String m_rackunitheight;

    /** persistent field */
    private OnmsNode m_node;

    private String m_managedObjectType;

    private String m_managedObjectInstance;

    private OnmsGeolocation m_geolocation = new OnmsGeolocation();

    /**
     * default constructor
     */
    public OnmsAssetRecord() {
    }

    /**
     * <p>getId</p>
     *
     * @return a {@link java.lang.Integer} object.
     */
    @Id
    @Column(nullable = false)
    @SequenceGenerator(name = "opennmsSequence", sequenceName = "opennmsNxtId", allocationSize = 1)
    @GeneratedValue(generator = "opennmsSequence")
    public Integer getId() {
        return m_id;
    }

    /**
     * <p>setId</p>
     *
     * @param id a {@link java.lang.Integer} object.
     */
    public void setId(final Integer id) {
        m_id = id;
    }

    /**
     * The node this asset information belongs to.
     *
     * @return a {@link org.opennms.netmgt.model.OnmsNode} object.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nodeId", unique = true)
    @JsonBackReference
    public OnmsNode getNode() {
        return m_node;
    }

    /**
     * Set the node associated with the asset record
     *
     * @param node a {@link org.opennms.netmgt.model.OnmsNode} object.
     */
    public void setNode(OnmsNode node) {
        m_node = node;
    }

    /**
     * --# category         : A broad idea of what this asset does (examples are
     * --#                    desktop, printer, server, infrastructure, etc.).
     *
     * @return a {@link java.lang.String} object.
     */
    @Column(name = "category", length = 64)
    public String getCategory() {
        return m_category;
    }

    public void setCategory(final String category) {
        m_category = category;
    }

    @Column(name = "manufacturer")
    public String getManufacturer() {
        return m_manufacturer;
    }

    public void setManufacturer(final String manufacturer) {
        m_manufacturer = manufacturer;
    }

    @Column(name = "vendor")
    public String getVendor() {
        return m_vendor;
    }

    public void setVendor(final String vendor) {
        m_vendor = vendor;
    }

    @Column(name = "modelNumber")
    public String getModelNumber() {
        return m_modelNumber;
    }

    public void setModelNumber(final String modelnumber) {
        m_modelNumber = modelnumber;
    }

    @Column(name = "serialNumber")
    public String getSerialNumber() {
        return m_serialNumber;
    }

    public void setSerialNumber(final String serialnumber) {
        m_serialNumber = serialnumber;
    }

    @Column(name = "description")
    public String getDescription() {
        return m_description;
    }

    public void setDescription(final String description) {
        m_description = description;
    }

    @Column(name = "circuitId")
    public String getCircuitId() {
        return m_circuitId;
    }

    public void setCircuitId(final String circuitid) {
        m_circuitId = circuitid;
    }

    @Column(name = "assetNumber")
    public String getAssetNumber() {
        return m_assetNumber;
    }

    public void setAssetNumber(final String assetnumber) {
        m_assetNumber = assetnumber;
    }

    @Column(name = "operatingSystem")
    public String getOperatingSystem() {
        return m_operatingSystem;
    }

    public void setOperatingSystem(final String operatingsystem) {
        m_operatingSystem = operatingsystem;
    }

    @Column(name = "rack")
    public String getRack() {
        return m_rack;
    }

    public void setRack(final String rack) {
        m_rack = rack;
    }

    @Column(name = "slot")
    public String getSlot() {
        return m_slot;
    }

    public void setSlot(final String slot) {
        m_slot = slot;
    }

    @Column(name = "port", length = 64)
    public String getPort() {
        return m_port;
    }

    public void setPort(final String port) {
        m_port = port;
    }

    @Column(name = "region")
    public String getRegion() {
        return m_region;
    }

    public void setRegion(final String region) {
        m_region = region;
    }

    @Column(name = "division")
    public String getDivision() {
        return m_division;
    }

    public void setDivision(final String division) {
        m_division = division;
    }

    @Column(name = "department")
    public String getDepartment() {
        return m_department;
    }

    public void setDepartment(final String department) {
        m_department = department;
    }

    @Embedded
    public OnmsGeolocation getGeolocation() {
        return m_geolocation;
    }

    public void setGeolocation(final OnmsGeolocation geolocation) {
        m_geolocation = geolocation;
    }

    @Column(name = "building")
    public String getBuilding() {
        return m_building;
    }

    public void setBuilding(final String building) {
        m_building = building;
    }

    @Column(name = "floor")
    public String getFloor() {
        return m_floor;
    }

    public void setFloor(final String floor) {
        m_floor = floor;
    }

    @Column(name = "room")
    public String getRoom() {
        return m_room;
    }

    public void setRoom(final String room) {
        m_room = room;
    }

    @Column(name = "vendorPhone")
    public String getVendorPhone() {
        return m_vendorPhone;
    }

    public void setVendorPhone(final String vendorphone) {
        m_vendorPhone = vendorphone;
    }

    @Column(name = "vendorFax")
    public String getVendorFax() {
        return m_vendorFax;
    }

    public void setVendorFax(final String vendorfax) {
        m_vendorFax = vendorfax;
    }

    @Column(name = "vendorAssetNumber")
    public String getVendorAssetNumber() {
        return m_vendorAssetNumber;
    }

    public void setVendorAssetNumber(final String vendorassetnumber) {
        m_vendorAssetNumber = vendorassetnumber;
    }

    @Column(name = "userLastModified", length = 20)
    public String getLastModifiedBy() {
        return m_lastModifiedBy == null? null : m_lastModifiedBy.trim();
    }

    public void setLastModifiedBy(final String userlastmodified) {
        m_lastModifiedBy = userlastmodified;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "lastModifiedDate")
    public Date getLastModifiedDate() {
        return m_lastModifiedDate;
    }

    public void setLastModifiedDate(final Date lastmodifieddate) {
        m_lastModifiedDate = lastmodifieddate;
    }

    @Column(name = "dateInstalled", length = 64)
    public String getDateInstalled() {
        return m_dateInstalled;
    }

    public void setDateInstalled(final String dateinstalled) {
        m_dateInstalled = dateinstalled;
    }

    @Column(name = "lease")
    public String getLease() {
        return m_lease;
    }

    public void setLease(final String lease) {
        m_lease = lease;
    }

    @Column(name = "leaseExpires", length = 64)
    public String getLeaseExpires() {
        return m_leaseExpires;
    }

    public void setLeaseExpires(final String leaseexpires) {
        m_leaseExpires = leaseexpires;
    }

    @Column(name = "supportPhone")
    public String getSupportPhone() {
        return m_supportPhone;
    }

    public void setSupportPhone(final String supportphone) {
        m_supportPhone = supportphone;
    }

    @Column(name = "maintcontract")
    public String getMaintcontract() {
        return m_maintcontract;
    }

    public void setMaintcontract(final String maintcontract) {
        m_maintcontract = maintcontract;
    }

    /**
     * @deprecated This field is provided for backwards compatibility with OpenNMS &lt; 1.10
     */
    @Transient
    public String getMaintContractNumber() {
        return getMaintcontract();
    }

    /**
     * @deprecated This field is provided for backwards compatibility with OpenNMS &lt; 1.10
     */
    public void setMaintContractNumber(final String maintcontract) {
        setMaintcontract(maintcontract);
    }

    @Column(name = "maintContractExpires", length = 64)
    public String getMaintContractExpiration() {
        return m_maintContractExpiration;
    }

    public void setMaintContractExpiration(final String maintcontractexpires) {
        m_maintContractExpiration = maintcontractexpires;
    }

    @Column(name = "displayCategory")
    public String getDisplayCategory() {
        return m_displayCategory;
    }

    public void setDisplayCategory(final String displaycategory) {
        m_displayCategory = displaycategory;
    }

    @Column(name = "notifyCategory")
    public String getNotifyCategory() {
        return m_notifyCategory;
    }

    public void setNotifyCategory(final String notifycategory) {
        m_notifyCategory = notifycategory;
    }

    @Column(name = "pollerCategory")
    public String getPollerCategory() {
        return m_pollerCategory;
    }

    public void setPollerCategory(final String pollercategory) {
        m_pollerCategory = pollercategory;
    }

    @Column(name = "thresholdCategory")
    public String getThresholdCategory() {
        return m_thresholdCategory;
    }

    public void setThresholdCategory(final String thresholdcategory) {
        m_thresholdCategory = thresholdcategory;
    }

    @Column(name = "comment")
    public String getComment() {
        return m_comment;
    }

    public void setComment(final String comment) {
        m_comment = comment;
    }

    @Column(name = "managedObjectType")
    public String getManagedObjectType() {
        return m_managedObjectType;
    }

    public void setManagedObjectType(final String mot) {
        m_managedObjectType = mot;
    }

    @Column(name = "managedObjectInstance")
    public String getManagedObjectInstance() {
        return m_managedObjectInstance;
    }

    public void setManagedObjectInstance(final String moi) {
        m_managedObjectInstance = moi;
    }

    @Column(name = "username")
    public String getUsername() {
        return m_username;
    }

    public void setUsername(final String username) {
        m_username = username;
    }

    @Column(name = "password")
    public String getPassword() {
        return m_password;
    }

    public void setPassword(final String password) {
        m_password = password;
    }

    @Column(name = "enable")
    public String getEnable() {
        return m_enable;
    }

    public void setEnable(final String enable) {
        m_enable = enable;
    }

    @Column(name = "connection", length = 32)
    public String getConnection() {
        return m_connection;
    }

    public void setConnection(final String connection) {
        if (TELNET_CONNECTION.equalsIgnoreCase(connection)) {
            m_connection = TELNET_CONNECTION;
        } else if (SSH_CONNECTION.equalsIgnoreCase(connection)) {
            m_connection = SSH_CONNECTION;
        } else if (RSH_CONNECTION.equalsIgnoreCase(connection)) {
            m_connection = RSH_CONNECTION;
        } else {
            m_connection = connection;
        }
    }

    @Column(name = "autoenable", length = 1)
    public String getAutoenable() {
        return m_autoenable;
    }

    public void setAutoenable(final String autoenable) {
        m_autoenable = autoenable;
    }

    @Column(name = "cpu")
    public String getCpu() {
        return m_cpu;
    }

    public void setCpu(final String cpu) {
        m_cpu = cpu;
    }

    @Column(name = "ram")
    public String getRam() {
        return m_ram;
    }

    public void setRam(final String ram) {
        m_ram = ram;
    }

    @Column(name = "snmpcommunity", length = 1)
    public String getSnmpcommunity() {
        return m_snmpcommunity;
    }

    public void setSnmpcommunity(final String snmpcommunity) {
        m_snmpcommunity = snmpcommunity;
    }

    @Column(name = "rackunitheight", length = 2)
    public String getRackunitheight() {
        return m_rackunitheight;
    }

    public void setRackunitheight(final String rackunitheight) {
        m_rackunitheight = rackunitheight;
    }

    @Column(name = "admin")
    public String getAdmin() {
        return m_admin;
    }

    public void setAdmin(final String admin) {
        m_admin = admin;
    }

    @Column(name = "additionalhardware")
    public String getAdditionalhardware() {
        return m_additionalhardware;
    }

    public void setAdditionalhardware(final String additionalhardware) {
        m_additionalhardware = additionalhardware;
    }

    @Column(name = "inputpower", length = 1)
    public String getInputpower() {
        return m_inputpower;
    }

    public void setInputpower(final String inputpower) {
        m_inputpower = inputpower;
    }

    @Column(name = "numpowersupplies", length = 1)
    public String getNumpowersupplies() {
        return m_numpowersupplies;
    }

    public void setNumpowersupplies(final String numpowersupplies) {
        m_numpowersupplies = numpowersupplies;
    }

    @Column(name = "hdd6")
    public String getHdd6() {
        return m_hdd6;
    }

    public void setHdd6(final String hdd6) {
        m_hdd6 = hdd6;
    }

    @Column(name = "hdd5")
    public String getHdd5() {
        return m_hdd5;
    }

    public void setHdd5(final String hdd5) {
        m_hdd5 = hdd5;
    }

    @Column(name = "hdd4")
    public String getHdd4() {
        return m_hdd4;
    }

    public void setHdd4(final String hdd4) {
        m_hdd4 = hdd4;
    }

    @Column(name = "hdd3")
    public String getHdd3() {
        return m_hdd3;
    }

    public void setHdd3(final String hdd3) {
        m_hdd3 = hdd3;
    }

    @Column(name = "hdd2")
    public String getHdd2() {
        return m_hdd2;
    }

    public void setHdd2(final String hdd2) {
        m_hdd2 = hdd2;
    }

    @Column(name = "hdd1")
    public String getHdd1() {
        return m_hdd1;
    }

    public void setHdd1(final String hdd1) {
        m_hdd1 = hdd1;
    }

    @Column(name = "storagectrl")
    public String getStoragectrl() {
        return m_storagectrl;
    }

    public void setStoragectrl(final String storagectrl) {
        m_storagectrl = storagectrl;
    }

    /**
     * PROXY METHOD: do not delete until {@link OnmsGeolocation} is truly a separate table, or projection mapping will fail.
     */
    @Transient
    @Deprecated
    public String getAddress1() {
        return m_geolocation == null ? null : m_geolocation.getAddress1();
    }

    @Deprecated
    public void setAddress1(final String address1) {
        if (m_geolocation != null)
            m_geolocation.setAddress1(address1);
    }

    @Transient
    @Deprecated
    public String getAddress2() {
        return m_geolocation == null ? null : m_geolocation.getAddress2();
    }

    @Deprecated
    public void setAddress2(final String address2) {
        if (m_geolocation != null)
            m_geolocation.setAddress2(address2);
    }

    @Transient
    @Deprecated
    public String getCity() {
        return m_geolocation == null ? null : m_geolocation.getCity();
    }

    @Deprecated
    public void setCity(final String city) {
        if (m_geolocation != null)
            m_geolocation.setCity(city);
    }

    @Transient
    @Deprecated
    public String getState() {
        return m_geolocation == null ? null : m_geolocation.getState();
    }

    @Deprecated
    public void setState(final String state) {
        if (m_geolocation != null)
            m_geolocation.setState(state);
    }

    @Transient
    @Deprecated
    public String getZip() {
        return m_geolocation == null ? null : m_geolocation.getZip();
    }

    @Deprecated
    public void setZip(final String zip) {
        if (m_geolocation != null)
            m_geolocation.setZip(zip);
    }

    @Transient
    @Deprecated
    public String getCountry() {
        return m_geolocation == null ? null : m_geolocation.getCountry();
    }

    @Deprecated
    public void setCountry(final String country) {
        if (m_geolocation != null)
            m_geolocation.setCountry(country);
    }

    @Transient
    @Deprecated
    public Double getLongitude() {
        return m_geolocation == null ? null : m_geolocation.getLongitude();
    }

    @Deprecated
    public void setLongitude(final Double longitude) {
        if (m_geolocation != null)
            m_geolocation.setLongitude(longitude);
    }

    @Transient
    @Deprecated
    public Double getLatitude() {
        return m_geolocation == null ? null : m_geolocation.getLatitude();
    }

    @Deprecated
    public void setLatitude(final Double latitude) {
        if (m_geolocation != null)
            m_geolocation.setLatitude(latitude);
    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
        .add("category", getCategory())
        .add("manufacturer", getManufacturer())
        .add("vendor", getVendor())
        .add("modelnumber", getModelNumber())
        .add("serialnumber", getSerialNumber())
        .add("description", getDescription())
        .add("circuitid", getCircuitId())
        .add("assetnumber", getAssetNumber())
        .add("operatingsystem", getOperatingSystem())
        .add("rack", getRack())
        .add("slot", getSlot())
        .add("port", getPort())
        .add("region", getRegion())
        .add("division", getDivision())
        .add("department", getDepartment())
        .add("address1", m_geolocation == null ? null : m_geolocation.getAddress1())
        .add("address2", m_geolocation == null ? null : m_geolocation.getAddress2())
        .add("city", m_geolocation == null ? null : m_geolocation.getCity())
        .add("state", m_geolocation == null ? null : m_geolocation.getState())
        .add("zip", m_geolocation == null ? null : m_geolocation.getZip())
        .add("country", m_geolocation == null ? null : m_geolocation.getCountry())
        .add("longitude", m_geolocation == null ? null : m_geolocation.getLongitude())
        .add("latitude", m_geolocation == null ? null : m_geolocation.getLatitude())
        .add("building", getBuilding())
        .add("floor", getFloor())
        .add("room", getRoom())
        .add("username", getUsername())
        .add("password", getPassword())
        .add("enable", getEnable())
        .add("autoenable", getAutoenable())
        .add("connection", getConnection())
        .add("vendorphone", getVendorPhone())
        .add("vendorfax", getVendorFax())
        .add("vendorassetnumber", getVendorAssetNumber())
        .add("userlastmodified", getLastModifiedBy())
        .add("lastmodifieddate", getLastModifiedDate())
        .add("dateinstalled", getDateInstalled())
        .add("lease", getLease())
        .add("leaseexpires", getLeaseExpires())
        .add("supportphone", getSupportPhone())
        .add("maintcontract", getMaintcontract())
        .add("maintcontractexpires", getMaintContractExpiration())
        .add("displaycategory", getDisplayCategory())
        .add("notifycategory", getNotifyCategory())
        .add("pollercategory", getPollerCategory())
        .add("thresholdcategory", getThresholdCategory())
        .add("comment", getComment())
        .add("cpu", getCpu())
        .add("ram", getRam())
        .add("storagectrl", getStoragectrl())
        .add("hdd1", getHdd1())
        .add("hdd2", getHdd2())
        .add("hdd3", getHdd3())
        .add("hdd4", getHdd4())
        .add("hdd5", getHdd5())
        .add("hdd6", getHdd6())
        .add("numpowersupplies", getNumpowersupplies())
        .add("inputpower", getInputpower())
        .add("additionalhardware", getAdditionalhardware())
        .add("admin", getAdmin())
        .add("snmpcommunity", getSnmpcommunity())
        .add("rackunitheight", getRackunitheight())
        .toString();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || obj.getClass() != this.getClass()) {
            throw new IllegalArgumentException("the Operation Object passed is either null or of the wrong class");
        }

        final OnmsAssetRecord cmpAsset = (OnmsAssetRecord) obj;

        final Integer newNodeId = cmpAsset.getNode().getId();
        if (newNodeId == null) {
            return false;
        }

        if (m_node.getId().equals(cmpAsset.getNode().getId())) {
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return 223 * m_node.getId().hashCode();
    }

    /**
     * Used to merge the contents of one asset record to another.  If equals implementation
     * returns false, the merge is aborted.
     *
     * @param newRecord a {@link org.opennms.netmgt.model.OnmsAssetRecord} object.
     */
    public void mergeRecord(OnmsAssetRecord newRecord) {

        if (!this.equals(newRecord)) {
            return;
        }

        OnmsGeolocation toGeolocation = this.getGeolocation();
        if (toGeolocation == null) {
            toGeolocation = new OnmsGeolocation();
            this.setGeolocation(toGeolocation);
        }
        final OnmsGeolocation fromGeolocation = newRecord.getGeolocation();

        //this works because all asset properties are strings
        //if the model dependencies ever change to not include spring, this will break
        final BeanWrapper currentBean = PropertyAccessorFactory.forBeanPropertyAccess(this);
        final BeanWrapper newBean = PropertyAccessorFactory.forBeanPropertyAccess(newRecord);
        final PropertyDescriptor[] pds = newBean.getPropertyDescriptors();

        // Don't update these properties
        final List<String> blackListedProperties = new ArrayList<>();
        blackListedProperties.add("class");
        blackListedProperties.add("city");
        blackListedProperties.add("zip");
        blackListedProperties.add("state");
        blackListedProperties.add("country");
        blackListedProperties.add("longitude");
        blackListedProperties.add("latitude");
        blackListedProperties.add("address1");
        blackListedProperties.add("address2");

        for (final PropertyDescriptor pd : pds) {
            final String propertyName = pd.getName();
            if (blackListedProperties.contains(propertyName)) {
                continue;
            }

            // This should never fail since both of these objects are of the same type
            if (newBean.getPropertyValue(propertyName) != null) {
                currentBean.setPropertyValue(propertyName, newBean.getPropertyValue(propertyName));
            }
        }

        toGeolocation.mergeGeolocation(fromGeolocation);
        setGeolocation(toGeolocation);
    }
}
