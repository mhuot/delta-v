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
package org.opennms.netmgt.bsm.persistence.api.functions.reduce;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.google.common.base.Preconditions;

@Entity
@DiscriminatorValue("threshold")
public class ThresholdEntity extends AbstractReductionFunctionEntity {

    @Column(name="threshold", nullable=false)    
    private float m_threshold;

    public ThresholdEntity() {

    }

    public ThresholdEntity(float threshold) {
        setThreshold(threshold);
    }

    public void setThreshold(float threshold) {
        Preconditions.checkArgument(threshold > 0, "threshold must be strictly positive");
        Preconditions.checkArgument(threshold <= 1, "threshold must be less or equal to 1");
        m_threshold = threshold;
    }

    public float getThreshold() {
        return m_threshold;
    }

    @Override
    public String toString() {
        return com.google.common.base.MoreObjects.toStringHelper(this)
                .add("id", getId())
                .add("threshold", m_threshold)
                .toString();
    }

    @Override
    public <T> T accept(ReductionFunctionEntityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
