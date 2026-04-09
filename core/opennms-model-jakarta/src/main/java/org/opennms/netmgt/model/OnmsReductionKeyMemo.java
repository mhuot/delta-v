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

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * <p>Specific memo which is attached to every alarm with a matching reduction
 * key.</p>
 *
 * @author <a href="mailto:Markus@OpenNMS.com">Markus Neumann</a>
 */
@Entity
@DiscriminatorValue(value="ReductionKeyMemo")
public class OnmsReductionKeyMemo extends OnmsMemo {

    private static final long serialVersionUID = 7472348439687562161L;

    @Column(name = "reductionkey")
    private String m_reductionKey;

    public String getReductionKey() {
        return m_reductionKey;
    }

    public void setReductionKey(String reductionKey) {
        this.m_reductionKey = reductionKey;
    }

}
