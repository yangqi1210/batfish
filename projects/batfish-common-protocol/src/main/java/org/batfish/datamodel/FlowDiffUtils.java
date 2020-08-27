package org.batfish.datamodel;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;

public final class FlowDiffUtils {
  private FlowDiffUtils() {}

  public static Flow applyFlowDiffs(Flow flow, Iterable<FlowDiff> flowDiffs) {
    Flow.Builder flowBuilder = flow.toBuilder();
    for (FlowDiff flowDiff : flowDiffs) {
      applyFlowDiff(flowBuilder, flowDiff);
    }
    return flowBuilder.build();
  }

  @VisibleForTesting
  static void applyFlowDiff(Flow.Builder flowBuilder, FlowDiff flowDiff) {
    switch (flowDiff.getFieldName()) {
    case Flow.PROP_DST_IP:
      checkState(flowBuilder.getDstIp().equals(Ip.parse(flowDiff.getOldValue())));
      flowBuilder.setDstIp(Ip.parse(flowDiff.getNewValue()));
      break;
    case Flow.PROP_SRC_IP:
        checkState(flowBuilder.getSrcIp().equals(Ip.parse(flowDiff.getOldValue())));
        flowBuilder.setSrcIp(Ip.parse(flowDiff.getNewValue()));
        break;
    case Flow.PROP_DST_PORT:
      checkState(flowBuilder.getDstPort().equals(Integer.parseInt(flowDiff.getOldValue())));
      flowBuilder.setDstPort(Integer.parseInt(flowDiff.getNewValue()));
      break;
    case Flow.PROP_SRC_PORT:
      checkState(flowBuilder.getSrcPort().equals(Integer.parseInt(flowDiff.getOldValue())));
      flowBuilder.setSrcPort(Integer.parseInt(flowDiff.getNewValue()));
      break;
    default:
      throw new IllegalArgumentException("Unexpected FlowDiff field name " + flowDiff.getFieldName());
    }
  }
}
