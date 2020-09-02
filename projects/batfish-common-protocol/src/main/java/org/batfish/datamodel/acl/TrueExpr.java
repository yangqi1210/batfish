package org.batfish.datamodel.acl;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.batfish.datamodel.TraceElement;

public class TrueExpr extends AclLineMatchExpr {
  public static final TrueExpr INSTANCE = new TrueExpr();

  private TrueExpr() {
    super(null);
  }

  public TrueExpr(TraceElement traceElement) {
    super(traceElement);
  }

  @Override
  public <R> R accept(GenericAclLineMatchExprVisitor<R> visitor) {
    return visitor.visitTrueExpr(this);
  }

  @Override
  protected boolean exprEquals(Object o) {
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode((Boolean) true);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass()).toString();
  }
}
