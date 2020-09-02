package org.batfish.dataplane.traceroute;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.batfish.datamodel.FlowDiffUtils.applyFlowDiffs;
import static org.batfish.datamodel.FlowDiffUtils.buildSessionTransformation;

import com.google.common.collect.ImmutableList;
import java.util.Objects;
import javax.annotation.Nullable;
import org.batfish.datamodel.Flow;
import org.batfish.datamodel.FlowDisposition;
import org.batfish.datamodel.collections.NodeInterfacePair;
import org.batfish.datamodel.flow.ArpErrorStep;
import org.batfish.datamodel.flow.DeliveredStep;
import org.batfish.datamodel.flow.EnterInputIfaceStep;
import org.batfish.datamodel.flow.EnterInputIfaceStep.EnterInputIfaceStepDetail;
import org.batfish.datamodel.flow.ExitOutputIfaceStep;
import org.batfish.datamodel.flow.ExitOutputIfaceStep.ExitOutputIfaceStepDetail;
import org.batfish.datamodel.flow.FilterStep;
import org.batfish.datamodel.flow.FirewallSessionTraceInfo;
import org.batfish.datamodel.flow.Hop;
import org.batfish.datamodel.flow.InboundStep;
import org.batfish.datamodel.flow.LoopStep;
import org.batfish.datamodel.flow.MatchSessionStep;
import org.batfish.datamodel.flow.OriginateStep;
import org.batfish.datamodel.flow.PolicyStep;
import org.batfish.datamodel.flow.RoutingStep;
import org.batfish.datamodel.flow.SessionAction;
import org.batfish.datamodel.flow.SessionMatchExpr;
import org.batfish.datamodel.flow.SessionScope;
import org.batfish.datamodel.flow.SetupSessionStep;
import org.batfish.datamodel.flow.SetupSessionStep.SetupSessionStepDetail;
import org.batfish.datamodel.flow.Step;
import org.batfish.datamodel.flow.StepAction;
import org.batfish.datamodel.flow.StepVisitor;
import org.batfish.datamodel.flow.Trace;
import org.batfish.datamodel.flow.TransformationStep;
import org.batfish.datamodel.transformation.Transformation;
import org.batfish.symbolic.IngressLocation;

/** Helpers for extracting {@link HopInfo} from {@link Trace traces}. */
public final class HopInfoUtils {
  private HopInfoUtils() {}

  private static FlowDisposition getStepDisposition(Step<?> step) {
    return step.accept(new StepDispositionVisitor());
  }

  private static class StepDispositionVisitor implements StepVisitor<FlowDisposition> {
    @Override
    public FlowDisposition visitArpErrorStep(ArpErrorStep arpErrorStep) {
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

    @Override
    public FlowDisposition visitDeliveredStep(DeliveredStep deliveredStep) {
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

    @Override
    public FlowDisposition visitEnterInputIfaceStep(EnterInputIfaceStep enterInputIfaceStep) {
      return null;
    }

    @Override
    public FlowDisposition visitExitOutputIfaceStep(ExitOutputIfaceStep exitOutputIfaceStep) {
      return null;
    }

    @Override
    public FlowDisposition visitFilterStep(FilterStep filterStep) {
      if (filterStep.getAction() == StepAction.DENIED) {
        switch (filterStep.getDetail().getType()) {
          case INGRESS_FILTER:
          case POST_TRANSFORMATION_INGRESS_FILTER:
            return FlowDisposition.DENIED_IN;
          case EGRESS_FILTER:
          case EGRESS_ORIGINAL_FLOW_FILTER:
          case PRE_TRANSFORMATION_EGRESS_FILTER:
            return FlowDisposition.DENIED_OUT;
        }
      }
      return null;
    }

    @Override
    public FlowDisposition visitInboundStep(InboundStep inboundStep) {
      return FlowDisposition.ACCEPTED;
    }

    @Override
    public FlowDisposition visitLoopStep(LoopStep loopStep) {
      return FlowDisposition.LOOP;
    }

    @Override
    public FlowDisposition visitMatchSessionStep(MatchSessionStep matchSessionStep) {
      return null;
    }

    @Override
    public FlowDisposition visitOriginateStep(OriginateStep originateStep) {
      return null;
    }

    @Override
    public FlowDisposition visitRoutingStep(RoutingStep routingStep) {
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
              String.format("invalid StepAction: %s", routingStep.getAction()));
      }
    }

    @Override
    public FlowDisposition visitPolicyStep(PolicyStep policyStep) {
      return null;
    }

    @Override
    public FlowDisposition visitSetupSessionStep(SetupSessionStep setupSessionStep) {
      return null;
    }

    @Override
    public FlowDisposition visitTransformationStep(TransformationStep transformationStep) {
      return null;
    }
  }

  /** Walks over a hop (and its steps) to pull out the {@link HopInfo}. */
  private static class HopInfoExtractor implements StepVisitor<Void> {
    private final HopInfo.Builder _builder;
    private final Hop _hop;
    // As in traceroute, the Flow's ingressNode/vrf/interface is constant (always the start
    // location).
    private Flow _currentFlow;
    private @Nullable String _currentVrf;
    private @Nullable IngressLocation _ingressLocation;

    HopInfoExtractor(Hop hop, Flow initialFlow) {
      _currentFlow = initialFlow;
      _hop = hop;
      _builder = HopInfo.builder(hop, initialFlow);
    }

    HopInfo buildNode() {
      return _builder.build(_currentFlow);
    }

    private @Nullable String getIngressInterface() {
      checkState(_ingressLocation != null, "ingressLocation must be defined");
      return _ingressLocation.isIngressVrf() ? null : _ingressLocation.getInterface();
    }

    @Override
    public Void visitArpErrorStep(ArpErrorStep arpErrorStep) {
      FlowDisposition stepDisposition = getStepDisposition(arpErrorStep);
      checkArgument(
          stepDisposition != null && !stepDisposition.isSuccessful(),
          "ArpErrorStep must have a failure disposition");
      _builder.setFlowDisposition(stepDisposition);
      return null;
    }

    @Override
    public Void visitDeliveredStep(DeliveredStep deliveredStep) {
      FlowDisposition stepDisposition = getStepDisposition(deliveredStep);
      checkArgument(
          stepDisposition != null && stepDisposition.isSuccessful(),
          "DeliveredStep must have a successful disposition");
      NodeInterfacePair outIface = deliveredStep.getDetail().getOutputInterface();
      _builder.setFlowDisposition(
          stepDisposition,
          TracerouteUtils.returnFlow(
              _currentFlow, outIface.getHostname(), null, outIface.getInterface()));
      return null;
    }

    @Override
    public Void visitEnterInputIfaceStep(EnterInputIfaceStep enterInputIfaceStep) {
      EnterInputIfaceStepDetail detail = enterInputIfaceStep.getDetail();
      NodeInterfacePair inputInterface = detail.getInputInterface();
      _ingressLocation =
          IngressLocation.interfaceLink(
              inputInterface.getHostname(), inputInterface.getInterface());
      _currentVrf = detail.getInputVrf();
      return null;
    }

    @Override
    public Void visitExitOutputIfaceStep(ExitOutputIfaceStep exitOutputIfaceStep) {
      ExitOutputIfaceStepDetail detail = exitOutputIfaceStep.getDetail();
      Flow transformedFlow = detail.getTransformedFlow();
      assert transformedFlow == null || transformedFlow.equals(_currentFlow)
          : "ExitOutputIfaceStep transformed flow does not equal current flow";
      return null;
    }

    @Override
    public Void visitFilterStep(FilterStep filterStep) {
      FlowDisposition stepDisposition = getStepDisposition(filterStep);
      checkArgument(
          stepDisposition == null
              || stepDisposition == FlowDisposition.DENIED_IN
              || stepDisposition == FlowDisposition.DENIED_OUT,
          "invalid disposition flow FilterStep");
      if (stepDisposition != null) {
        _builder.setFlowDisposition(stepDisposition);
      }
      return null;
    }

    @Override
    public Void visitInboundStep(InboundStep inboundStep) {
      // do nothing
      return null;
    }

    @Override
    public Void visitLoopStep(LoopStep loopStep) {
      checkState(_currentVrf != null, "currentVrf must be defined");
      checkState(
          getStepDisposition(loopStep) == FlowDisposition.LOOP,
          "LoopStep must have LOOP disposition");
      _builder.setFlowDisposition(FlowDisposition.LOOP);
      _builder.setLoopDetectedBreadcrumb(
          new Breadcrumb(
              _hop.getNode().getName(), _currentVrf, getIngressInterface(), _currentFlow));
      return null;
    }

    @Override
    public Void visitMatchSessionStep(MatchSessionStep matchSessionStep) {
      // do nothing
      return null;
    }

    @Override
    public Void visitOriginateStep(OriginateStep originateStep) {
      checkState(
          _ingressLocation == null, "ingressLocation already set when OriginateStep encountered");
      checkState(_currentVrf == null, "currentVrf already set when OriginateStep encountered");
      _currentVrf = checkNotNull(originateStep.getDetail().getOriginatingVrf());
      _ingressLocation = IngressLocation.vrf(_hop.getNode().getName(), _currentVrf);
      return null;
    }

    @Override
    public Void visitRoutingStep(RoutingStep routingStep) {
      checkState(_currentVrf != null);
      FlowDisposition disposition = getStepDisposition(routingStep);
      if (disposition != null) {
        checkArgument(
            disposition == FlowDisposition.NO_ROUTE || disposition == FlowDisposition.NULL_ROUTED,
            "invalid disposition for RoutingStep");
        _builder.setFlowDisposition(disposition);
      }
      _builder.setVisitedBreadcrumb(
          new Breadcrumb(
              _hop.getNode().getName(), _currentVrf, getIngressInterface(), _currentFlow));
      return null;
    }

    @Override
    public Void visitPolicyStep(PolicyStep policyStep) {
      // do nothing
      return null;
    }

    @Override
    public Void visitSetupSessionStep(SetupSessionStep setupSessionStep) {
      SetupSessionStepDetail detail = setupSessionStep.getDetail();
      String name = _hop.getNode().getName();
      SessionAction sessionAction = detail.getSessionAction();
      SessionScope sessionScope = detail.getSessionScope();
      SessionMatchExpr matchCriteria = detail.getMatchCriteria();
      Transformation transformation = buildSessionTransformation(detail.getTransformation());
      _builder.setFirewallSessionTraceInfo(
          new FirewallSessionTraceInfo(
              name, sessionAction, sessionScope, matchCriteria, transformation));
      return null;
    }

    @Override
    public Void visitTransformationStep(TransformationStep transformationStep) {
      _currentFlow = applyFlowDiffs(_currentFlow, transformationStep.getDetail().getFlowDiffs());
      return null;
    }
  }

  static HopInfo computeHopInfo(Flow ingressFlow, Hop hop) {
    HopInfoExtractor ctor = new HopInfoExtractor(hop, ingressFlow);
    for (Step<?> step : hop.getSteps()) {
      step.accept(ctor);
    }
    return ctor.buildNode();
  }

  static @Nullable FlowDisposition getHopDisposition(Hop hop) {
    ImmutableList<FlowDisposition> stepDispositions =
        hop.getSteps().stream()
            .map(step -> step.accept(new StepDispositionVisitor()))
            .filter(Objects::nonNull)
            .collect(ImmutableList.toImmutableList());
    checkState(stepDispositions.size() < 2);
    return stepDispositions.isEmpty() ? null : stepDispositions.get(0);
  }
}
