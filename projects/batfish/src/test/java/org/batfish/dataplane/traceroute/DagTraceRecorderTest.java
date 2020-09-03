package org.batfish.dataplane.traceroute;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Ordering.natural;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.util.List;
import java.util.SortedMap;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.TracerouteEngine;
import org.batfish.datamodel.ConcreteInterfaceAddress;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.Flow;
import org.batfish.datamodel.FlowDisposition;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.Interface.Builder;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.NetworkFactory;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.StaticRoute;
import org.batfish.datamodel.Vrf;
import org.batfish.datamodel.flow.Hop;
import org.batfish.datamodel.flow.TraceAndReverseFlow;
import org.batfish.main.Batfish;
import org.batfish.main.BatfishTestUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DagTraceRecorderTest {
  private final NetworkFactory _nf = new NetworkFactory();
  private static final Prefix ROUTE_PREFIX = Prefix.parse("254.254.254.254/32");

  SortedMap<String, Configuration> buildEcmpNetwork(int height, int width) {
    Stream<Configuration> innerNodes =
        IntStream.range(1, height + 1)
            .mapToObj(
                row ->
                    IntStream.range(1, width + 1).mapToObj(col -> buildEcmpNode(row, col, width)))
            .flatMap(Function.identity());
    return innerNodes.collect(
        ImmutableSortedMap.toImmutableSortedMap(
            natural(), Configuration::getHostname, Function.identity()));
  }

  /**
   * Has col interfaces above and below (to the adjacent layers). Each interface is on a /31.
   * "Above" subnets are 1.row.col.0/31 "Above" interfaces have odd IPs (last address in the
   * subnet). "Below" subnets are 1.row+1.col.0/31 "Below" interfaces have even IPs (first address
   * in the subnet). Use static routes for 8.8.8.8 to route traffic down.
   */
  private Configuration buildEcmpNode(int row, int col, int numCols) {
    checkArgument(row > 0 && col > 0 && col <= numCols);
    checkArgument(numCols < 128);

    Configuration c =
        _nf.configurationBuilder()
            .setHostname(String.format("row%scol%s", row, col))
            .setConfigurationFormat(ConfigurationFormat.CISCO_IOS)
            .build();

    Vrf vrf = _nf.vrfBuilder().setOwner(c).setName(Configuration.DEFAULT_VRF_NAME).build();

    // one interface (and address) per row, column, side (up/down). up=1, down=0
    // above interface
    Builder ib = _nf.interfaceBuilder().setOwner(c).setVrf(vrf);
    ib.setName("up")
        .setAddress(ConcreteInterfaceAddress.parse(String.format("1.%s.%s.1/16", row + 1, col)))
        .build();

    // below interface
    Interface down =
        ib.setName("down")
            .setAddress(ConcreteInterfaceAddress.parse(String.format("1.%s.%s.0/16", row, col)))
            .build();

    // route traffic down to each column in the row below
    vrf.setStaticRoutes(
        IntStream.range(1, numCols + 1)
            .mapToObj(
                i ->
                    StaticRoute.builder()
                        .setNextHopInterface(down.getName())
                        .setNetwork(ROUTE_PREFIX)
                        .setAdministrativeCost(1)
                        // forward to the up interface in the ith column in the row
                        .setNextHopIp(Ip.parse(String.format("1.%s.%s.1", row, i)))
                        .build())
            .collect(ImmutableSortedSet.toImmutableSortedSet(natural())));
    return c;
  }

  @Rule public TemporaryFolder _tmp = new TemporaryFolder();

  @Test
  public void testEcmpNetwork() throws IOException {
    int height = 7;
    int width = 10;
    SortedMap<String, Configuration> configs = buildEcmpNetwork(height, width);
    Batfish batfish = BatfishTestUtils.getBatfish(configs, _tmp);
    NetworkSnapshot snapshot = batfish.getSnapshot();
    batfish.computeDataPlane(snapshot);
    TracerouteEngine tr = batfish.getTracerouteEngine(snapshot);
    Flow flow =
        Flow.builder()
            .setDstIp(ROUTE_PREFIX.getStartIp())
            .setIngressNode(String.format("row%scol1", height))
            .build();
    long start = System.currentTimeMillis();
    List<TraceAndReverseFlow> traces =
        tr.computeTracesAndReverseFlows(ImmutableSet.of(flow), false).get(flow);
    long t = System.currentTimeMillis() - start;
    System.out.println("time: " + t);

    int traceHops = traces.stream().mapToInt(trace -> trace.getTrace().getHops().size()).sum();
    int traceEdges = traces.stream().mapToInt(trace -> trace.getTrace().getHops().size() - 1).sum();

    System.out.println("traces: " + traces.size());
    System.out.println("traceHops: " + traceHops);
    System.out.println("traceEdges: " + traceEdges);

    //    DagTraceRecorder recorder = new DagTraceRecorder(flow);
    //    traces.forEach(recorder::recordTrace);
    //    int nodes = recorder.countNodes();
    //    int edges = recorder.countEdges();
    //
    //    System.out.println("nodes: " + nodes);
    //    System.out.println("edges: " + edges);

    return;
  }

  private static final Flow TEST_FLOW =
      Flow.builder().setDstIp(Ip.parse("1.1.1.1")).setIngressNode("node").build();

  private static Breadcrumb breadcrumb(String node, Flow flow) {
    return new Breadcrumb(node, "vrf", null, flow);
  }

  private HopInfo acceptedHop(String node) {
    return HopInfo.successHop(
        HopTestUtils.acceptedHop(node),
        TEST_FLOW,
        FlowDisposition.ACCEPTED,
        TEST_FLOW,
        null,
        breadcrumb(node, TEST_FLOW));
  }

  private HopInfo forwardedHop(String node) {
    return forwardedHop(node, TEST_FLOW);
  }

  private HopInfo forwardedHop(String node, Flow flow) {
    return HopInfo.forwardedHop(
        HopTestUtils.forwardedHop(node), flow, breadcrumb(node, flow), null);
  }

  private HopInfo loopHop(String node) {
    return loopHop(node, TEST_FLOW);
  }

  private HopInfo loopHop(String node, Flow flow) {
    return HopInfo.loopHop(HopTestUtils.loopHop(node), flow, breadcrumb(node, flow), null);
  }

  /**
   * Test that a node on a looping path are not reused for paths that do include the breadcrumb
   * required for detecting the loop.
   *
   * <p>Setup: We have a looping path A -> B -> A
   *
   * <p>We cannot record the partial trace: C -> B
   */
  @Test
  public void testNoReuse_requiredBreadcrumb() {
    HopInfo hopA = forwardedHop("A");
    HopInfo hopB = forwardedHop("B");
    HopInfo hopALoop = loopHop("A");
    HopInfo hopC = forwardedHop("C");
    DagTraceRecorder recorder = new DagTraceRecorder(TEST_FLOW);
    assertTrue(recorder.tryRecordPartialTrace(ImmutableList.of(hopA, hopB, hopALoop)));
    assertFalse(recorder.tryRecordPartialTrace(ImmutableList.of(hopC, hopB)));
  }

  /**
   * Test nodes along a non-looping path are not reused for paths that include a breadcrumb that
   * would cause a loop to be detected.
   *
   * <p>Setup: We have a non-looping path A -> B -> C -> D
   *
   * <p>We cannot record the partial trace: C -> B
   */
  @Test
  public void testNoReuse_forbiddenBreadcrumb() {
    Flow flow = Flow.builder().setDstIp(Ip.parse("1.1.1.1")).setIngressNode("node").build();
    HopInfo hopA = forwardedHop("A");
    HopInfo hopB = forwardedHop("B");
    HopInfo hopC = forwardedHop("C");
    Hop hopD = HopTestUtils.acceptedHop("D");
    DagTraceRecorder recorder = new DagTraceRecorder(flow);
    //    assertTrue(recorder.tryRecordPartialTrace(ImmutableList.of(hopA, hopB, hopC, hopD)));
    //    assertFalse(recorder.tryRecordPartialTrace(ImmutableList.of(hopC, hopB)));
  }

  /**
   * Test nodes constructed with one flow cannot be reused for other flows.
   *
   * <p>Setup: We have a non-looping path A -> B -> C
   *
   * <p>D transforms the flow, so we cannot record the partial trace: D -> B
   */
  @Test
  public void testNoReuse_transformedFlow() {
    Flow transformedFlow = TEST_FLOW.toBuilder().setDstIp(Ip.parse("2.2.2.2")).build();
    HopInfo hopA = forwardedHop("A");
    HopInfo hopB = forwardedHop("B");
    HopInfo hopC = acceptedHop("C");
    HopInfo hopD = forwardedHop("D");
    HopInfo hopBTransformed = forwardedHop("B", transformedFlow);
    DagTraceRecorder recorder = new DagTraceRecorder(TEST_FLOW);
    assertTrue(recorder.tryRecordPartialTrace(ImmutableList.of(hopA, hopB, hopC)));
    assertFalse(recorder.tryRecordPartialTrace(ImmutableList.of(hopD, hopBTransformed)));
  }

  @Test
  public void testRecordPartialTrace() {
    HopInfo hopA = forwardedHop("A");
    HopInfo hopB = forwardedHop("B");
    HopInfo hopC = acceptedHop("C");
    HopInfo hopD = forwardedHop("D");
    DagTraceRecorder recorder = new DagTraceRecorder(TEST_FLOW);
    assertTrue(recorder.tryRecordPartialTrace(ImmutableList.of(hopA, hopB, hopC)));
    assertTrue(recorder.tryRecordPartialTrace(ImmutableList.of(hopD, hopB)));
  }
}
