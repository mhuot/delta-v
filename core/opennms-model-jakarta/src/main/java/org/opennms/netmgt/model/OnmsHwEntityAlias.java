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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

@Entity
@Table(name="hwEntityAlias")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OnmsHwEntityAlias implements Serializable, Comparable<OnmsHwEntityAlias> {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = -2863137645849222221L;

    /** The id. */
    private Integer m_id;

    /** The entity Alias index. */
    private Integer m_index;

    /** The entity physical index. */
    private String m_oid;

    /** The hardware entity. */
    private OnmsHwEntity m_hwEntity;

    private Integer m_hwEntityId;

    /**
     * The Constructor.
     */
    public OnmsHwEntityAlias() {
    }

    /**
     * The Constructor.
     *
     * @param index the alias index
     * @param oid the alias oid
     */
    public OnmsHwEntityAlias(Integer index, String oid) {
        super();
        this.m_index = index;
        this.m_oid = oid;
    }

    /**
     * @return the id
     */
    @Id
    @Column(nullable=false)
    @SequenceGenerator(name="opennmsSequence", sequenceName="opennmsNxtId", allocationSize = 1)
    @GeneratedValue(generator="opennmsSequence")
    public Integer getId() {
        return m_id;
    }

    /**
     * @param id the m_id to set
     */
    public void setId(Integer id) {
        this.m_id = id;
    }

    /**
     * Gets the hardware entity.
     *
     * @return the hardware entity
     */
    @ManyToOne(optional=false, fetch=FetchType.LAZY)
    @JoinColumn(name="hwEntityId")
    public OnmsHwEntity getHwEntity() {
        return m_hwEntity;
    }

    @Transient
    public Integer getHwEntityId() {
        return m_hwEntityId;
    }

    public void setHwEntityId(Integer hwEntityId) {
        this.m_hwEntityId = hwEntityId;
    }

    /**
     * Sets the hardware entity.
     *
     * @param hwEntity the hardware entity
     */
    public void setHwEntity(OnmsHwEntity hwEntity) {
        m_hwEntity = hwEntity;
    }

    /**
     * @return the m_entAliasId
     */
    public Integer getIndex() {
        return m_index;
    }

    /**
     * @param index the index to set
     */
    public void setIndex(Integer index) {
        this.m_index = index;
    }

    /**
     * @return the m_entAliasOid
     */
    public String getOid() {
        return m_oid;
    }

    /**
     * @param oid the oid to set
     */
    public void setOid(String oid) {
        this.m_oid = oid;
    }

    @Override
    public String toString() {
        ToStringBuilder b = new ToStringBuilder(OnmsHwEntityAlias.class.getSimpleName(), ToStringStyle.SHORT_PREFIX_STYLE);
        if (m_hwEntity != null) {
            b.append("entity", m_hwEntity.getEntPhysicalIndex());
        }
        if (m_index != null) {
            b.append("idx", m_index);
        }
        if (m_oid != null) {
            b.append("oid", m_oid);
        }
        return b.toString();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj instanceof OnmsHwEntityAlias) {
            return toString().equals(obj.toString());
        }
        return false;
    }

    @Override
    public int compareTo(OnmsHwEntityAlias o) {
        if (o == null) return -1;
        return toString().compareTo(o.toString());
    }

}
