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

package org.eclipse.mosaic.rti.time;

import org.eclipse.mosaic.rti.MosaicComponentParameters;
import org.eclipse.mosaic.rti.api.ComponentProvider;
import org.eclipse.mosaic.rti.api.FederateAmbassador;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.PreemptableFederateAmbassador;
import org.eclipse.mosaic.rti.api.TimeManagement;
import org.eclipse.mosaic.rti.api.parameters.FederatePriority;
import org.eclipse.mosaic.rti.api.time.FederateEvent;

import java.util.Optional;

/**
 * This class is a sequential implementation of the <code>TimeManagement</code>
 * interface.
 */
public class SequentialTimeManagement extends AbstractTimeManagement {

    private final double realtimeBrake;

    /**
     * Creates a new instance of the sequential time management.
     *
     * @param federation          reference to the <code>ComponentFactory</code> to access simulation components
     * @param componentParameters parameters specifically for this {@link TimeManagement},
     *                            e.g., the target realtime factor for the simulation.
     */
    public SequentialTimeManagement(ComponentProvider federation, MosaicComponentParameters componentParameters) {
        super(federation, componentParameters);
        this.realtimeBrake = componentParameters.getRealTimeBreak();
    }

    /**
     * Runs the simulation sequentially.
     *
     * @throws InternalFederateException an exception inside of a joined federate occurs
     * @throws IllegalValueException     a parameter has an invalid value
     * @see TimeManagement
     */
    @Override
    public void runSimulation() throws InternalFederateException, IllegalValueException {
        federation.getMonitor().onBeginSimulation(federation.getFederationManagement(), this, 1);

        this.prepareSimulationRun();

        final PerformanceCalculator performanceCalculator = new PerformanceCalculator();
        final RealtimeSynchronisation realtimeSync = new RealtimeSynchronisation(realtimeBrake);

        long currentRealtimeNs;
        boolean success = false;
        FederateEvent event;
        FederateAmbassador ambassador;

        // privileged federate w/ preemptive execution
        long lastTimestamp = 0;
        boolean lastRunDidAbort = false;
        PreemptableFederateAmbassador privilegedAmbassador = null;

        int countHighestPriority = 0;
        for (FederateAmbassador amb : federation.getFederationManagement().getAmbassadors()) {
            if (amb.getPriority() == FederatePriority.HIGHEST) {
                countHighestPriority++;
            }
        }
        if (countHighestPriority > 1) {
            throw new InternalFederateException("Cannot have multiple priority-zero ambassadors. Priority-zero implicitly encodes preemptive execution.");
        } else if (countHighestPriority == 1) {
            Optional<FederateAmbassador> res = federation.getFederationManagement().getAmbassadors().stream().filter(amb -> amb.getPriority() == FederatePriority.HIGHEST).findFirst();
            if (res.isPresent() && res.get() instanceof PreemptableFederateAmbassador preempt) {
                privilegedAmbassador = preempt;
            } else {
                throw new InternalFederateException("The priority-zero ambassador has to implement the PreemptableFederateAmbassador interface.");
            }
        }

        while (this.time <= getEndTime()) {
            // the end time is inclusive, in order to schedule events in the last simulation time step

            // sync with real time
            if (this.time > 0) {
                realtimeSync.sync(this.time);
            }

            // read the next event
            synchronized (this.events) {
                if (this.events.isEmpty()) break;
                event = this.events.poll();
                if (event == null || event.getRequestedTime() > getEndTime()) {
                    this.time = getEndTime();
                    break;
                }
            }

            // always let run privileged federate first, then all others (yea, double execution for new-time privileged-federate events)
            if (privilegedAmbassador != null) {
                if (event.getRequestedTime() > lastTimestamp) {
                    success = privilegedAmbassador.advanceTimePreemptable(event.getRequestedTime());
                    if (success) {
                        lastTimestamp = event.getRequestedTime();
                        lastRunDidAbort = false;
                    } else {
                        if (lastRunDidAbort) {
                            throw new InternalFederateException("Discovered dead-lock: privileged federate preempts without scheduling a new event");
                        }
                        lastRunDidAbort = true;
                        this.events.add(event);
                        continue;
                    }
                }
            }

            // call ambassador associated with the scheduled event
            ambassador = federation.getFederationManagement().getAmbassador(event.getFederateId());
            if (ambassador != null) {
                federation.getMonitor().onBeginActivity(event);
                long startTime = System.currentTimeMillis();
                ambassador.advanceTime(event.getRequestedTime());
                federation.getMonitor().onEndActivity(event, System.currentTimeMillis() - startTime);
            }

            // advance global time
            this.time = event.getRequestedTime();

            currentRealtimeNs = System.nanoTime();
            final PerformanceInformation performanceInformation =
                    performanceCalculator.update(time, getEndTime(), currentRealtimeNs);
            printProgress(currentRealtimeNs, performanceInformation);
            updateWatchDog();
        }

        this.finishSimulationRun(STATUS_CODE_SUCCESS);
    }


}

