package org.apache.helix.controller.rebalancer.waged.constraints;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Collections;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.apache.helix.controller.rebalancer.waged.model.AssignableNode;
import org.apache.helix.controller.rebalancer.waged.model.AssignableReplica;
import org.apache.helix.controller.rebalancer.waged.model.ClusterContext;
import org.apache.helix.model.Partition;
import org.apache.helix.model.ResourceAssignment;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestPartitionMovementConstraint {
  private static final String INSTANCE = "TestInstance";
  private static final String RESOURCE = "TestResource";
  private static final String PARTITION = "TestPartition";
  private AssignableNode _testNode;
  private AssignableReplica _testReplica;
  private ClusterContext _clusterContext;
  private SoftConstraint _constraint = new PartitionMovementConstraint();

  @BeforeMethod
  public void init() {
    _testNode = mock(AssignableNode.class);
    _testReplica = mock(AssignableReplica.class);
    _clusterContext = mock(ClusterContext.class);
    when(_testReplica.getResourceName()).thenReturn(RESOURCE);
    when(_testReplica.getPartitionName()).thenReturn(PARTITION);
    when(_testNode.getInstanceName()).thenReturn(INSTANCE);
  }

  @Test
  public void testGetAssignmentScoreWhenBestPossibleBaselineMissing() {
    when(_clusterContext.getBaselineAssignment()).thenReturn(Collections.emptyMap());
    when(_clusterContext.getBestPossibleAssignment()).thenReturn(Collections.emptyMap());
    double score = _constraint.getAssignmentScore(_testNode, _testReplica, _clusterContext);
    double normalizedScore =
        _constraint.getAssignmentNormalizedScore(_testNode, _testReplica, _clusterContext);
    Assert.assertEquals(score, 0.0);
    Assert.assertEquals(normalizedScore, 0.0);
  }

  @Test
  public void testGetAssignmentScoreWhenBestPossibleBaselineSame() {
    ResourceAssignment mockResourceAssignment = mock(ResourceAssignment.class);
    when(mockResourceAssignment.getReplicaMap(new Partition(PARTITION)))
        .thenReturn(ImmutableMap.of(INSTANCE, "Master"));
    Map<String, ResourceAssignment> assignmentMap =
        ImmutableMap.of(RESOURCE, mockResourceAssignment);
    when(_clusterContext.getBaselineAssignment()).thenReturn(assignmentMap);
    when(_clusterContext.getBestPossibleAssignment()).thenReturn(assignmentMap);
    // when the calculated states are both equal to the replica's current state
    when(_testReplica.getReplicaState()).thenReturn("Master");
    double score = _constraint.getAssignmentScore(_testNode, _testReplica, _clusterContext);
    double normalizedScore =
        _constraint.getAssignmentNormalizedScore(_testNode, _testReplica, _clusterContext);

    Assert.assertEquals(score, 1.0);
    Assert.assertEquals(normalizedScore, 1.0);
    // when the calculated states are both different from the replica's current state
    when(_testReplica.getReplicaState()).thenReturn("Slave");
    score = _constraint.getAssignmentScore(_testNode, _testReplica, _clusterContext);
    normalizedScore =
        _constraint.getAssignmentNormalizedScore(_testNode, _testReplica, _clusterContext);

    Assert.assertEquals(score, 0.5);
    Assert.assertEquals(normalizedScore, 0.5);
  }

  @Test
  public void testGetAssignmentScoreWhenBestPossibleBaselineOpposite() {
    String instanceNameA = INSTANCE + "A";
    String instanceNameB = INSTANCE + "B";
    String instanceNameC = INSTANCE + "C";
    AssignableNode testAssignableNode = mock(AssignableNode.class);

    ResourceAssignment bestPossibleResourceAssignment = mock(ResourceAssignment.class);
    when(bestPossibleResourceAssignment.getReplicaMap(new Partition(PARTITION)))
        .thenReturn(ImmutableMap.of(instanceNameA, "Master", instanceNameB, "Slave"));
    when(_clusterContext.getBestPossibleAssignment())
        .thenReturn(ImmutableMap.of(RESOURCE, bestPossibleResourceAssignment));
    ResourceAssignment baselineResourceAssignment = mock(ResourceAssignment.class);
    when(baselineResourceAssignment.getReplicaMap(new Partition(PARTITION)))
        .thenReturn(ImmutableMap.of(instanceNameA, "Slave", instanceNameC, "Master"));
    when(_clusterContext.getBaselineAssignment())
        .thenReturn(ImmutableMap.of(RESOURCE, baselineResourceAssignment));

    // when the replica's state matches with best possible
    when(testAssignableNode.getInstanceName()).thenReturn(instanceNameA);
    when(_testReplica.getReplicaState()).thenReturn("Master");
    double score =
        _constraint.getAssignmentScore(testAssignableNode, _testReplica, _clusterContext);
    double normalizedScore =
        _constraint.getAssignmentNormalizedScore(testAssignableNode, _testReplica, _clusterContext);
    Assert.assertEquals(score, 1.0);
    Assert.assertEquals(normalizedScore, 1.0);

    // when the replica's allocation matches with best possible
    when(testAssignableNode.getInstanceName()).thenReturn(instanceNameB);
    when(_testReplica.getReplicaState()).thenReturn("Master");
    score = _constraint.getAssignmentScore(testAssignableNode, _testReplica, _clusterContext);
    normalizedScore =
        _constraint.getAssignmentNormalizedScore(testAssignableNode, _testReplica, _clusterContext);
    Assert.assertEquals(score, 0.5);
    Assert.assertEquals(normalizedScore, 0.5);

    // when the replica's state matches with baseline only
    when(testAssignableNode.getInstanceName()).thenReturn(instanceNameC);
    when(_testReplica.getReplicaState()).thenReturn("Master");
    score = _constraint.getAssignmentScore(testAssignableNode, _testReplica, _clusterContext);
    normalizedScore =
        _constraint.getAssignmentNormalizedScore(testAssignableNode, _testReplica, _clusterContext);
    // The calculated score is lower than previous value cause the replica's state matches with
    // best possible is preferred
    Assert.assertEquals(score, 0.25);
    Assert.assertEquals(normalizedScore, 0.25);

    // when the replica's allocation matches with baseline only
    when(testAssignableNode.getInstanceName()).thenReturn(instanceNameC);
    when(_testReplica.getReplicaState()).thenReturn("Slave");
    score = _constraint.getAssignmentScore(testAssignableNode, _testReplica, _clusterContext);
    normalizedScore =
        _constraint.getAssignmentNormalizedScore(testAssignableNode, _testReplica, _clusterContext);
    // The calculated score is lower than previous value cause the replica's state matches with
    // best possible is preferred
    Assert.assertEquals(score, 0.25);
    Assert.assertEquals(normalizedScore, 0.25);
  }

  @Test
  public void testGetAssignmentScoreWhenBestPossibleMissing() {
    ResourceAssignment mockResourceAssignment = mock(ResourceAssignment.class);
    when(mockResourceAssignment.getReplicaMap(new Partition(PARTITION)))
        .thenReturn(ImmutableMap.of(INSTANCE, "Master"));
    Map<String, ResourceAssignment> assignmentMap =
        ImmutableMap.of(RESOURCE, mockResourceAssignment);
    when(_clusterContext.getBaselineAssignment()).thenReturn(assignmentMap);
    when(_clusterContext.getBestPossibleAssignment()).thenReturn(Collections.emptyMap());
    // when the calculated states are both equal to the replica's current state
    when(_testReplica.getReplicaState()).thenReturn("Master");
    double score = _constraint.getAssignmentScore(_testNode, _testReplica, _clusterContext);
    double normalizedScore =
        _constraint.getAssignmentNormalizedScore(_testNode, _testReplica, _clusterContext);

    Assert.assertEquals(score, 1.0);
    Assert.assertEquals(normalizedScore, 1.0);
    // when the calculated states are both different from the replica's current state
    when(_testReplica.getReplicaState()).thenReturn("Slave");
    score = _constraint.getAssignmentScore(_testNode, _testReplica, _clusterContext);
    normalizedScore =
        _constraint.getAssignmentNormalizedScore(_testNode, _testReplica, _clusterContext);

    Assert.assertEquals(score, 0.5);
    Assert.assertEquals(normalizedScore, 0.5);
  }
}
