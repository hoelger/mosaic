/*
 * Copyright (c) 2022 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.providers;

import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.PerceptionModel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.SpatialObjectAdapter;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.TrafficLightObject;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroupInfo;
import org.eclipse.mosaic.lib.spatial.KdTree;
import org.eclipse.mosaic.lib.spatial.SpatialTreeTraverser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link TrafficLightIndex} using a KD-Tree to store traffic lights.
 */
public class TrafficLightTree extends TrafficLightIndex {

    private final int bucketSize;

    private KdTree<TrafficLightObject> trafficLightTree;

    private SpatialTreeTraverser.InRadius<TrafficLightObject> treeTraverser;

    private boolean triggerNewTree = false;

    public TrafficLightTree(int bucketSize) {
        this.bucketSize = bucketSize;
    }

    @Override
    public List<TrafficLightObject> getTrafficLightsInRange(PerceptionModel perceptionModel) {
        if (triggerNewTree) {
            rebuildTree();
        }
        treeTraverser.setup(perceptionModel.getBoundingBox().center,
                perceptionModel.getBoundingBox().center.distanceSqrTo(perceptionModel.getBoundingBox().min)); // overestimating distance
        treeTraverser.traverse(trafficLightTree);
        return treeTraverser.getResult().stream().filter(perceptionModel::isInRange).toList();
    }

    private void rebuildTree() {
        List<TrafficLightObject> allTrafficLights = new ArrayList<>(indexedTrafficLights.values());
        trafficLightTree = new KdTree<>(new SpatialObjectAdapter<>(), allTrafficLights, bucketSize);
        treeTraverser = new SpatialTreeTraverser.InRadius<>();
        triggerNewTree = false;
    }

    @Override
    public int getNumberOfTrafficLights() {
        if (triggerNewTree) {
            rebuildTree();
        }
        return trafficLightTree.getRoot().size();
    }

    @Override
    public void addTrafficLight(TrafficLightGroup trafficLightGroup) {
        triggerNewTree = true;
        super.addTrafficLight(trafficLightGroup);
    }
}
