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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name="requisitioned_categories")
public class RequisitionedCategoryAssociation implements Serializable, Comparable<RequisitionedCategoryAssociation> {
    private static final long serialVersionUID = 1L;

    private Integer m_id;
    private OnmsNode m_node;
    private OnmsCategory m_category;

    public RequisitionedCategoryAssociation() {
    }

    public RequisitionedCategoryAssociation(final OnmsNode node, final OnmsCategory category) {
        m_node = node;
        m_category = category;
    }

    @Id
    @SequenceGenerator(name="opennmsSequence", sequenceName="opennmsNxtId", allocationSize = 1)
    @GeneratedValue(generator="opennmsSequence")
    @Column(name="id")
    public Integer getId() {
        return m_id;
    }

    public void setId(final Integer id) {
        m_id = id;
    }

    @ManyToOne
    @JoinColumn(name="nodeId", nullable=false)
    public OnmsNode getNode() {
        return m_node;
    }

    public void setNode(final OnmsNode node) {
        m_node = node;
    }

    @ManyToOne
    @JoinColumn(name="categoryId", nullable=false)
    public OnmsCategory getCategory() {
        return m_category;
    }

    public void setCategory(final OnmsCategory category) {
        m_category = category;
    }

    @Override
    public String toString() {
        return "RequisitionedCategoryAssociation [id=" + m_id + ", node=" + m_node.getId() + ", category=" + m_category.getName() + "]";
    }

    @Override
    public int compareTo(final RequisitionedCategoryAssociation o) {
        int ret = m_node.compareTo(o.m_node);
        if (ret == 0) {
            ret = m_category.compareTo(o.m_category);
        }
        return ret;
    }

}
