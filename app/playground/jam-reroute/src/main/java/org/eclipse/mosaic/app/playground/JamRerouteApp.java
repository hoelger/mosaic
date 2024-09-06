/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.app.playground;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class JamRerouteApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication {

    List<Double> lastDistances;
    int LAST_DISTANCES_MAXLEN = 20; // number of seconds to remember
    double JAM_THRESHOLD = 5; // moving less than JAM_THRESHOLD meter within LAST_DISTANCES_MAXLEN seconds will trigger a traffic jam alarm

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        double previous = 0;
        if (Objects.nonNull(previousVehicleData)) {
             previous = previousVehicleData.getDistanceDriven();
        }
        double delta = updatedVehicleData.getDistanceDriven() - previous;
        // getLog().infoSimTime(this, "delta distance: {}",  delta);
        lastDistances.add(delta);
        //getLog().infoSimTime(this, "dd: {} sum: {}", updatedVehicleData.getDistanceDriven(), sum);
        if (lastDistances.size() > LAST_DISTANCES_MAXLEN) {
            lastDistances.subList(0, lastDistances.size() - LAST_DISTANCES_MAXLEN).clear();
        }
        //getLog().infoSimTime(this, "lastDistances {}", lastDistances);
        double lastDistancesSum = lastDistances.stream().mapToDouble(Double::doubleValue).sum();
        getLog().infoSimTime(this, "lastDistancesSum {}", lastDistancesSum);

        if (lastDistancesSum < JAM_THRESHOLD) {
            // TRIGGER ALARM
        }
    }

    @Override
    public void processEvent(Event event) throws Exception {
    }

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Initialize application");
        this.lastDistances = new ArrayList<Double>();
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown application");
    }

}
