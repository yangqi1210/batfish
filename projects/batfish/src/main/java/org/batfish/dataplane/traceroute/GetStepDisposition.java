package org.batfish.dataplane.traceroute;

import org.batfish.datamodel.FlowDisposition;
import org.batfish.datamodel.flow.ArpErrorStep;
import org.batfish.datamodel.flow.DeliveredStep;
import org.batfish.datamodel.flow.FilterStep;
import org.batfish.datamodel.flow.PolicyStep;
import org.batfish.datamodel.flow.RoutingStep;
import org.batfish.datamodel.flow.Step;
import org.batfish.datamodel.flow.StepAction;

/**
 * Extract the {@link org.batfish.datamodel.FlowDisposition} (if any) from a {@link Step}. Most of
 * the time, the disposition is uniquely identified by the {@link StepAction}. For some actions, we
 * need to consider other data, e.g. {@link StepAction#DENIED} corresponds to either {@link
 * FlowDisposition#DENIED_IN} or {@link FlowDisposition#DENIED_OUT}.
 */
public final class GetStepDisposition {
  private GetStepDisposition() {}

  static FlowDisposition getStepDisposition(ArpErrorStep arpErrorStep) {
    switch (arpErrorStep.getAction()) {
      case INSUFFICIENT_INFO:
        return FlowDisposition.INSUFFICIENT_INFO;
      case NEIGHBOR_UNREACHABLE:
        return FlowDisposition.NEIGHBOR_UNREACHABLE;
      default:
        throw new IllegalStateException(
            String.format("invalid StepAction: %s", arpErrorStep.getAction()));
    }
  }

  static FlowDisposition getStepDisposition(DeliveredStep deliveredStep) {
    switch (deliveredStep.getAction()) {
      case DELIVERED_TO_SUBNET:
        return FlowDisposition.DELIVERED_TO_SUBNET;
      case EXITS_NETWORK:
        return FlowDisposition.EXITS_NETWORK;
      default:
        throw new IllegalStateException(
            String.format("invalid StepAction: %s", deliveredStep.getAction()));
    }
  }

  static FlowDisposition getStepDisposition(FilterStep filterStep) {
    switch (filterStep.getAction()) {
      case DENIED:
        // fall through
        break;
      case PERMITTED:
        // no disposition
        return null;
      default:
        throw new IllegalStateException(
            "Unexpected StepAction for FilterStep: " + filterStep.getAction());
    }

    // StepAction is DENIED, so disposition is DENIED_IN or DENIED_OUT.
    FilterStep.FilterType type = filterStep.getDetail().getType();
    switch (type) {
      case INGRESS_FILTER:
      case POST_TRANSFORMATION_INGRESS_FILTER:
        return FlowDisposition.DENIED_IN;
      case EGRESS_FILTER:
      case EGRESS_ORIGINAL_FLOW_FILTER:
      case PRE_TRANSFORMATION_EGRESS_FILTER:
        return FlowDisposition.DENIED_OUT;
      default:
        throw new IllegalStateException("Unexpected FilterType: " + type);
    }
  }

  static FlowDisposition getStepDisposition(RoutingStep routingStep) {
    switch (routingStep.getAction()) {
      case NO_ROUTE:
        return FlowDisposition.NO_ROUTE;
      case NULL_ROUTED:
        return FlowDisposition.NULL_ROUTED;
      case FORWARDED:
      case FORWARDED_TO_NEXT_VRF:
        return null;
      default:
        throw new IllegalStateException(
            "Unexpected StepAction for RoutingStep: " + routingStep.getAction());
    }
  }

  static FlowDisposition getStepDisposition(PolicyStep policyStep) {
    switch (policyStep.getAction()) {
      case DENIED:
        return FlowDisposition.DENIED_IN;
      case PERMITTED:
        return null;
      default:
        throw new IllegalStateException(
            "Unexpected StepAction for PolicyStep: " + policyStep.getAction());
    }
  }
}
