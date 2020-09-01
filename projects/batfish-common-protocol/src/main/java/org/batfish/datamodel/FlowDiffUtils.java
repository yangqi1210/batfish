package org.batfish.datamodel;

import static com.google.common.base.Preconditions.checkState;
import static org.batfish.datamodel.transformation.Transformation.always;
import static org.batfish.datamodel.transformation.TransformationStep.assignDestinationIp;
import static org.batfish.datamodel.transformation.TransformationStep.assignDestinationPort;
import static org.batfish.datamodel.transformation.TransformationStep.assignSourceIp;
import static org.batfish.datamodel.transformation.TransformationStep.assignSourcePort;

import com.google.common.annotations.VisibleForTesting;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.batfish.datamodel.transformation.Transformation;
import org.batfish.datamodel.transformation.TransformationStep;

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
        throw new IllegalArgumentException(
            "Unexpected FlowDiff field name " + flowDiff.getFieldName());
    }
  }

  /**
   * Convert a set of {@link FlowDiff flow diffs} (on the forward flow) to a session {@link
   * Transformation} (i.e. for the return flow).
   */
  public static @Nullable Transformation buildSessionTransformation(Set<FlowDiff> flowDiffs) {
    if (flowDiffs.isEmpty()) {
      return null;
    }
    return always()
        .apply(
            flowDiffs.stream()
                .map(FlowDiffUtils::getSessionTransformationStep)
                .collect(Collectors.toList()))
        .build();
  }

  /**
   * Convert a {@link FlowDiff flow diff} (on the forward flow) to a session {@link
   * TransformationStep} (i.e. for the return flow).
   */
  static TransformationStep getSessionTransformationStep(FlowDiff flowDiff) {
    switch (flowDiff.getFieldName()) {
      case Flow.PROP_DST_IP:
        return assignSourceIp(Ip.parse(flowDiff.getOldValue()));
      case Flow.PROP_SRC_IP:
        return assignDestinationIp(Ip.parse(flowDiff.getOldValue()));
      case Flow.PROP_DST_PORT:
        return assignSourcePort(Integer.parseInt(flowDiff.getOldValue()));
      case Flow.PROP_SRC_PORT:
        return assignDestinationPort(Integer.parseInt(flowDiff.getOldValue()));
      default:
        throw new IllegalArgumentException(
            "Unexpected FlowDiff field name " + flowDiff.getFieldName());
    }
  }
}
