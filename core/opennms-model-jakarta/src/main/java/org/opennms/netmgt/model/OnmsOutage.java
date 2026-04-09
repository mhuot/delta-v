/*
 * Copyright (C) 1999-2024 The OpenNMS Group, Inc.
 * Copyright (C) 2026 BeaconStrategists, Inc. (Modifications)
 *
 * This file is part of OpenNMS(R) / Delta-V.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.opennms.netmgt.model;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;

import com.google.common.base.MoreObjects;


/**
 * <p>OnmsOutage class.</p>
 *
 * @hibernate.class table="outages"
 */
@Entity
@Table(name="outages")
@FilterDef(name = FilterManager.AUTH_FILTER_NAME,
    parameters = @ParamDef(name = "userGroups", type = String.class))
@Filter(name=FilterManager.AUTH_FILTER_NAME, condition="exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId join ipInterface on x.nodeid = ipInterface.nodeid join ifServices on ipInterface.id = ifServices.ipInterfaceId where ifServices.id = ifServiceId and cg.groupId in (:userGroups))")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OnmsOutage implements Serializable {

    private static final long serialVersionUID = 3846398168228820151L;

    /** identifier field */
    private Integer m_id;

    /** persistent field */
    private Date m_ifLostService;

    /** nullable persistent field */
    private Date m_ifRegainedService;

    /** denormalized event TSID and UEI for service lost event */
    private Long m_svcLostEventTsid;
    private String m_svcLostEventUei;

    /** denormalized event TSID and UEI for service regained event */
    private Long m_svcRegainedEventTsid;
    private String m_svcRegainedEventUei;

    /** persistent field */
    private OnmsMonitoredService m_monitoredService;

    /** persistent field */
    private Date m_suppressTime;

    /** persistent field */
    private String m_suppressedBy;

    /** persistent field */
    private OnmsMonitoringLocation m_perspective;

    /**
     * full constructor
     *
     * @param ifLostService a {@link java.util.Date} object.
     * @param ifRegainedService a {@link java.util.Date} object.
     * @param monitoredService a {@link org.opennms.netmgt.model.OnmsMonitoredService} object.
     * @param suppressTime a {@link java.util.Date} object.
     * @param suppressedBy a {@link java.lang.String} object.
     */
    public OnmsOutage(Date ifLostService, Date ifRegainedService, OnmsMonitoredService monitoredService, Date suppressTime, String suppressedBy) {
        m_ifLostService = ifLostService;
        m_ifRegainedService = ifRegainedService;
        m_monitoredService = monitoredService;
        m_suppressTime = suppressTime;
        m_suppressedBy = suppressedBy;
    }

    /**
     * default constructor
     */
    public OnmsOutage() {
    }

    /**
     */
    public OnmsOutage(Date ifLostService, OnmsMonitoredService monitoredService) {
        m_ifLostService = ifLostService;
        m_monitoredService = monitoredService;
    }

    public OnmsOutage(Date ifLostService, Date ifRegainedService, OnmsMonitoredService monitoredService) {
        m_ifLostService = ifLostService;
        m_ifRegainedService = ifRegainedService;
        m_monitoredService = monitoredService;
    }

    /**
     * <p>getId</p>
     *
     * @return a {@link java.lang.Integer} object.
     */
    @Id
    @Column(name="outageId", nullable=false)
    @SequenceGenerator(name="outageSequence", sequenceName="outageNxtId", allocationSize=1)
    @GeneratedValue(generator="outageSequence")
    public Integer getId() {
        return m_id;
    }

    /**
     * <p>setId</p>
     *
     * @param outageId a {@link java.lang.Integer} object.
     */
    public void setId(Integer outageId) {
        m_id = outageId;
    }

    /**
     * <p>getMonitoredService</p>
     *
     * @return a {@link org.opennms.netmgt.model.OnmsMonitoredService} object.
     */
    @ManyToOne
    @JoinColumn(name="ifserviceId")
    public OnmsMonitoredService getMonitoredService() {
        return m_monitoredService;
    }

    /**
     * <p>setMonitoredService</p>
     *
     * @param monitoredService a {@link org.opennms.netmgt.model.OnmsMonitoredService} object.
     */
    public void setMonitoredService(OnmsMonitoredService monitoredService) {
        m_monitoredService = monitoredService;
    }


    /**
     * <p>getIfLostService</p>
     *
     * @return a {@link java.util.Date} object.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="ifLostService", nullable=false)
    public Date getIfLostService() {
        return m_ifLostService;
    }

    /**
     * <p>setIfLostService</p>
     *
     * @param ifLostService a {@link java.util.Date} object.
     */
    public void setIfLostService(Date ifLostService) {
        m_ifLostService = ifLostService;
    }

    @Column(name="svc_lost_event_tsid")
    public Long getSvcLostEventTsid() {
        return m_svcLostEventTsid;
    }

    public void setSvcLostEventTsid(Long svcLostEventTsid) {
        m_svcLostEventTsid = svcLostEventTsid;
    }

    @Column(name="svc_lost_event_uei")
    public String getSvcLostEventUei() {
        return m_svcLostEventUei;
    }

    public void setSvcLostEventUei(String svcLostEventUei) {
        m_svcLostEventUei = svcLostEventUei;
    }


    /**
     * <p>getIfRegainedService</p>
     *
     * @return a {@link java.util.Date} object.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="ifRegainedService")
    public Date getIfRegainedService() {
        return m_ifRegainedService;
    }

    /**
     * <p>setIfRegainedService</p>
     *
     * @param ifRegainedService a {@link java.util.Date} object.
     */
    public void setIfRegainedService(Date ifRegainedService) {
        m_ifRegainedService = ifRegainedService;
    }

    @Column(name="svc_regained_event_tsid")
    public Long getSvcRegainedEventTsid() {
        return m_svcRegainedEventTsid;
    }

    public void setSvcRegainedEventTsid(Long svcRegainedEventTsid) {
        m_svcRegainedEventTsid = svcRegainedEventTsid;
    }

    @Column(name="svc_regained_event_uei")
    public String getSvcRegainedEventUei() {
        return m_svcRegainedEventUei;
    }

    public void setSvcRegainedEventUei(String svcRegainedEventUei) {
        m_svcRegainedEventUei = svcRegainedEventUei;
    }

    /**
     * <p>getSuppressTime</p>
     *
     * @return a {@link java.util.Date} object.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="suppressTime")
    public Date getSuppressTime(){
        return m_suppressTime;
    }

    /**
     * <p>setSuppressTime</p>
     *
     * @param timeToSuppress a {@link java.util.Date} object.
     */
    public void setSuppressTime(Date timeToSuppress){
        m_suppressTime = timeToSuppress;
    }


    /**
     * <p>getSuppressedBy</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Column(name="suppressedBy")
    public String getSuppressedBy(){
        return m_suppressedBy;
    }

    /**
     * <p>setSuppressedBy</p>
     *
     * @param suppressorMan a {@link java.lang.String} object.
     */
    public void setSuppressedBy(String suppressorMan){
        m_suppressedBy = suppressorMan;
    }


    /**
     * This method is necessary for CXF to be able to introspect
     * the type of {@link OnmsNode} parameters.
     *
     * @return a {@link OnmsNode} object.
     */
    @Transient
    @JsonIgnore
    public OnmsNode getNode() {
        return getMonitoredService().getIpInterface().getNode();
    }

    /**
     * This method is necessary for CXF to be able to introspect
     * the type of {@link OnmsNode} parameters.
     */
    public void setNode(OnmsNode node) {
        OnmsMonitoredService service = getMonitoredService();
        if (service == null) {
            service = new OnmsMonitoredService();
            setMonitoredService(service);
        }
        OnmsIpInterface intf = service.getIpInterface();
        if (intf == null) {
            intf = new OnmsIpInterface();
            service.setIpInterface(intf);
        }
        intf.setNode(node);
    }

    /**
     * <p>getNodeId</p>
     *
     * @return a {@link java.lang.Integer} object.
     */
    @Transient
    public Integer getNodeId(){
        return getMonitoredService().getNodeId();
    }

    /**
     * <p>getNodeLabel</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Transient
    public String getNodeLabel(){
        return getMonitoredService().getIpInterface().getNode().getLabel();
    }

    /**
     * <p>getForeignSource</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Transient
    public String getForeignSource(){
        return getMonitoredService().getIpInterface().getNode().getForeignSource();
    }

    /**
     * <p>getForeignId</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Transient
    public String getForeignId(){
        return getMonitoredService().getIpInterface().getNode().getForeignId();
    }

    /**
     * <p>getLocationName</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Transient
    public String getLocationName(){
        return getMonitoredService().getIpInterface().getNode().getLocation().getLocationName();
    }

    /**
     * <p>getIpAddress</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Transient
    public InetAddress getIpAddress() {
        return getMonitoredService().getIpAddress();
    }

    /**
     * <p>getIpAddressAsString</p>
     *
     * @return a {@link java.lang.String} object.
     * @deprecated use getIpAddress
     */
    @Transient
    @JsonIgnore
    public String getIpAddressAsString() {
        return getMonitoredService().getIpAddressAsString();
    }

    /**
     * <p>getServiceId</p>
     *
     * @return a {@link java.lang.Integer} object.
     */
    @Transient
    public Integer getServiceId() {
        return getMonitoredService().getServiceId();
    }

    /**
     * This method is necessary for CXF to be able to introspect
     * the type of {@link OnmsNode} parameters.
     *
     * @return a {@link OnmsServiceType} object.
     */
    @Transient
    @JsonIgnore
    public OnmsServiceType getServiceType() {
        return getMonitoredService().getServiceType();
    }

    /**
     * This method is necessary for CXF to be able to introspect
     * the type of {@link OnmsServiceType} parameters.
     */
    public void setServiceType(OnmsServiceType type) {
        OnmsMonitoredService service = getMonitoredService();
        if (service == null) {
            service = new OnmsMonitoredService();
            setMonitoredService(service);
        }
        service.setServiceType(type);
    }

    /**
     * Monitoring perspective that this outage is associated with.
     */
    @ManyToOne(optional=true, fetch=FetchType.LAZY)
    @JoinColumn(name="perspective")
    public OnmsMonitoringLocation getPerspective() {
        return m_perspective;
    }

    /**
     * Set the monitoring perspective for this outage.
     */
    public void setPerspective(OnmsMonitoringLocation perspective) {
        m_perspective = perspective;
    }

    /**
     * <p>toString</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("outageId", m_id)
            .add("ifLostService", m_ifLostService)
            .add("ifRegainedService", m_ifRegainedService)
            .add("svcLostEventTsid", m_svcLostEventTsid)
            .add("svcLostEventUei", m_svcLostEventUei)
            .add("svcRegainedEventTsid", m_svcRegainedEventTsid)
            .add("svcRegainedEventUei", m_svcRegainedEventUei)
            .add("service", m_monitoredService)
            .add("suppressedBy", m_suppressedBy)
            .add("suppressTime", m_suppressTime)
            .add("perspective", m_perspective)
            .toString();
    }

}
