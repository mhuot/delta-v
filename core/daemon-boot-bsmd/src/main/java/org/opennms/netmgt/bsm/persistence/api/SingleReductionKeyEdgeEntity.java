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

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Size;

import com.google.common.collect.Sets;

@Entity
@Table(name = "bsm_service_reductionkeys",
        uniqueConstraints = @UniqueConstraint(columnNames = {"id", "reductionkey"}))
@PrimaryKeyJoinColumn(name="id")
@DiscriminatorValue("reductionkeys")
public class SingleReductionKeyEdgeEntity extends BusinessServiceEdgeEntity {

    private String reductionKey;
    private String m_friendlyName;

    public void setReductionKey(String reductionKey) {
        this.reductionKey = reductionKey;
    }

    @Column(name = "reductionkey", nullable = false)
    public String getReductionKey() {
        return reductionKey;
    }

    @Override
    @Transient
    public Set<String> getReductionKeys() {
        return Sets.newHashSet(reductionKey);
    }

    @Column(name="friendlyname", nullable = true)
    @Size(min = 0, max = 30)
    public String getFriendlyName() {
        return m_friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        m_friendlyName = friendlyName;
    }

    @Override
    public String toString() {
        return com.google.common.base.MoreObjects.toStringHelper(this)
                .add("super", super.toString())
                .add("reductionKey", reductionKey)
                .toString();
    }

    @Override
    public boolean equalsDefinition(BusinessServiceEdgeEntity other) {
        boolean equalsSuper = super.equalsDefinition(other);
        if (equalsSuper) {
            return Objects.equals(reductionKey, ((SingleReductionKeyEdgeEntity) other).reductionKey) &&
                   Objects.equals(m_friendlyName, ((SingleReductionKeyEdgeEntity) other).m_friendlyName);
        }
        return false;
    }

    @Override
    public <T> T accept(EdgeEntityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
