/*
 * Copyright (c) 2025 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.fed.ns3.ambassador.config.model;

import org.eclipse.mosaic.lib.geo.GeoPoint;

/**
 * {@link CNodeBProperties} extends the structure like {@link CMobileNetworkProperties}
 * with specific eNodeB data.
 */
public class CNodeBProperties {
    /**
     * The eNodeB position.
     */
    public GeoPoint nodeBPosition;

    @Override
    public String toString() {
        return ((nodeBPosition != null) ? "nodeBPos: " + nodeBPosition.toString() : "null");
    }
}
