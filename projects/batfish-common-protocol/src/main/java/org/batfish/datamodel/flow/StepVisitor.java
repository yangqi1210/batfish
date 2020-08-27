package org.batfish.datamodel.flow;

/** Visitor interface for {@link Step}. */
public interface StepVisitor<T> {
  T visitArpErrorStep(ArpErrorStep arpErrorStep);

  T visitDeliveredStep(DeliveredStep deliveredStep);

  T visitEnterInputIfaceStep(EnterInputIfaceStep enterInputIfaceStep);

  T visitExitOutputIfaceStep(ExitOutputIfaceStep exitOutputIfaceStep);

  T visitFilterStep(FilterStep filterStep);

  T visitInboundStep(InboundStep inboundStep);

  T visitLoopStep(LoopStep loopStep);

  T visitMatchSessionStep(MatchSessionStep matchSessionStep);

  T visitOriginateStep(OriginateStep originateStep);

  T visitRoutingStep(RoutingStep routingStep);

  T visitPolicyStep(PolicyStep policyStep);

  T visitSetupSessionStep(SetupSessionStep setupSessionStep);

  T visitTransformationStep(TransformationStep transformationStep);
}
