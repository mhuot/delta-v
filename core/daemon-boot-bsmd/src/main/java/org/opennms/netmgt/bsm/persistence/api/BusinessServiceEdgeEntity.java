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
package org.opennms.netmgt.bsm.persistence.api;

import java.util.Objects;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.opennms.netmgt.bsm.persistence.api.functions.map.AbstractMapFunctionEntity;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

/**
 * Base edges that includes properties common to all edge types.
 *
 * Ideally this class would be abstract, but in some cases Hibernate may
 * try to instantiate this class.
 *
 * @author jwhite
 */
@Entity
@Table(name = "bsm_service_edge")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name="type", discriminatorType= DiscriminatorType.STRING)
@DiscriminatorValue(value="")
public class BusinessServiceEdgeEntity implements EdgeEntity {

    public static final int DEFAULT_WEIGHT = 1;

    private Long m_id;

    // The Business Service reference where this edge belongs to!
    private BusinessServiceEntity m_businessService;

    private boolean m_enabled = true;

    private int m_weight = DEFAULT_WEIGHT;

    private AbstractMapFunctionEntity m_mapFunction;

    @Id
    @SequenceGenerator(name = "opennmsSequence", sequenceName = "opennmsNxtId", allocationSize = 1)
    @GeneratedValue(generator = "opennmsSequence")
    @Column(name = "id", nullable = false)
    public Long getId() {
        return m_id;
    }

    public void setId(Long id) {
        m_id = id;
    }

    @ManyToOne(optional=false)
    @JoinColumn(name="bsm_service_id")
    public BusinessServiceEntity getBusinessService() {
        return m_businessService;
    }

    public void setBusinessService(BusinessServiceEntity service) {
        m_businessService = Objects.requireNonNull(service);
    }

    @Column(name = "enabled", nullable = false)
    public boolean isEnabled() {
        return m_enabled;
    }

    public void setEnabled(boolean enabled) {
        m_enabled = enabled;
    }

    @Column(name = "weight", nullable = false)
    public int getWeight() {
        return m_weight;
    }

    @Override
    @Transient
    public Set<String> getReductionKeys() {
        return Sets.newHashSet();
    }

    public void setWeight(int weight) {
        Preconditions.checkArgument(weight > 0, "weight must be strictly positive.");
        m_weight = weight;
    }

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "bsm_map_id")
    public AbstractMapFunctionEntity getMapFunction() {
        return m_mapFunction;
    }

    public void setMapFunction(AbstractMapFunctionEntity mapFunction) {
        m_mapFunction = Objects.requireNonNull(mapFunction);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (!(obj instanceof BusinessServiceEdgeEntity)) return false;
        final BusinessServiceEdgeEntity other = (BusinessServiceEdgeEntity) obj;
        if (getId() != null) {
            return getId().equals(other.getId());
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return 0; // HACK: always return 0, as otherwise Sets etc do not work.
    }

    @Override
    public String toString() {
        return com.google.common.base.MoreObjects.toStringHelper(this)
                .add("id", m_id)
                .add("businessService", m_businessService == null ? null : m_businessService.getId())
                .add("enabled", m_enabled)
                .add("weight", m_weight)
                .add("mapFunction", m_mapFunction)
                .toString();
    }

    /**
     * Defines if the definition of the edge is equal to the given one.
     * This is quite different than the equals method of the object itself.
     *
     * @return true if equal, otherwise false
     */
    public boolean equalsDefinition(BusinessServiceEdgeEntity other) {
        if (other == null) return false;
        if (!getClass().equals(other.getClass())) return false;
        boolean equals = Objects.equals(getWeight(), other.getWeight())
                && Objects.equals(getBusinessService().getId(), other.getBusinessService().getId())
                && getMapFunction().equalsDefinition(other.getMapFunction());
        return equals;
    }

    @Override
    public <T> T accept(EdgeEntityVisitor<T> visitor) {
        // ALl sub classes MUST overwrite this properly, as this class cannot be abstract.
        // This is due to how hibernate deals with inheritance strategies.
        throw new IllegalStateException("Class '" + getClass().getName() + "' did not overwrite accept(EdgeEntityVisitor) method properly");
    }
}
