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

package org.eclipse.mosaic.app.tutorial;

import org.eclipse.mosaic.app.tutorial.message.InterVehicleMsg;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.util.scheduling.Event;

public class MessageSendingAppCell extends AbstractApplication<VehicleOperatingSystem> implements CommunicationApplication {

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Initialize application");
        getOs().getCellModule().enable();
        getLog().infoSimTime(this, "Activated Cell Module");

        getLog().infoSimTime(this, "Sending out cell message to server_0");
        getOs().getCellModule().sendV2xMessage(
            new InterVehicleMsg(
                getOs().getCellModule().createMessageRouting().destination("server_0").topological().build(),
                getOs().getPosition()
            )
        );
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown application");
    }

    @Override
    public void processEvent(Event event) throws Exception {
        getLog().infoSimTime(this, "Received event: {}", event.getResourceClassSimpleName());
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        getLog().infoSimTime(this, "Received V2X Message from {}", receivedV2xMessage.getMessage().getRouting().getSource().getSourceName());
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgedMessage) {
    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {
    }

}
