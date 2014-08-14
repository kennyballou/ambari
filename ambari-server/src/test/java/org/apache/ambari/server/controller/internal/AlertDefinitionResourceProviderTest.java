/**
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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ambari.server.controller.internal;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.resetToStrict;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.ambari.server.controller.AmbariManagementController;
import org.apache.ambari.server.controller.spi.Predicate;
import org.apache.ambari.server.controller.spi.Request;
import org.apache.ambari.server.controller.spi.Resource;
import org.apache.ambari.server.controller.utilities.PredicateBuilder;
import org.apache.ambari.server.controller.utilities.PropertyHelper;
import org.apache.ambari.server.orm.dao.AlertDefinitionDAO;
import org.apache.ambari.server.orm.entities.AlertDefinitionEntity;
import org.apache.ambari.server.state.Cluster;
import org.apache.ambari.server.state.Clusters;
import org.apache.ambari.server.state.alert.AlertDefinitionHash;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * AlertDefinition tests
 */
public class AlertDefinitionResourceProviderTest {

  AlertDefinitionDAO dao = null;
  AlertDefinitionHash definitionHash = null;

  private static String DEFINITION_UUID = UUID.randomUUID().toString();

  @Before
  public void before() {
    dao = createStrictMock(AlertDefinitionDAO.class);
    definitionHash = createNiceMock(AlertDefinitionHash.class);

    AlertDefinitionResourceProvider.init(dao, definitionHash);
  }

  /**
   * @throws Exception
   */
  @Test
  public void testGetResourcesNoPredicate() throws Exception {
    AlertDefinitionResourceProvider provider = createProvider(null);

    Request request = PropertyHelper.getReadRequest("AlertDefinition/cluster_name",
        "AlertDefinition/id");

    Set<Resource> results = provider.getResources(request, null);

    assertEquals(0, results.size());
  }

  /**
   * @throws Exception
   */
  @Test
  public void testGetResourcesClusterPredicate() throws Exception {
    Request request = PropertyHelper.getReadRequest(
        AlertDefinitionResourceProvider.ALERT_DEF_CLUSTER_NAME,
        AlertDefinitionResourceProvider.ALERT_DEF_ID,
        AlertDefinitionResourceProvider.ALERT_DEF_NAME);

    AmbariManagementController amc = createMock(AmbariManagementController.class);
    Clusters clusters = createMock(Clusters.class);
    Cluster cluster = createMock(Cluster.class);
    expect(amc.getClusters()).andReturn(clusters).atLeastOnce();
    expect(clusters.getCluster((String) anyObject())).andReturn(cluster).atLeastOnce();
    expect(cluster.getClusterId()).andReturn(Long.valueOf(1)).anyTimes();

    Predicate predicate = new PredicateBuilder().property(
        AlertDefinitionResourceProvider.ALERT_DEF_CLUSTER_NAME).equals("c1").toPredicate();

    expect(dao.findAll(1L)).andReturn(getMockEntities());

    replay(amc, clusters, cluster, dao);

    AlertDefinitionResourceProvider provider = createProvider(amc);
    Set<Resource> results = provider.getResources(request, predicate);

    assertEquals(1, results.size());

    Resource r = results.iterator().next();

    Assert.assertEquals("my_def", r.getPropertyValue(AlertDefinitionResourceProvider.ALERT_DEF_NAME));

    verify(amc, clusters, cluster, dao);
  }

  /**
   * @throws Exception
   */
  @Test
  public void testGetSingleResource() throws Exception {
    Request request = PropertyHelper.getReadRequest(
        AlertDefinitionResourceProvider.ALERT_DEF_CLUSTER_NAME,
        AlertDefinitionResourceProvider.ALERT_DEF_ID,
        AlertDefinitionResourceProvider.ALERT_DEF_NAME,
        AlertDefinitionResourceProvider.ALERT_DEF_SOURCE_TYPE);

    AmbariManagementController amc = createMock(AmbariManagementController.class);
    Clusters clusters = createMock(Clusters.class);
    Cluster cluster = createMock(Cluster.class);
    expect(amc.getClusters()).andReturn(clusters).atLeastOnce();
    expect(clusters.getCluster((String) anyObject())).andReturn(cluster).atLeastOnce();
    expect(cluster.getClusterId()).andReturn(Long.valueOf(1)).anyTimes();

    Predicate predicate = new PredicateBuilder().property(
        AlertDefinitionResourceProvider.ALERT_DEF_CLUSTER_NAME).equals("c1")
          .and().property(AlertDefinitionResourceProvider.ALERT_DEF_ID).equals("1").toPredicate();

    expect(dao.findById(1L)).andReturn(getMockEntities().get(0));

    replay(amc, clusters, cluster, dao);

    AlertDefinitionResourceProvider provider = createProvider(amc);
    Set<Resource> results = provider.getResources(request, predicate);

    assertEquals(1, results.size());

    Resource r = results.iterator().next();

    Assert.assertEquals("my_def", r.getPropertyValue(AlertDefinitionResourceProvider.ALERT_DEF_NAME));
    Assert.assertEquals("metric", r.getPropertyValue(AlertDefinitionResourceProvider.ALERT_DEF_SOURCE_TYPE));
    Assert.assertNotNull(r.getPropertyValue("AlertDefinition/source/type"));
  }

  /**
   * @throws Exception
   */
  @Test
  public void testCreateResources() throws Exception {
    AmbariManagementController amc = createMock(AmbariManagementController.class);
    Clusters clusters = createMock(Clusters.class);
    Cluster cluster = createMock(Cluster.class);
    expect(amc.getClusters()).andReturn(clusters).atLeastOnce();
    expect(clusters.getCluster((String) anyObject())).andReturn(cluster).atLeastOnce();
    expect(cluster.getClusterId()).andReturn(Long.valueOf(1)).anyTimes();

    Capture<AlertDefinitionEntity> entityCapture = new Capture<AlertDefinitionEntity>();
    dao.create(capture(entityCapture));
    expectLastCall();

    // creating a single definition should invalidate hosts of the definition
    definitionHash.invalidateHosts(EasyMock.anyObject(AlertDefinitionEntity.class));
    expectLastCall().once();

    replay(amc, clusters, cluster, dao, definitionHash);

    AlertDefinitionResourceProvider provider = createProvider(amc);

    Map<String, Object> requestProps = new HashMap<String, Object>();
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_CLUSTER_NAME, "c1");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_INTERVAL, "1");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_NAME, "my_def");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_SERVICE_NAME, "HDFS");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_SOURCE_TYPE, "METRIC");

    Request request = PropertyHelper.getCreateRequest(Collections.singleton(requestProps), null);

    provider.createResources(request);

    Assert.assertTrue(entityCapture.hasCaptured());
    AlertDefinitionEntity entity = entityCapture.getValue();
    Assert.assertNotNull(entity);

    Assert.assertEquals(Long.valueOf(1), entity.getClusterId());
    Assert.assertNull(entity.getComponentName());
    Assert.assertEquals("my_def", entity.getDefinitionName());
    Assert.assertTrue(entity.getEnabled());
    Assert.assertNotNull(entity.getHash());
    Assert.assertEquals(Integer.valueOf(1), entity.getScheduleInterval());
    Assert.assertNull(entity.getScope());
    Assert.assertEquals("HDFS", entity.getServiceName());
    Assert.assertNotNull(entity.getSource());
    Assert.assertEquals("METRIC", entity.getSourceType());

    verify(amc, clusters, cluster, dao);

  }

  /**
   * @throws Exception
   */
  @Test
  public void testUpdateResources() throws Exception {
    AmbariManagementController amc = createMock(AmbariManagementController.class);
    Clusters clusters = createMock(Clusters.class);
    Cluster cluster = createMock(Cluster.class);
    expect(amc.getClusters()).andReturn(clusters).atLeastOnce();
    expect(clusters.getCluster((String) anyObject())).andReturn(cluster).atLeastOnce();
    expect(cluster.getClusterId()).andReturn(Long.valueOf(1)).anyTimes();

    Capture<AlertDefinitionEntity> entityCapture = new Capture<AlertDefinitionEntity>();
    dao.create(capture(entityCapture));
    expectLastCall();

    // updateing a single definition should invalidate hosts of the definition
    definitionHash.invalidateHosts(EasyMock.anyObject(AlertDefinitionEntity.class));
    expectLastCall().once();

    replay(amc, clusters, cluster, dao, definitionHash);

    Map<String, Object> requestProps = new HashMap<String, Object>();
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_CLUSTER_NAME, "c1");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_INTERVAL, "1");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_NAME, "my_def");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_SERVICE_NAME, "HDFS");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_SOURCE_TYPE, "METRIC");

    Request request = PropertyHelper.getCreateRequest(Collections.singleton(requestProps), null);

    AlertDefinitionResourceProvider provider = createProvider(amc);

    provider.createResources(request);

    Assert.assertTrue(entityCapture.hasCaptured());
    AlertDefinitionEntity entity = entityCapture.getValue();
    Assert.assertNotNull(entity);

    Predicate p = new PredicateBuilder().property(
        AlertDefinitionResourceProvider.ALERT_DEF_ID).equals("1").and().property(
            AlertDefinitionResourceProvider.ALERT_DEF_CLUSTER_NAME).equals("c1").toPredicate();
    // everything is mocked, there is no DB
    entity.setDefinitionId(Long.valueOf(1));

    String oldName = entity.getDefinitionName();
    String oldHash = entity.getHash();

    resetToStrict(dao);
    expect(dao.findById(1L)).andReturn(entity).anyTimes();
    expect(dao.merge((AlertDefinitionEntity) anyObject())).andReturn(entity).anyTimes();
    replay(dao);

    requestProps = new HashMap<String, Object>();
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_CLUSTER_NAME, "c1");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_INTERVAL, "1");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_NAME, "my_def1");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_SERVICE_NAME, "HDFS");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_SOURCE_TYPE, "METRIC");
    request = PropertyHelper.getUpdateRequest(requestProps, null);

    provider.updateResources(request, p);

    Assert.assertFalse(oldHash.equals(entity.getHash()));
    Assert.assertFalse(oldName.equals(entity.getDefinitionName()));

    verify(amc, clusters, cluster, dao);
  }

  /**
   * @throws Exception
   */
  @Test
  public void testDeleteResources() throws Exception {
    AmbariManagementController amc = createMock(AmbariManagementController.class);
    Clusters clusters = createMock(Clusters.class);
    Cluster cluster = createMock(Cluster.class);
    expect(amc.getClusters()).andReturn(clusters).atLeastOnce();
    expect(clusters.getCluster((String) anyObject())).andReturn(cluster).atLeastOnce();
    expect(cluster.getClusterId()).andReturn(Long.valueOf(1)).anyTimes();

    Capture<AlertDefinitionEntity> entityCapture = new Capture<AlertDefinitionEntity>();
    dao.create(capture(entityCapture));
    expectLastCall();

    // deleting a single definition should invalidate hosts of the definition
    definitionHash.invalidateHosts(EasyMock.anyObject(AlertDefinitionEntity.class));
    expectLastCall().once();

    replay(amc, clusters, cluster, dao, definitionHash);

    AlertDefinitionResourceProvider provider = createProvider(amc);

    Map<String, Object> requestProps = new HashMap<String, Object>();
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_CLUSTER_NAME, "c1");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_INTERVAL, "1");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_NAME, "my_def");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_SERVICE_NAME, "HDFS");
    requestProps.put(AlertDefinitionResourceProvider.ALERT_DEF_SOURCE_TYPE, "METRIC");

    Request request = PropertyHelper.getCreateRequest(Collections.singleton(requestProps), null);

    provider.createResources(request);

    Assert.assertTrue(entityCapture.hasCaptured());
    AlertDefinitionEntity entity = entityCapture.getValue();
    Assert.assertNotNull(entity);

    Predicate p = new PredicateBuilder().property(
        AlertDefinitionResourceProvider.ALERT_DEF_ID).equals("1").and().property(
            AlertDefinitionResourceProvider.ALERT_DEF_CLUSTER_NAME).equals("c1").toPredicate();
    // everything is mocked, there is no DB
    entity.setDefinitionId(Long.valueOf(1));

    resetToStrict(dao);
    expect(dao.findById(1L)).andReturn(entity).anyTimes();
    dao.remove(capture(entityCapture));
    expectLastCall();
    replay(dao);

    provider.deleteResources(p);

    AlertDefinitionEntity entity1 = entityCapture.getValue();
    Assert.assertEquals(Long.valueOf(1), entity1.getDefinitionId());

    verify(amc, clusters, cluster, dao);

  }

  /**
   * @param amc
   * @return
   */
  private AlertDefinitionResourceProvider createProvider(AmbariManagementController amc) {
    return new AlertDefinitionResourceProvider(
        PropertyHelper.getPropertyIds(Resource.Type.AlertDefinition),
        PropertyHelper.getKeyPropertyIds(Resource.Type.AlertDefinition),
        amc);
  }

  /**
   * @return
   */
  private List<AlertDefinitionEntity> getMockEntities() {
    AlertDefinitionEntity entity = new AlertDefinitionEntity();
    entity.setClusterId(Long.valueOf(1L));
    entity.setComponentName(null);
    entity.setDefinitionId(Long.valueOf(1L));
    entity.setDefinitionName("my_def");
    entity.setEnabled(true);
    entity.setHash(DEFINITION_UUID);
    entity.setScheduleInterval(Integer.valueOf(2));
    entity.setServiceName(null);
    entity.setSourceType("metric");
    entity.setSource("{'jmx': 'beanName/attributeName', 'host': '{{aa:123445}}'}");

    return Arrays.asList(entity);
  }
}
