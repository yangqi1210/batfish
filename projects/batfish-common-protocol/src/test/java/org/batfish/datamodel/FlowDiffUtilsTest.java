package org.batfish.datamodel;

import static org.batfish.datamodel.transformation.TransformationStep.assignDestinationIp;
import static org.batfish.datamodel.transformation.TransformationStep.assignDestinationPort;
import static org.batfish.datamodel.transformation.TransformationStep.assignSourceIp;
import static org.batfish.datamodel.transformation.TransformationStep.assignSourcePort;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import org.batfish.datamodel.acl.AclLineMatchExprs;
import org.batfish.datamodel.transformation.Transformation;
import org.junit.Test;

public class FlowDiffUtilsTest {

  /** Based on TracerouteUtilsTest::testSessionTransformation. */
  @Test
  public void testGetTransformationStep() {
    Ip srcIp1 = Ip.parse("1.1.1.1");
    Ip dstIp1 = Ip.parse("2.2.2.2");
    int srcPort1 = 10001;
    int dstPort1 = 10002;
    Ip srcIp2 = Ip.parse("3.3.3.3");
    Ip dstIp2 = Ip.parse("4.4.4.4");
    int srcPort2 = 10003;
    int dstPort2 = 10004;

    Flow inputFlow =
        Flow.builder()
            .setIngressNode("inNode")
            .setIngressInterface("inInterf")
            .setDstIp(dstIp1)
            .setSrcIp(srcIp1)
            .setDstPort(dstPort1)
            .setSrcPort(srcPort1)
            .setIpProtocol(IpProtocol.TCP)
            .build();
    Flow currentFlow =
        Flow.builder()
            .setIngressNode("inNode")
            .setIngressInterface("inInterf")
            .setDstIp(dstIp2)
            .setSrcIp(srcIp2)
            .setDstPort(dstPort2)
            .setSrcPort(srcPort2)
            .setIpProtocol(IpProtocol.TCP)
            .build();

    Transformation transformation =
        FlowDiffUtils.buildSessionTransformation(FlowDiff.flowDiffs(inputFlow, currentFlow));

    assertThat(transformation.getGuard(), equalTo(AclLineMatchExprs.TRUE));

    assertThat(
        transformation.getTransformationSteps(),
        containsInAnyOrder(
            assignSourceIp(dstIp1),
            assignSourcePort(dstPort1),
            assignDestinationIp(srcIp1),
            assignDestinationPort(srcPort1)));

    assertNull(transformation.getAndThen());
    assertNull(transformation.getOrElse());
  }
}
