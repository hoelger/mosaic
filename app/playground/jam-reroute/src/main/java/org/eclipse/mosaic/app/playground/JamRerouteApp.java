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

import org.eclipse.mosaic.fed.application.ambassador.navigation.INavigationModule;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.SensorType;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Denm;
import org.eclipse.mosaic.lib.objects.v2x.etsi.DenmContent;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleRoute;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.routing.RoutingParameters;
import org.eclipse.mosaic.lib.routing.RoutingPosition;
import org.eclipse.mosaic.lib.routing.RoutingResponse;
import org.eclipse.mosaic.lib.routing.util.ReRouteSpecificConnectionsCostFunction;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import jdk.jfr.EventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class JamRerouteApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication, CommunicationApplication {

    private boolean startup = true;
    private List<Double> lastDistances;
    private int LAST_DISTANCES_MAXLEN = 20; // number of seconds to remember
    private double JAM_THRESHOLD = 5; // moving less than JAM_THRESHOLD meter within LAST_DISTANCES_MAXLEN seconds will trigger a traffic jam alarm
    private boolean routeChanged = false;

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Initialize application");
        this.lastDistances = new ArrayList<Double>();
        getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                .addRadio()
                .channel(AdHocChannel.CCH)
                .power(50.)
                .create());
        getLog().infoSimTime(this, "Activated AdHoc Module");
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown application");
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        if (!isValidStateAndLog()) {
            return;
        }

        if(true) return; // DO NOT detect jam via vehicle data

        double previous = 0;
        if (Objects.nonNull(previousVehicleData)) {
             previous = previousVehicleData.getDistanceDriven();
        }
        double delta = updatedVehicleData.getDistanceDriven() - previous;
        // getLog().infoSimTime(this, "delta distance: {}",  delta);
        lastDistances.add(delta);
        //getLog().infoSimTime(this, "dd: {} sum: {}", updatedVehicleData.getDistanceDriven(), sum);
        if (lastDistances.size() > LAST_DISTANCES_MAXLEN) {
            startup = false; // the list is now "full" and we can use it's values
            lastDistances.subList(0, lastDistances.size() - LAST_DISTANCES_MAXLEN).clear();
        }
        //getLog().infoSimTime(this, "lastDistances {}", lastDistances);
        double lastDistancesSum = lastDistances.stream().mapToDouble(Double::doubleValue).sum();
        getLog().infoSimTime(this, "lastDistancesSum {}", lastDistancesSum);

        if (!startup && lastDistancesSum < JAM_THRESHOLD) {
            sendJamAlarm((float)(lastDistancesSum/LAST_DISTANCES_MAXLEN)); // m/s
        }
    }

    private void sendJamAlarm(float causedSpeed) {
        // failsafe
        if (getOs().getVehicleData() == null) {
            getLog().infoSimTime(this, "No vehicleInfo given, skipping.");
            return;
        }
        if (getOs().getVehicleData().getRoadPosition() == null) {
            getLog().warnSimTime(this, "No road position given, skip this event");
            return;
        }

        // longLat of the vehicle that detected an traffic jam.
        GeoPoint vehicleLongLat = getOs().getPosition();
        // ID of the connection on which the vehicle detected an traffic jam.
        String roadId = getOs().getVehicleData().getRoadPosition().getConnection().getId();

        getLog().infoSimTime(this, "Traffic jam detected");
        getLog().debugSimTime(this, "Position: {}", vehicleLongLat);
        getLog().debugSimTime(this, "RoadId on which the event take place: {}", roadId);

        GeoCircle dest = new GeoCircle(vehicleLongLat, 3000);
        MessageRouting routing = getOs().getAdHocModule().createMessageRouting().geoBroadCast(dest);
        Denm denm = new Denm(routing,
                new DenmContent(
                        getOs().getSimulationTime(),
                        vehicleLongLat,
                        roadId,
                        SensorType.SPEED,
                        0,
                        causedSpeed,
                        0.0f,
                        vehicleLongLat,
                        null,
                        null),
                200);
        getOs().getAdHocModule().sendV2xMessage(denm);
        getLog().infoSimTime(this, "Traffic jam alarm sent.");
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        final V2xMessage msg = receivedV2xMessage.getMessage();

        // Only DEN Messages are handled
        if (!(msg instanceof Denm)) {
            getLog().infoSimTime(this, "Ignoring message of type: {}", msg.getSimpleClassName());
            return;
        }
        final Denm denm = (Denm) msg;
        // Only SPEED types are handled
        if (denm.getWarningType() != SensorType.SPEED) {
            getLog().infoSimTime(this, "Ignoring event of type: {}", denm.getWarningType());
            return;
        }

        getLog().infoSimTime(this, "Received traffic jam alert message from {}", msg.getRouting().getSource().getSourceName());

        if (routeChanged) {
            getLog().infoSimTime(this, "Route already changed");
        } else {
            final String affectedConnectionId = denm.getEventRoadId();
            final VehicleRoute routeInfo = Objects.requireNonNull(getOs().getNavigationModule().getCurrentRoute());

            for (final String connection : routeInfo.getConnectionIds()) {
                if (connection.equals(affectedConnectionId)) {
                    getLog().infoSimTime(this, "The Event is on the vehicle's route {} = {}", connection, affectedConnectionId);

                    // is this somewhere stored, or what if the circumnavigation of the second closed road then switches back to the first (closed) one.
                    circumnavigateAffectedRoad(denm, affectedConnectionId);
                    routeChanged = true;
                    return;
                }
            }
        }
    }

    private void circumnavigateAffectedRoad(Denm denm, final String affectedRoadId) {
        ReRouteSpecificConnectionsCostFunction myCostFunction = new ReRouteSpecificConnectionsCostFunction();
        myCostFunction.setConnectionSpeedMS(affectedRoadId, denm.getCausedSpeed());
        INavigationModule navigationModule = getOs().getNavigationModule();
        RoutingParameters routingParameters = new RoutingParameters().costFunction(myCostFunction);
        RoutingResponse response = navigationModule.calculateRoutes(new RoutingPosition(navigationModule.getTargetPosition()), routingParameters);
        CandidateRoute newRoute = response.getBestRoute();
        if (newRoute != null) {
            getLog().infoSimTime(this, "Sending Change Route Command at position: {}", denm.getSenderPosition());
            navigationModule.switchRoute(newRoute);
        }
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
    public void processEvent(Event event) throws Exception {

    }
}
