/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import com.graphhopper.reader.OSMNode;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.GHUtility;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class FootFlagEncoderTest
{
    private final EncodingManager encodingManager = new EncodingManager("CAR,BIKE,FOOT");
    private final FootFlagEncoder footEncoder = (FootFlagEncoder) encodingManager.getEncoder("FOOT");

    @Test
    public void testGetSpeed()
    {
        long fl = footEncoder.setProperties(10, true, true);
        assertEquals(10, footEncoder.getSpeed(fl), 1e-1);
    }

    @Test
    public void testBasics()
    {
        long fl = footEncoder.flagsDefault(true, true);
        assertEquals(FootFlagEncoder.MEAN_SPEED, footEncoder.getSpeed(fl), 1e-1);

        long fl1 = footEncoder.flagsDefault(true, false);
        long fl2 = footEncoder.reverseFlags(fl1);
        assertEquals(footEncoder.getSpeed(fl2), footEncoder.getSpeed(fl1), 1e-1);
    }

    @Test
    public void testCombined()
    {
        FlagEncoder carEncoder = encodingManager.getEncoder("CAR");
        long fl = footEncoder.setProperties(10, true, true) | carEncoder.setProperties(100, true, false);
        assertEquals(10, footEncoder.getSpeed(fl), 1e-1);
        assertTrue(footEncoder.isForward(fl));
        assertTrue(footEncoder.isBackward(fl));

        assertEquals(100, carEncoder.getSpeed(fl), 1e-1);
        assertTrue(carEncoder.isForward(fl));
        assertFalse(carEncoder.isBackward(fl));

        assertEquals(0, carEncoder.getSpeed(footEncoder.setProperties(10, true, true)), 1e-1);
    }

    @Test
    public void testGraph()
    {
        Graph g = new GraphBuilder(encodingManager).create();
        g.edge(0, 1).setDistance(10).setFlags(footEncoder.setProperties(10, true, true));
        g.edge(0, 2).setDistance(10).setFlags(footEncoder.setProperties(5, true, true));
        g.edge(1, 3).setDistance(10).setFlags(footEncoder.setProperties(10, true, true));
        EdgeExplorer out = g.createEdgeExplorer(new DefaultEdgeFilter(footEncoder, false, true));
        assertEquals(GHUtility.asSet(1, 2), GHUtility.getNeighbors(out.setBaseNode(0)));
        assertEquals(GHUtility.asSet(0, 3), GHUtility.getNeighbors(out.setBaseNode(1)));
        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(out.setBaseNode(2)));
    }

    @Test
    public void testAccess()
    {
        OSMWay way = new OSMWay(1);

        way.setTag("highway", "motorway");
        way.setTag("sidewalk", "yes");
        assertTrue(footEncoder.acceptWay(way) > 0);
        way.setTag("sidewalk", "left");
        assertTrue(footEncoder.acceptWay(way) > 0);

        way.setTag("sidewalk", "none");
        assertFalse(footEncoder.acceptWay(way) > 0);
        way.clearTags();

        way.setTag("highway", "pedestrian");
        assertTrue(footEncoder.acceptWay(way) > 0);

        way.setTag("highway", "footway");
        assertTrue(footEncoder.acceptWay(way) > 0);

        way.setTag("highway", "motorway");
        assertFalse(footEncoder.acceptWay(way) > 0);

        way.setTag("highway", "path");
        assertTrue(footEncoder.acceptWay(way) > 0);

        way.setTag("bicycle", "official");
        assertTrue(footEncoder.acceptWay(way) > 0);
        way.setTag("foot", "no");
        assertFalse(footEncoder.acceptWay(way) > 0);

        way.setTag("foot", "official");
        assertTrue(footEncoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        assertFalse(footEncoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("motorroad", "yes");
        assertFalse(footEncoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "cycleway");
        assertTrue(footEncoder.acceptWay(way) > 0);
        way.setTag("foot", "no");
        assertFalse(footEncoder.acceptWay(way) > 0);
        way.setTag("access", "yes");
        assertFalse(footEncoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("foot", "yes");
        way.setTag("access", "no");
        assertTrue(footEncoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("ford", "yes");
        assertFalse(footEncoder.acceptWay(way) > 0);
        way.setTag("foot", "yes");
        assertTrue(footEncoder.acceptWay(way) > 0);

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(footEncoder.acceptWay(way) > 0);
        way.setTag("foot", "no");
        assertFalse(footEncoder.acceptWay(way) > 0);
    }

    @Test
    public void testRailPlatformIssue366()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("railway", "platform");
        long flags = footEncoder.handleWayTags(way, footEncoder.acceptWay(way), 0);
        assertNotEquals(0, flags);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        flags = footEncoder.handleWayTags(way, footEncoder.acceptWay(way), 0);
        assertNotEquals(0, flags);

        way.clearTags();
        // only tram, no highway => no access
        way.setTag("railway", "tram");
        flags = footEncoder.handleWayTags(way, footEncoder.acceptWay(way), 0);
        assertEquals(0, flags);
    }

    @Test
    public void testMixSpeedAndSafe()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "motorway");
        long flags = footEncoder.handleWayTags(way, footEncoder.acceptWay(way), 0);
        assertEquals(0, flags);

        way.setTag("sidewalk", "yes");
        flags = footEncoder.handleWayTags(way, footEncoder.acceptWay(way), 0);
        assertEquals(5, footEncoder.getSpeed(flags), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        flags = footEncoder.handleWayTags(way, footEncoder.acceptWay(way), 0);
        assertEquals(5, footEncoder.getSpeed(flags), 1e-1);
    }

    @Test
    public void testPriority()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "cycleway");
        assertEquals(PriorityCode.UNCHANGED.getValue(), footEncoder.handlePriority(way, 0));

        way.setTag("highway", "primary");
        assertEquals(PriorityCode.REACH_DEST.getValue(), footEncoder.handlePriority(way, 0));

        way.setTag("highway", "track");
        way.setTag("bicycle", "official");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, 0));

        way.setTag("highway", "track");
        way.setTag("bicycle", "designated");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, 0));

        way.setTag("highway", "cycleway");
        way.setTag("bicycle", "designated");
        way.setTag("foot", "designated");
        assertEquals(PriorityCode.PREFER.getValue(), footEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("sidewalk", "yes");
        assertEquals(PriorityCode.REACH_DEST.getValue(), footEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.UNCHANGED.getValue(), footEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("bicycle", "official");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, 0));
        
        way.clearTags();
        way.setTag("highway", "residential");        
        way.setTag("sidewalk", "yes");
        assertEquals(PriorityCode.PREFER.getValue(), footEncoder.handlePriority(way, 0));                
    }

    @Test
    public void testSlowHiking()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "track");
        way.setTag("sac_scale", "hiking");
        long flags = footEncoder.handleWayTags(way, footEncoder.acceptWay(way), 0);
        assertEquals(FootFlagEncoder.MEAN_SPEED, footEncoder.getSpeed(flags), 1e-1);

        way.setTag("highway", "track");
        way.setTag("sac_scale", "mountain_hiking");
        flags = footEncoder.handleWayTags(way, footEncoder.acceptWay(way), 0);
        assertEquals(FootFlagEncoder.SLOW_SPEED, footEncoder.getSpeed(flags), 1e-1);
    }

    @Test
    public void testTurnFlagEncoding_noCostsAndRestrictions()
    {
        long flags_r0 = footEncoder.getTurnFlags(true, 0);
        long flags_0 = footEncoder.getTurnFlags(false, 0);

        long flags_r20 = footEncoder.getTurnFlags(true, 20);
        long flags_20 = footEncoder.getTurnFlags(false, 20);

        assertEquals(0, footEncoder.getTurnCost(flags_r0), 1e-1);
        assertEquals(0, footEncoder.getTurnCost(flags_0), 1e-1);

        assertEquals(0, footEncoder.getTurnCost(flags_r20), 1e-1);
        assertEquals(0, footEncoder.getTurnCost(flags_20), 1e-1);

        assertFalse(footEncoder.isTurnRestricted(flags_r0));
        assertFalse(footEncoder.isTurnRestricted(flags_0));

        assertFalse(footEncoder.isTurnRestricted(flags_r20));
        assertFalse(footEncoder.isTurnRestricted(flags_20));
    }

    @Test
    public void testBarrierAccess()
    {
        // by default allow access through the gate for bike & foot!
        OSMNode node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "gate");
        // no barrier!
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "yes");
        // no barrier!
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        // barrier!
        assertTrue(footEncoder.handleNodeTags(node) > 0);

        node.setTag("bicycle", "yes");
        // no barrier!?
        // assertTrue(footEncoder.handleNodeTags(node) == 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("foot", "yes");
        // no barrier!
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        node.setTag("locked", "yes");
        // barrier!
        assertTrue(footEncoder.handleNodeTags(node) > 0);
    }

    @Test
    public void handleWayTagsRoundabout()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("junction", "roundabout");
        way.setTag("highway", "tertiary");
        long flags = footEncoder.handleWayTags(way, footEncoder.acceptWay(way), 0);
        assertTrue(footEncoder.isBool(flags, FlagEncoder.K_ROUNDABOUT));
    }

    public void testFord()
    {
        // by default deny access through fords!
        OSMNode node = new OSMNode(1, -1, -1);
        node.setTag("ford", "no");
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("ford", "yes");
        assertTrue(footEncoder.handleNodeTags(node) > 0);

        // Now let's allow fords for foot
        footEncoder.setBlockFords(Boolean.FALSE);

        node = new OSMNode(1, -1, -1);
        node.setTag("ford", "no");
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        node = new OSMNode(1, -1, -1);
        node.setTag("ford", "yes");
        assertTrue(footEncoder.handleNodeTags(node) == 0);
    }
}
