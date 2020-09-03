package org.batfish.dataplane.traceroute;

import static com.google.common.base.Preconditions.checkState;
import static org.batfish.datamodel.FlowDiffUtils.applyFlowDiffs;
import static org.batfish.datamodel.FlowDiffUtils.buildSessionTransformation;
import static org.batfish.dataplane.traceroute.GetStepDisposition.getStepDisposition;
import static org.batfish.dataplane.traceroute.TracerouteUtils.returnFlow;

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
      _builder.setFlowDisposition(getStepDisposition(arpErrorStep));
      return null;
    }

    @Override
    public Void visitDeliveredStep(DeliveredStep deliveredStep) {
      FlowDisposition stepDisposition = getStepDisposition(deliveredStep);
      NodeInterfacePair outIface = deliveredStep.getDetail().getOutputInterface();
      _builder.setFlowDisposition(
          stepDisposition,
          returnFlow(_currentFlow, outIface.getHostname(), null, outIface.getInterface()));
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
      if (stepDisposition != null) {
        _builder.setFlowDisposition(stepDisposition);
      }
      return null;
    }

    @Override
    public Void visitInboundStep(InboundStep inboundStep) {
      assert inboundStep.getAction() == StepAction.ACCEPTED
          : "InboundStep must have action ACCEPTED";
      _builder.setFlowDisposition(
          FlowDisposition.ACCEPTED,
          returnFlow(_currentFlow, _hop.getNode().getName(), _currentVrf, null));
      return null;
    }

    @Override
    public Void visitLoopStep(LoopStep loopStep) {
      assert _currentVrf != null : "currentVrf must be defined before LoopStep";
      assert loopStep.getAction() == StepAction.LOOP : "LoopStep must have LOOP action";
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
      assert _ingressLocation == null
          : "ingressLocation already set when OriginateStep encountered";
      assert _currentVrf == null : "currentVrf already set when OriginateStep encountered";
      _currentVrf = originateStep.getDetail().getOriginatingVrf();
      _ingressLocation = IngressLocation.vrf(_hop.getNode().getName(), _currentVrf);
      return null;
    }

    @Override
    public Void visitRoutingStep(RoutingStep routingStep) {
      assert _currentVrf != null : "currentVrf must be set before RoutingStep";
      FlowDisposition disposition = getStepDisposition(routingStep);
      if (disposition != null) {
        _builder.setFlowDisposition(disposition);
      }
      _builder.setVisitedBreadcrumb(
          new Breadcrumb(
              _hop.getNode().getName(), _currentVrf, getIngressInterface(), _currentFlow));
      return null;
    }

    @Override
    public Void visitPolicyStep(PolicyStep policyStep) {
      FlowDisposition disposition = getStepDisposition(policyStep);
      if (disposition != null) {
        _builder.setFlowDisposition(disposition);
      }
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
}
