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

package org.eclipse.mosaic.fed.application.ambassador.simulation.electric;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernelRule;
import org.eclipse.mosaic.fed.application.ambassador.simulation.navigation.CentralNavigationComponent;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.CentralPerceptionComponent;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.providers.TrafficLightIndex;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.providers.TrafficLightTree;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.CartesianRectangle;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.MutableCartesianPoint;
import org.eclipse.mosaic.lib.junit.GeoProjectionRule;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLight;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightProgram;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;
import org.eclipse.mosaic.lib.util.scheduling.EventManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TrafficLightIndexTest {

    private TrafficLightIndex trafficLightIndex;

    private final EventManager eventManagerMock = mock(EventManager.class);
    private final CentralPerceptionComponent cpcMock = mock(CentralPerceptionComponent.class);
    private final CentralNavigationComponent cncMock = mock(CentralNavigationComponent.class);

    @Rule
    public SimulationKernelRule simulationKernelRule = new SimulationKernelRule(eventManagerMock, null, cncMock, cpcMock);

    @Rule
    public GeoProjectionRule projectionRule = new GeoProjectionRule(GeoPoint.latLon(52.5, 13.4));

    @Before
    public void before() {
        int BUCKET_SIZE = 20;
        trafficLightIndex = new TrafficLightTree(BUCKET_SIZE);
        when(cpcMock.getScenarioBounds())
                .thenReturn(new CartesianRectangle(new MutableCartesianPoint(100, 90, 0), new MutableCartesianPoint(310, 115, 0)));
    }

    private void addTrafficLights(CartesianPoint... positions) {
        HashMap<String, TrafficLightProgram> trafficLightProgramsMocks = new HashMap<>();
        TrafficLightProgram trafficLightProgramMock = mock(TrafficLightProgram.class);
        trafficLightProgramsMocks.put("0", trafficLightProgramMock);
        List<TrafficLight> trafficLightMocks = new ArrayList<>();
        for (CartesianPoint position : positions) {
            TrafficLight trafficLightMock = mock(TrafficLight.class);
            when(trafficLightMock.getPosition()).thenReturn(position.toGeo());
            when(trafficLightMock.getCurrentState()).thenReturn(TrafficLightState.GREEN);
            when(trafficLightMock.getIncomingLane()).thenReturn("E0_0");
            when(trafficLightMock.getOutgoingLane()).thenReturn("E1_0");
            when(trafficLightMock.getId()).thenReturn((int) position.getZ()); // on purpose
            trafficLightMocks.add(trafficLightMock);
        }
        TrafficLightGroup trafficLightGroup = new TrafficLightGroup("tls", trafficLightProgramsMocks, trafficLightMocks);
        trafficLightIndex.addTrafficLight(trafficLightGroup);
    }

    @Test
    public void testAddTrafficLight() {
        addTrafficLights(new MutableCartesianPoint(110, 100, 1));
        assertEquals(trafficLightIndex.getNumberOfTrafficLights(), 1);
    }
}
