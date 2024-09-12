/*
 * Copyright (c) 2024 Fraunhofer FOKUS and others. All rights reserved.
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

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.ambassador.simulation.tmc.InductionLoop;
import org.eclipse.mosaic.fed.application.ambassador.simulation.tmc.LaneAreaDetector;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.TrafficManagementCenterApplication;
import org.eclipse.mosaic.fed.application.app.api.os.TrafficManagementCenterOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.SensorType;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Denm;
import org.eclipse.mosaic.lib.objects.v2x.etsi.DenmContent;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RoadMonitoringServer extends AbstractApplication<TrafficManagementCenterOperatingSystem> implements TrafficManagementCenterApplication, CommunicationApplication {


    // would have to group all this for solely detector_0
    private boolean startup = true;
    private List<Double> lastAvgSpeedMs;
    private int LAST_AVG_SPEED_MAXLEN = 5;
    private double JAM_THRESHOLD = 7;
    private final static GeoPoint LOCATION_DETECTOR_0 = GeoPoint.latLon(47.629681, 7.619705);
    private final static String ROAD_DETECTOR_0 = "56982684_6687013229_6687013226";

    public RoadMonitoringServer() {}

    @Override
    public void onStartup() {
        this.lastAvgSpeedMs = new ArrayList<Double>();
        getOs().getCellModule().enable();
    }

    @Override
    public void onInductionLoopUpdated(Collection<InductionLoop> updatedInductionLoops) {
        for (InductionLoop item : getOs().getInductionLoops()) {

            if (item.getAverageSpeedMs() != 0.0) {
                getLog().infoSimTime(this, "Detector '{}': average speed: {} m/s, traffic flow: {} veh/h", item.getId(), item.getAverageSpeedMs(), item.getTrafficFlowVehPerHour());
                lastAvgSpeedMs.add(item.getAverageSpeedMs());
                if (lastAvgSpeedMs.size() > LAST_AVG_SPEED_MAXLEN) {
                    startup = false; // the list is now "full" and we can use it's values
                    lastAvgSpeedMs.subList(0, lastAvgSpeedMs.size() - LAST_AVG_SPEED_MAXLEN).clear();
                }
                double lastAvgSpeedMs_AVG = lastAvgSpeedMs.stream().mapToDouble(Double::doubleValue).sum() / lastAvgSpeedMs.size();
                getLog().infoSimTime(this, "lastAvgSpeedMs_AVG {}", lastAvgSpeedMs_AVG);
                if (!startup && lastAvgSpeedMs_AVG < JAM_THRESHOLD) {
                    sendJamAlarm(1f); // m/s
                }
            }
        }
    }


    private void sendJamAlarm(float causedSpeed) {

        getLog().infoSimTime(this, "Traffic jam detected");

        GeoCircle dest = new GeoCircle(LOCATION_DETECTOR_0, 3000);
        MessageRouting routing = getOs().getCellModule().createMessageRouting().geoBroadcastBasedOnUnicast(dest);
        Denm denm = new Denm(routing,
                new DenmContent(
                        getOs().getSimulationTime(),
                        LOCATION_DETECTOR_0,
                        ROAD_DETECTOR_0,
                        SensorType.SPEED,
                        0,
                        causedSpeed,
                        0.0f,
                        LOCATION_DETECTOR_0,
                        null,
                        null),
                200);
        getOs().getCellModule().sendV2xMessage(denm);
        getLog().infoSimTime(this, "Traffic jam alarm sent.");
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
    }

        @Override
    public void onCamBuilding(CamBuilder camBuilder) {
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgedMessage) {
    }

    @Override
    public void onLaneAreaDetectorUpdated(Collection<LaneAreaDetector> updatedLaneAreaDetectors) {
        for (LaneAreaDetector item : getOs().getLaneAreaDetectors()) {
            getLog().infoSimTime(this, "Segment '{}': average speed: {} m/s, traffic density: {} veh/km", item.getId(), item.getMeanSpeed(), item.getTrafficDensity());
        }
    }

    @Override
    public void onShutdown() {
    }

    @Override
    public void processEvent(Event event) {
    }
}
